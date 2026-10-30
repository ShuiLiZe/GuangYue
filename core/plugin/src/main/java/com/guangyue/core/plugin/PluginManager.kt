package com.guangyue.core.plugin

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import android.net.Uri
import android.provider.OpenableColumns
import com.guangyue.core.plugin.runtime.ErrorDetail
import com.guangyue.core.plugin.runtime.PluginRuntime
import com.guangyue.core.plugin.runtime.PluginRuntimeException
import com.guangyue.core.plugin.runtime.PreludeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 插件生命周期：从本地文件 / 直链 / 订阅源安装，启停、删除、更新。
 *
 * 安装从不信任字节内容：代码先在一个一次性沙箱 runtime 里解析并启动，只有导出了合法
 * MusicFree 模块才会写入磁盘。
 *
 * 订阅源（MusicFree 插件市场）形如：
 * ```
 * { "name": "…", "plugins": [ { "name": "网易云", "url": "https://…/netease.js", "version": "0.1.6" } ] }
 * ```
 * [installFromUrl] 自动识别「单个 .js」与「订阅 JSON」两种链接；订阅 JSON 会分批并发下载
 * 各插件并逐个 [installCode]，最后返回 [BatchInstallResult] 统计。
 */
@Singleton
class PluginManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PluginStore,
    @com.guangyue.core.plugin.di.PlaybackHttp private val http: OkHttpClient,
    private val prelude: PreludeProvider,
) {
    private val _plugins = MutableStateFlow<List<PluginMeta>>(emptyList())
    val plugins: StateFlow<List<PluginMeta>> = _plugins.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** 批次进度，[busy] 为 true 时才有意义。 */
    private val _progress = MutableStateFlow(BatchProgress())
    val progress: StateFlow<BatchProgress> = _progress.asStateFlow()

    /** 已记录的订阅源。 */
    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions.asStateFlow()

    /** 最近一次解析到的订阅源插件清单（UI 用来渲染「可安装插件」与全选）。 */
    private val _catalog = MutableStateFlow<List<CatalogEntry>>(emptyList())
    val catalog: StateFlow<List<CatalogEntry>> = _catalog.asStateFlow()

    @Volatile private var loaded = false

    init {
        // 让出厂体检基线（assets/plugin-health.json）能在没有 Context 的代码里被读到
        PluginAssetContext.install(context)
    }

    /** 最近一次失败插件的原始报错（含元信息 JSON），供「展开详情」显示。 */
    @Volatile
    var lastErrorDetails: String? = null
        private set

    suspend fun load() = withContext(Dispatchers.IO) {
        if (loaded) return@withContext
        _plugins.value = store.readIndex().ordered()
        _subscriptions.value = store.readSubscriptions().sortedByDescending { it.checkedAt }
        loaded = true
        if (_catalog.value.isEmpty()) {
            _catalog.value = _subscriptions.value.flatMap { sub ->
                store.readCachedSource(sub.url)?.plugins?.map { CatalogEntry(it, sub.url) } ?: emptyList()
            }
        }
        // 0.0.7 允许每个插件各自一个开关，升上来的旧数据可能同时启用十几个；这里收成一个。
        coerceSingleSource()
    }

    fun enabled(): List<PluginMeta> = _plugins.value.filter { it.enabled && it.error == null }

    fun find(id: String): PluginMeta? = _plugins.value.firstOrNull { it.id == id }

    fun findByPlatform(platform: String): PluginMeta? =
        _plugins.value.firstOrNull { it.platform == platform }

    /** 本地是否已装过这个 srcUrl（订阅增量更新时用）。 */
    fun findInstalledBySrc(entryUrl: String): PluginMeta? =
        _plugins.value.firstOrNull { it.srcUrl == entryUrl || it.source == entryUrl }

    // ------------------------------------------------------------------ install

    /** 从机器本地文件安装（SAF uri 或裸路径）。 */
    suspend fun installFromUri(uri: Uri): InstallResult = withContext(Dispatchers.IO) {
        _busy.value = true
        try {
            val code = readText(uri) ?: return@withContext InstallResult.Failure("无法读取文件")
            installCode(code, uri.toString(), displayNameOf(uri))
        } finally {
            _busy.value = false
        }
    }

    /**
     * 从远程链接安装。自动区分目标类型：
     *
     * 1. **订阅 JSON**（含 `plugins` 数组）→ 批量安装，返回 [InstallResult.BatchInstalled]
     * 2. **单个插件描述 JSON**（含 `url` / `srcUrl`）→ 跟进到真正的 .js 再安装
     * 3. **.js 源码**（其余情况）→ 单条安装
     */
    suspend fun installFromUrl(url: String): InstallResult = withContext(Dispatchers.IO) {
        _busy.value = true
        _progress.value = BatchProgress(stage = "拉取订阅源")
        try {
            val target = url.trim()
            if (target.isEmpty()) return@withContext InstallResult.Failure("链接为空")

            val text = download(target)
                ?: return@withContext InstallResult.Failure("下载失败：$target")

            val parsed = parseSubscription(text, target)
            if (parsed != null) {
                if (parsed.plugins.isEmpty()) {
                    return@withContext InstallResult.Failure("订阅源里没有插件：$target")
                }
                recordSubscription(parsed)
                InstallResult.BatchInstalled(installSubscription(parsed))
            } else {
                val single = singleEntryUrl(text) ?: target
                val code = if (single == target) text
                else (download(single) ?: return@withContext InstallResult.Failure("下载失败：$single"))
                installCode(code, single, single.substringAfterLast('/').removeSuffix(".js"))
            }
        } finally {
            _progress.value = BatchProgress()
            _busy.value = false
        }
    }

    /** 只安装订阅源里选中的那几条（UI 全选/全不选提交入口）。 */
    suspend fun installFromSubscription(
        sourceUrl: String,
        selected: List<SubscriptionEntry>,
    ): InstallResult = withContext(Dispatchers.IO) {
        if (selected.isEmpty()) return@withContext InstallResult.Failure("没有选中任何插件")
        _busy.value = true
        _progress.value = BatchProgress(stage = "安装所选插件", total = selected.size)
        try {
            val batch = installEntries(
                entries = selected,
                known = subscriptionsOf(sourceUrl),
                countAsUpdate = { entry ->
                    val local = findInstalledBySrc(entry.url)
                    local != null && compareVersion(entry.version, local.version) > 0
                },
            )
            InstallResult.BatchInstalled(batch)
        } finally {
            _progress.value = BatchProgress()
            _busy.value = false
        }
    }

    /**
     * 校验 + 落盘插件源码。
     *
     * 沙箱探测会拒掉非 MusicFree 文件（HTML 错误页、没有 `module.exports` 的压缩包等），
     * 之后才碰磁盘。
     */
    suspend fun installCode(
        code: String,
        source: String,
        fallbackName: String? = null,
    ): InstallResult = withContext(Dispatchers.IO) {
        if (code.isBlank()) {
            val msg = "插件内容为空"
            recordDetail(source, msg)
            return@withContext InstallResult.Failure(msg)
        }

        val runtime = PluginRuntime(
            meta = PluginMeta(id = "", platform = "probe", name = "probe", version = "0", source = source),
            code = code,
            http = http,
            preludeProvider = prelude,
        )
        try {
            runtime.install()
            // 元信息一律走「JS 侧 JSON.stringify → Kotlin 宽松解析」，不经过任何反射类型转换。
            val metaJson = runtime.readMetaJson()
            val decoded = PluginMetaCodec.decode(
                json = metaJson,
                source = source,
                sizeBytes = code.toByteArray().size.toLong(),
                fallbackPlatform = fallbackName,
            )
            val probed = decoded.meta ?: return@withContext InstallResult.Failure(
                shortReason(decoded.error, metaJson).also {
                    recordDetail(source, buildString {
                        append(decoded.error ?: "插件元信息不完整")
                        append("\nmeta = ").append(metaJson.take(1500))
                    })
                },
            )
            val id = store.idFor(probed.platform, source)

            // 同 platform 同 source → 更新；同 platform 不同 source → 拒绝（平台 id 会撞车）
            val existing = _plugins.value.firstOrNull { it.platform == probed.platform }
            if (existing != null && existing.id != id) {
                return@withContext InstallResult.Failure(
                    "已安装同平台插件「${existing.displayName}」，请先删除后再安装",
                )
            }

            val finalMeta = probed.copy(
                id = id,
                srcUrl = probed.srcUrl ?: source.takeIf { it.startsWith("http") },
                // 0.0.7：导入不再自动启用。批量导入 57 个插件时全部启用会让搜索页把每个音源
                // 都打一遍（还会被源站限流），用户根本不知道该用哪个。现在导入只代表「已安装」，
                // 出现在搜索页的可选列表里需要用户自己打开，或用「只启用体检可用的」一键打开。
                enabled = existing?.enabled ?: false,
                sortOrder = existing?.sortOrder ?: (_plugins.value.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                health = existing?.health
                    // 没体检过就用打包的出厂体检基线（真机实测结果），否则保持「未体检」
                    ?: PluginHealthBaseline.lookup(probed.platform)?.first
                    ?: PluginHealth.UNKNOWN,
                healthNote = existing?.healthNote ?: PluginHealthBaseline.lookup(probed.platform)?.second,
                healthAt = existing?.healthAt ?: 0L,
                installedAt = existing?.installedAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            store.writeCode(id, code)
            store.writeMeta(finalMeta)
            upsert(finalMeta)
            InstallResult.Success(finalMeta)
        } catch (e: PluginRuntimeException) {
            recordDetail(source, failureDetail(e, e.message))
            InstallResult.Failure(shortReason(e.message, null))
        } catch (t: Throwable) {
            recordDetail(source, failureDetail(t, t.message))
            InstallResult.Failure(shortReason(t.message, null))
        } finally {
            runtime.close()
        }
    }

    /**
     * 单插件失败时给用户一句短中文（列表里直接显示），原始报错留在 [PluginManager.lastErrorDetails]
     * 里供「展开详情」查看。
     */
    private fun shortReason(raw: String?, metaJson: String?): String {
        val message = raw?.trim().orEmpty()
        lastErrorDetails = buildString {
            if (message.isNotEmpty()) append(message)
            if (!metaJson.isNullOrBlank()) {
                if (isNotEmpty()) append('\n')
                append("meta = ").append(metaJson.take(800))
            }
        }.ifEmpty { "插件加载失败" }
        return when {
            message.isEmpty() -> "插件加载失败"
            message.contains("No such type converter", ignoreCase = true) ->
                "插件返回了无法识别的数据类型（内部桥接已改为纯 JSON 解析）"
            message.contains("platform", ignoreCase = true) -> "插件没有声明 platform 字段"
            message.contains("module.exports", ignoreCase = true) -> "插件没有导出 module.exports 对象"
            message.contains("超时", ignoreCase = true) -> message
            message.contains("无法加载模块", ignoreCase = true) -> "插件依赖的模块无法加载"
            else -> message.lineSequence().first().take(120)
        }
    }

    // ---------------------------------------------------------------- failure forensics (0.0.2)

    /**
     * 失败详情：**原始异常类名 + message + 前 15 行堆栈**。
     *
     * 0.0.1 只给用户一句友好文案，用户反馈问题时什么都拿不到。现在：
     *  - [detailFor] 按插件（URL / 文件名）返回完整现场，UI「展开详情」直接原样显示；
     *  - [lastErrorDetails] 保留兼容，兼容旧调用方与日志面板。
     *
     * 详情只存最近 [DETAIL_LIMIT] 条，键是安装源，不会把批量导入 57 个插件的内存撑爆。
     */
    private val _errorDetails = java.util.Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(32, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?) =
                size > DETAIL_LIMIT
        },
    )

    /** 取某个安装源（URL / 文件名 / 插件显示名）的失败详情；查不到返回 null。 */
    fun detailFor(source: String?): String? {
        val key = source?.trim().orEmpty()
        if (key.isEmpty()) return lastErrorDetails
        _errorDetails[key]?.let { return it }
        // 同一插件可能因「重复平台」等原因以别名失败，退回最近一条。
        return lastErrorDetails
    }

    /** 记录一条失败现场。 */
    private fun recordDetail(source: String?, detail: String) {
        lastErrorDetails = detail
        val key = source?.trim().orEmpty().ifEmpty { "<unknown>" }
        _errorDetails[key] = detail
    }

    /**
     * 组装失败现场文本：优先用原始异常（[PluginRuntimeException.detailText]），
     * 拿不到原始异常时退回当前异常的 [ErrorDetail]。
     */
    private fun failureDetail(t: Throwable?, fallbackMessage: String?): String {
        val detail = when (t) {
            is PluginRuntimeException -> t.detailText
            null -> return fallbackMessage?.trim().orEmpty().ifEmpty { "插件加载失败" }
            else -> ErrorDetail.of(t).format()
        }
        return detail.ifBlank { fallbackMessage?.trim().orEmpty().ifEmpty { "插件加载失败" } }
    }

    // ------------------------------------------------------------------ subscription

    /** 记录 / 刷新一条订阅源。 */
    private fun recordSubscription(source: SubscriptionSource) {
        val now = System.currentTimeMillis()
        val existing = _subscriptions.value.firstOrNull { it.url == source.url }
        val row = Subscription(
            url = source.url,
            name = source.name.ifBlank { hostOf(source.url) },
            addedAt = existing?.addedAt ?: now,
            checkedAt = now,
            count = source.plugins.size,
        )
        val next = _subscriptions.value.filterNot { it.url == source.url } + row
        _subscriptions.value = next
        store.writeSubscriptions(next)
        store.writeCachedSource(source.url, source)
        _catalog.value = next.flatMap { sub ->
            if (sub.url == source.url) {
                source.plugins.map { CatalogEntry(it, sub.url) }
            } else {
                store.readCachedSource(sub.url)?.plugins?.map { CatalogEntry(it, sub.url) } ?: emptyList()
            }
        }
    }

    private fun subscriptionsOf(url: String): Subscription? =
        _subscriptions.value.firstOrNull { it.url == url }

    suspend fun forgetSubscription(url: String) = withContext(Dispatchers.IO) {
        val next = _subscriptions.value.filterNot { it.url == url }
        _subscriptions.value = next
        store.writeSubscriptions(next)
        _catalog.value = _catalog.value.filterNot { it.sourceUrl == url }
    }

    /**
     * 拉取订阅源最新内容（不安装），用于「一键安装/更新官方音源」按钮的清单展示。
     * 拉取失败时回落到缓存，都失败返回 null。
     */
    suspend fun refreshCatalog(sourceUrl: String): List<CatalogEntry>? = withContext(Dispatchers.IO) {
        val text = download(sourceUrl)
        val parsed = text?.let { parseSubscription(it, sourceUrl) }
        if (parsed != null) {
            recordSubscription(parsed)
            parsed.plugins.map { CatalogEntry(it, sourceUrl) }
        } else {
            store.readCachedSource(sourceUrl)?.plugins?.map { CatalogEntry(it, sourceUrl) }
        }
    }

    /** 一次性把订阅源里「本地没有 + 有新版」的插件全部装/更新。 */
    suspend fun installOrUpdateAll(sourceUrl: String, force: Boolean = false): InstallResult =
        withContext(Dispatchers.IO) {
            _busy.value = true
            _progress.value = BatchProgress(stage = "拉取订阅源")
            try {
                val text = download(sourceUrl)
                    ?: return@withContext InstallResult.Failure("订阅源无法访问：$sourceUrl")
                val parsed = parseSubscription(text, sourceUrl)
                    ?: return@withContext InstallResult.Failure("这不是一个订阅源：$sourceUrl")
                if (parsed.plugins.isEmpty()) {
                    return@withContext InstallResult.Failure("订阅源里没有插件")
                }
                recordSubscription(parsed)
                InstallResult.BatchInstalled(installSubscription(parsed, force = force))
            } finally {
                _progress.value = BatchProgress()
                _busy.value = false
            }
        }

    /**
     * 增量更新：对比远程订阅列表与本地插件版本号，只重装差异项。
     *
     * 规则：
     *  - 远程有、本地无 → 进 [BatchUpdateResult.discovered]，是否安装由调用方决定
     *  - 远程版本 > 本地版本 → 重装（保留 enabled）
     *  - 其余跳过
     *  - 没有订阅源的孤立插件（仅有 srcUrl）→ 退化为单条 [checkUpdate] + [update]
     */
    suspend fun checkUpdates(installDiscovered: Boolean = false): BatchUpdateResult =
        withContext(Dispatchers.IO) {
            _busy.value = true
            _progress.value = BatchProgress(stage = "检查更新")
            try {
                load()
                val updated = mutableListOf<PluginMeta>()
                val failed = mutableListOf<Pair<String, String>>()
                val discovered = mutableListOf<SubscriptionEntry>()

                val subs = _subscriptions.value.ifEmpty {
                    listOf(Subscription(url = DEFAULT_SOURCE_URL, name = "官方音源"))
                }

                var checked = 0
                for (sub in subs) {
                    val text = download(sub.url)
                    val parsed = text?.let { parseSubscription(it, sub.url) }
                    if (parsed == null) {
                        failed += (sub.name.ifBlank { sub.url }) to "订阅源无法访问"
                        continue
                    }
                    recordSubscription(parsed)
                    for (entry in parsed.plugins) {
                        checked++
                        val local = findInstalledBySrc(entry.url)
                        if (local == null) {
                            discovered += entry
                            continue
                        }
                        if (compareVersion(entry.version, local.version) <= 0) continue
                        val code = download(entry.url)
                        if (code == null) {
                            failed += entry.displayName to "下载失败"
                            continue
                        }
                        when (val r = installCode(code, entry.url, entry.displayName)) {
                            is InstallResult.Success -> updated += r.meta
                            is InstallResult.Failure -> failed += entry.displayName to r.message
                            is InstallResult.BatchInstalled -> Unit
                        }
                    }
                }

                if (installDiscovered && discovered.isNotEmpty()) {
                    val batch = installEntries(discovered, null) { false }
                    updated += batch.installed + batch.updated
                    batch.failed.forEach { failed += it }
                    discovered.clear()
                }

                // 没有订阅记录、但带 srcUrl 的插件走单条检查
                val covered = _catalog.value.map { it.entry.url }.toSet()
                for (meta in _plugins.value) {
                    val src = meta.srcUrl ?: continue
                    if (src in covered) continue
                    checked++
                    when (val c = checkUpdate(meta.id)) {
                        is UpdateCheck.Available ->
                            (update(meta.id) as? InstallResult.Success)?.let { updated += it.meta }
                        is UpdateCheck.Error -> failed += meta.displayName to c.message
                        else -> Unit
                    }
                }

                BatchUpdateResult(checked, updated, failed, discovered)
            } finally {
                _progress.value = BatchProgress()
                _busy.value = false
            }
        }

    /** 批量下载 + 安装：每批 [BATCH_SIZE] 个，批内并发、批间串行，避免把源站打爆。 */
    private suspend fun installSubscription(
        source: SubscriptionSource,
        force: Boolean = false,
    ): BatchInstallResult = installEntries(source.plugins, subscriptionsOf(source.url)) { entry ->
        val local = findInstalledBySrc(entry.url)
        force || (local != null && compareVersion(entry.version, local.version) > 0)
    }

    private suspend fun installEntries(
        entries: List<SubscriptionEntry>,
        known: Subscription?,
        countAsUpdate: (SubscriptionEntry) -> Boolean,
    ): BatchInstallResult {
        val installed = mutableListOf<PluginMeta>()
        val updated = mutableListOf<PluginMeta>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        var done = 0

        _progress.value = BatchProgress(stage = "下载插件", done = 0, total = entries.size)

        entries.chunked(BATCH_SIZE).forEachIndexed { batchIndex, batch ->
            coroutineScope {
                batch.forEach { entry ->
                    launch(Dispatchers.IO) {
                        try {
                            gate.withPermit {
                                val local = findInstalledBySrc(entry.url)
                                when {
                                    local == null -> {
                                        val code = download(entry.url)
                                        if (code == null) {
                                            synchronized(failed) { failed += entry.displayName to "下载失败" }
                                        } else {
                                            when (val r = installCode(code, entry.url, entry.displayName)) {
                                                is InstallResult.Success ->
                                                    synchronized(installed) { installed += r.meta }
                                                is InstallResult.Failure ->
                                                    synchronized(failed) { failed += entry.displayName to r.message }
                                                is InstallResult.BatchInstalled -> Unit
                                            }
                                        }
                                    }

                                    countAsUpdate(entry) -> {
                                        val code = download(entry.url)
                                        if (code == null) {
                                            synchronized(failed) { failed += entry.displayName to "下载失败" }
                                        } else {
                                            val wasEnabled = local.enabled
                                            when (val r = installCode(code, entry.url, entry.displayName)) {
                                                is InstallResult.Success -> {
                                                    if (!wasEnabled) setEnabled(r.meta.id, false)
                                                    synchronized(updated) { updated += r.meta }
                                                }
                                                is InstallResult.Failure ->
                                                    synchronized(failed) { failed += entry.displayName to r.message }
                                                is InstallResult.BatchInstalled -> Unit
                                            }
                                        }
                                    }

                                    else -> synchronized(skipped) { skipped += entry.displayName }
                                }
                            }
                        } finally {
                            done += 1
                            _progress.value = _progress.value.copy(done = done)
                        }
                    }
                }
            }
            _progress.value = _progress.value.copy(stage = "第 ${batchIndex + 1} 批完成")
        }

        return BatchInstallResult(
            total = entries.size,
            installed = installed.sortedBy { it.name },
            updated = updated.sortedBy { it.name },
            skipped = skipped,
            failed = failed,
            subscriptions = known?.let { listOf(it) } ?: emptyList(),
        )
    }

    // ------------------------------------------------------------------ manage

    suspend fun setEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val meta = find(id) ?: return@withContext
        upsert(meta.copy(enabled = enabled, updatedAt = System.currentTimeMillis()))
        if (enabled) coerceCurrent(id)
    }

    /**
     * 0.0.8：**唯一当前音源**。
     *
     * 用户在插件页点一个单选、或在搜索页顶部选一个，走的都是这里：把一个插件设为当前音源，
     * 其余插件全部让位（`enabled = false`）。App 里任何时刻最多只有**一个**插件处于运行状态，
     * 不会一次搜索同时打十几个源站，也不会出现「到底是哪个音源在跑」的困惑。
     *
     * @return 被选中的插件；[id] 不存在时返回 null。
     */
    suspend fun setCurrentSource(id: String): PluginMeta? = withContext(Dispatchers.IO) {
        val target = find(id) ?: return@withContext null
        coerceCurrent(id)
        find(target.id)
    }

    /** 当前选中的那个音源（没选过、或选中的已被删掉时为 null）。 */
    fun currentSource(): PluginMeta? =
        _plugins.value.firstOrNull { it.enabled && it.error == null }

    /** 清空当前音源：所有插件都变成「已安装、可选、未选中」。 */
    suspend fun clearCurrentSource() = withContext(Dispatchers.IO) {
        if (_plugins.value.none { it.enabled }) return@withContext
        val now = System.currentTimeMillis()
        val next = _plugins.value.map { it.copy(enabled = false, updatedAt = now) }
        next.forEach { store.writeMeta(it) }
        _plugins.value = next.ordered()
        store.writeIndex(_plugins.value)
        notifyReload(null)
    }

    /**
     * 保证同一时刻最多一个插件启用。`load()` 之后调一次，修掉 0.0.7 留下的
     * 「十几个插件同时启用」的旧数据（老用户升级上来就会遇到）。
     */
    suspend fun coerceSingleSource() = withContext(Dispatchers.IO) {
        val enabledList = _plugins.value.filter { it.enabled && it.error == null }
        if (enabledList.size <= 1) return@withContext
        coerceCurrent(enabledList.first().id)
        notifyReload(null)
    }

    /** 把 [id] 设为唯一启用项（内部用；不触发 runtime 重载）。 */
    private fun coerceCurrent(id: String) {
        val now = System.currentTimeMillis()
        val next = _plugins.value.map { meta ->
            val wanted = meta.id == id
            if (meta.enabled == wanted) meta else meta.copy(enabled = wanted, updatedAt = now)
        }
        next.forEach { store.writeMeta(it) }
        _plugins.value = next.ordered()
        store.writeIndex(_plugins.value)
    }

    /** 全选 / 全不选：一次性改所有插件的启用状态。 */
    suspend fun setAllEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val next = _plugins.value.map {
            if (it.enabled == enabled) it else it.copy(enabled = enabled, updatedAt = now)
        }
        next.forEach { store.writeMeta(it) }
        _plugins.value = next.ordered()
        store.writeIndex(_plugins.value)
        notifyReload(null)
    }

    /**
     * 0.0.7「只启用体检可用的」：把 [PluginHealth.OK] 的插件打开、其余关掉。
     *
     * 导入后 57 个插件全部处于「已安装未启用」，用户点这一下就能得到一份真能搜能播的列表，
     * 而不必逐个试错。返回 (启用数, 总插件数)。
     */
    suspend fun enableHealthyOnly(): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val next = _plugins.value.map { meta ->
            val wanted = meta.isHealthy
            if (meta.enabled == wanted) meta else meta.copy(enabled = wanted, updatedAt = now)
        }
        next.forEach { store.writeMeta(it) }
        _plugins.value = next.ordered()
        store.writeIndex(_plugins.value)
        notifyReload(null)
        _plugins.value.count { it.enabled } to _plugins.value.size
    }

    /** 体检结论落盘（复检结束时批量写一次，避免逐个写 index）。 */
    suspend fun recordHealth(
        results: Map<String, Pair<String, String?>>,
        markEnabled: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (results.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val next = _plugins.value.map { meta ->
            val hit = results[meta.id] ?: return@map meta
            meta.copy(
                health = hit.first,
                healthNote = hit.second,
                healthAt = now,
                enabled = if (markEnabled) hit.first == PluginHealth.OK else meta.enabled,
            )
        }
        next.forEach { store.writeMeta(it) }
        _plugins.value = next.ordered()
        store.writeIndex(_plugins.value)
        notifyReload(null)
    }

    /**
     * 拖动排序：把 [id] 挪到 [to] 位置，重排后写回 [PluginMeta.sortOrder]。
     *
     * 管理页和搜索页的音源顺序都按它显示，用户把常用的音源拖到最上面即可。
     */
    suspend fun moveTo(id: String, to: Int) = withContext(Dispatchers.IO) {
        val list = _plugins.value.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        if (from < 0 || list.size < 2) return@withContext
        val target = to.coerceIn(0, list.lastIndex)
        if (from == target) return@withContext
        val item = list.removeAt(from)
        list.add(target, item)
        val next = list.mapIndexed { index, meta ->
            if (meta.sortOrder == index) meta else meta.copy(sortOrder = index)
        }
        next.forEach { store.writeMeta(it) }
        _plugins.value = next
        store.writeIndex(_plugins.value)
    }

    /**
     * 搜索页需要的那份「可选音源」列表：只含已启用、无安装错误、真的声明了 search 的插件。
     *
     * 0.0.7 的硬要求是绝不一次并发打所有插件，所以这份列表只作为**选择器的候选**，
     * 真正发请求的永远只有用户选中的那一个。
     */
    fun selectable(): List<PluginMeta> =
        _plugins.value
            .filter { it.enabled && it.error == null && it.hasSearch && it.supportsMusic }
            .ordered()

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        store.delete(id)
        _plugins.value = _plugins.value.filterNot { it.id == id }
        store.writeIndex(_plugins.value)
        notifyReload(null)
    }

    /** 清空所有插件（UI 长按 / 设置页使用）。 */
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        _plugins.value.forEach { store.delete(it.id) }
        _plugins.value = emptyList()
        store.writeIndex(emptyList())
        notifyReload(null)
    }

    /**
     * 对比已安装版本与插件 `srcUrl` 旁的版本信息
     * （MusicFree 在 .js 同目录放 `<name>.json`：`{ version, desc }`）。
     */
    suspend fun checkUpdate(id: String): UpdateCheck = withContext(Dispatchers.IO) {
        val meta = find(id) ?: return@withContext UpdateCheck.Unknown
        val url = meta.srcUrl ?: return@withContext UpdateCheck.NoSource
        val remote = runCatching { fetchVersion(url) }.getOrNull()
            ?: return@withContext UpdateCheck.Error("无法获取远程版本")
        if (compareVersion(remote, meta.version) > 0) {
            UpdateCheck.Available(current = meta.version, latest = remote)
        } else {
            UpdateCheck.Latest(current = meta.version)
        }
    }

    /** 重新下载 `srcUrl` 并重装，保留 enabled 状态。 */
    suspend fun update(id: String): InstallResult = withContext(Dispatchers.IO) {
        val meta = find(id) ?: return@withContext InstallResult.Failure("插件不存在")
        val url = meta.srcUrl ?: return@withContext InstallResult.Failure("插件没有 srcUrl，无法更新")
        val wasEnabled = meta.enabled
        val code = download(url) ?: return@withContext InstallResult.Failure("下载失败：$url")
        installCode(code, url).also {
            if (it is InstallResult.Success && !wasEnabled) setEnabled(it.meta.id, false)
        }
    }

    /** 安装/更新完成后通知 runtime 重载。 */
    fun notifyReload(platform: String? = null) {
        reloadHook?.invoke(platform)
    }

    /** 由 [MusicPluginBridge] 注入，用来丢弃 runtime 与查询缓存。 */
    var reloadHook: ((String?) -> Unit)? = null

    // ------------------------------------------------------------------ subscription parsing

    /**
     * 解析订阅 JSON。兼容：
     *
     * ```json
     * { "name": "源名", "plugins": [ { "name": "…", "url": "…", "version": "1.0.0" } ] }
     * ```
     * ```json
     * [ { "name": "…", "url": "…", "version": "1.0.0" } ]
     * ```
     * ```json
     * { "plugins": ["https://…/a.js"] }
     * ```
     * 也接受 `plugins[].srcUrl` / `downloadUrl` 的写法。
     */
    internal fun parseSubscription(text: String, sourceUrl: String): SubscriptionSource? {
        val trimmed = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return null

        val root = Json2.parse(trimmed) ?: return null
        val obj = root as? JsonObject
        val array = root as? JsonArray
        if (obj == null && array == null) return null

        val entries: List<SubscriptionEntry>
        val name: String
        if (array != null) {
            entries = array.mapNotNull(::toEntry)
            name = hostOf(sourceUrl)
        } else {
            val raw = obj!!["plugins"] as? JsonArray ?: return null
            entries = raw.mapNotNull(::toEntry)
            name = obj["name"].asStr() ?: hostOf(sourceUrl)
        }
        return SubscriptionSource(url = sourceUrl, name = name, plugins = entries.distinctBy { it.url })
    }

    private fun toEntry(element: JsonElement): SubscriptionEntry? {
        (element as? JsonPrimitive)?.let { p ->
            val url = p.content.trim()
            return url.takeIf { it.startsWith("http") }?.let {
                SubscriptionEntry(url = it, name = it.substringAfterLast('/').removeSuffix(".js"))
            }
        }
        val o = element as? JsonObject ?: return null
        val url = o["url"].asStr()
            ?: o["srcUrl"].asStr()
            ?: o["downloadUrl"].asStr()
            ?: return null
        if (!url.startsWith("http")) return null
        return SubscriptionEntry(
            name = o["name"].asStr() ?: o["platform"].asStr() ?: "",
            url = url,
            version = o["version"].asStr() ?: "",
        )
    }

    /** 单个插件描述 JSON（`{ "url": "…" }` / `{ "srcUrl": "…" }`）里真正的 .js 地址。 */
    private fun singleEntryUrl(text: String): String? {
        val trimmed = text.trim()
        if (!trimmed.startsWith("{")) return null
        val o = Json2.parse(trimmed) as? JsonObject ?: return null
        if (o.containsKey("plugins")) return null
        return (o["url"].asStr() ?: o["srcUrl"].asStr())?.takeIf { it.startsWith("http") }
    }

    // ------------------------------------------------------------------ internals

    private fun upsert(meta: PluginMeta) {
        val list = _plugins.value.toMutableList()
        val at = list.indexOfFirst { it.id == meta.id }
        if (at >= 0) list[at] = meta else list.add(meta)
        _plugins.value = list.ordered()
        store.writeIndex(_plugins.value)
        notifyReload(meta.platform)
    }

    /** 用户排序优先，[PluginMeta.sortOrder] 相同（全 0 的老数据）时按名称。 */
    private fun List<PluginMeta>.ordered(): List<PluginMeta> =
        sortedWith(compareBy({ it.sortOrder }, { it.name }))

    private fun readText(uri: Uri): String? = runCatching {
        val path = uri.path
        if (uri.scheme == "file" || uri.scheme == null) {
            if (path == null) null else File(path).readText()
        } else {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }
    }.getOrNull()

    /** 选中文件 / 订阅条目的展示名。 */
    fun displayNameOf(uri: Uri): String? = runCatching {
        if (uri.scheme == "file") return File(uri.path ?: return null).name
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    internal fun download(url: String): String? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", PluginRuntime.UA).build()
        http.newCall(req).execute().use { r -> if (!r.isSuccessful) null else r.body?.string() }
    }.getOrNull()

    /**
     * MusicFree 的版本号有时在同目录 `<name>.json`，有时直接写在 .js 里。
     */
    private fun fetchVersion(srcUrl: String): String? {
        val candidates = buildList {
            add(srcUrl)
            if (srcUrl.endsWith(".js")) add(srcUrl.dropLast(3) + ".json")
            if (srcUrl.endsWith(".user.js")) add(srcUrl.removeSuffix(".user.js") + ".json")
        }
        for (u in candidates) {
            val body = download(u) ?: continue
            val trimmed = body.trim()
            if (trimmed.startsWith("{")) {
                (Json2.parse(trimmed).asObj()?.get("version")).asStr()?.let { return it }
            } else if (u.endsWith(".js")) {
                Regex("""version\s*:\s*['"]([^'"]+)['"]""")
                    .find(body)?.groupValues?.getOrNull(1)?.let { return it }
            }
        }
        return null
    }

    internal fun compareVersion(a: String, b: String): Int {
        val pa = a.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        val pb = b.split('.', '-', '+').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val d = pa.getOrElse(i) { 0 } - pb.getOrElse(i) { 0 }
            if (d != 0) return d
        }
        return 0
    }

    private fun hostOf(url: String): String =
        url.substringAfter("://").substringBefore('/').ifBlank { url }

    /** 批量下载并发闸门：订阅源里几十个插件也不会瞬间打满连接池。 */
    private val gate = Semaphore(BATCH_SIZE)

    companion object {
        /** 内置默认订阅源：官方音源「一键安装 / 更新」。 */
        const val DEFAULT_SOURCE_URL = "https://hebijunge.github.io/musicfree-plugins/plugins.json"

        /** 一批同时下载多少个插件。 */
        const val BATCH_SIZE = 4

        /** 最多保留多少条「失败详情」现场。 */
        private const val DETAIL_LIMIT = 64
    }
}

/** 订阅源里的一条「可安装插件」。 */
data class CatalogEntry(
    val entry: SubscriptionEntry,
    val sourceUrl: String,
) {
    val key: String get() = entry.url
}

/** 批量任务进度。 */
data class BatchProgress(
    val stage: String = "",
    val done: Int = 0,
    val total: Int = 0,
) {
    val fraction: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val isIdle: Boolean get() = stage.isEmpty()
}

sealed interface InstallResult {
    data class Success(val meta: PluginMeta) : InstallResult
    data class Failure(val message: String) : InstallResult

    /** 订阅源 / 多选批量安装。 */
    data class BatchInstalled(val result: BatchInstallResult) : InstallResult
}

sealed interface UpdateCheck {
    data class Available(val current: String, val latest: String) : UpdateCheck
    data class Latest(val current: String) : UpdateCheck
    data class Error(val message: String) : UpdateCheck
    data object NoSource : UpdateCheck
    data object Unknown : UpdateCheck
}
