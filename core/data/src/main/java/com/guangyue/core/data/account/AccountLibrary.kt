package com.guangyue.core.data.account

import android.content.Context
import com.guangyue.core.data.model.TrackEntity
import com.guangyue.core.plugin.MusicPluginBridge
import com.guangyue.core.plugin.PluginManager
import com.guangyue.core.plugin.login.QrPlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import com.guangyue.core.plugin.login.NeteaseCrypto
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** 一次同步的结论（Folia App.tsx：synced → status.dataSynced；ran-out → status.loginExpired；其余 → status.syncFailed）。 */
enum class AccountSyncOutcome { SYNCED, EXPIRED, FAILED, NOT_SIGNED_IN }

/** 读歌单曲目时登录已失效。 */
class AccountExpiredException : Exception("登录状态已失效，请重新登录")

/**
 * 登录后的账号数据（Folia account/accountRead.ts + syncAccountData.ts 的手机版）：
 * 扫码成功自动读一次（reason=sign-in）、「同步数据」手动重读、启动时有凭据就安静地读一次；
 * 读到的歌单整份落到 files/account/，重开 App 先显示缓存，不必等网络。
 *
 * QQ 音乐（musicu.fcg）与网易云（eapi）；酷狗的账号读取还没做（见 docs/port/folia-diff.md）。
 */
@Singleton
class AccountLibrary @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plugins: PluginManager,
    private val bridge: MusicPluginBridge,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Mutex()
    private val http = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val _qqPlaylists = MutableStateFlow<List<AccountPlaylist>>(emptyList())
    /** QQ 账号的歌单（我喜欢置顶 → 自建 → 收藏）；未登录或还没读过是空的。 */
    val qqPlaylists: StateFlow<List<AccountPlaylist>> = _qqPlaylists.asStateFlow()

    private val _nePlaylists = MutableStateFlow<List<AccountPlaylist>>(emptyList())
    /** 网易云账号的歌单（我喜欢的音乐置顶 → 自建 → 收藏）；未登录或还没读过是空的。 */
    val neteasePlaylists: StateFlow<List<AccountPlaylist>> = _nePlaylists.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    @Serializable
    private data class Snapshot(val uin: String = "", val syncedAt: Long = 0L, val playlists: List<AccountPlaylist> = emptyList())

    @Serializable
    private data class TrackSnapshot(val songs: List<AccountSong> = emptyList())

    @Serializable
    private data class NeSnapshot(val uid: String = "", val syncedAt: Long = 0L, val playlists: List<AccountPlaylist> = emptyList())

    @Serializable
    private data class NeTrackSnapshot(val songs: List<NeteaseSong> = emptyList())

    private val dir get() = File(context.filesDir, "account").apply { mkdirs() }
    private val qqFile get() = File(dir, "qq_playlists.json")
    private fun qqTracksFile(id: String) = File(dir, "qq_tracks_" + id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
    private val neFile get() = File(dir, "netease_playlists.json")
    private fun neTracksFile(id: String) = File(dir, "netease_tracks_" + id.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json")
    private val prefs get() = context.getSharedPreferences("account_credentials", Context.MODE_PRIVATE)

    init {
        scope.launch {
            val snap = runCatching { json.decodeFromString(Snapshot.serializer(), qqFile.readText()) }.getOrNull()
            if (snap != null && _qqPlaylists.value.isEmpty()) _qqPlaylists.value = snap.playlists
            val ne = runCatching { json.decodeFromString(NeSnapshot.serializer(), neFile.readText()) }.getOrNull()
            if (ne != null && _nePlaylists.value.isEmpty()) _nePlaylists.value = ne.playlists
        }
    }

    // ———————————————————————— 凭据 ————————————————————————

    /** 扫码成功：把 uin / musickey 存下来（只存在本机私有目录）。 */
    fun saveQqCredential(fields: Map<String, String>) {
        val c = QqCredential.fromFields(fields) ?: return
        prefs.edit().putString("qq_uin", c.uin).putString("qq_key", c.musicKey).apply()
    }

    /** 退出登录：凭据、歌单、曲目缓存一起清掉。 */
    fun clearQq() {
        prefs.edit().remove("qq_uin").remove("qq_key").apply()
        _qqPlaylists.value = emptyList()
        scope.launch { dir.listFiles()?.filter { it.name.startsWith("qq_") }?.forEach { it.delete() } }
    }

    private fun qqPluginsOrdered() = plugins.plugins.value
        .filter { (QrPlatform.of(it.platform) ?: QrPlatform.of(it.name)) == QrPlatform.QQ }
        .sortedByDescending { it.enabled && it.error == null }

    /**
     * 存下的凭据；0.0.30 及以前扫码只把凭据写进了插件账号，这里回头从 QQ 插件的账号变量里认一次（认到就补存）。
     */
    private suspend fun qqCredential(): QqCredential? {
        val uin = prefs.getString("qq_uin", null).orEmpty()
        val key = prefs.getString("qq_key", null).orEmpty()
        if (uin.isNotBlank() && key.isNotBlank()) return QqCredential(uin, key)
        runCatching { plugins.load() }
        for (p in qqPluginsOrdered()) {
            val vars = runCatching { bridge.readUserVariables(p.id) }.getOrDefault(emptyMap())
            val c = QqCredential.fromFields(vars) ?: continue
            prefs.edit().putString("qq_uin", c.uin).putString("qq_key", c.musicKey).apply()
            return c
        }
        return null
    }

    suspend fun hasQqCredential(): Boolean = withContext(Dispatchers.IO) { qqCredential() != null }

    /** 播放这些歌用哪个音源：已装的 QQ 插件的 platform（取链按 platform 找插件，找不到时洛雪源按「qq」认 tx）。 */
    fun qqTrackPlatform(): String = qqPluginsOrdered().firstOrNull()?.platform?.takeIf { it.isNotBlank() } ?: "QQ音乐"

    // ———————————————————————— 网络 ————————————————————————

    private fun post(c: QqCredential, body: String): String {
        val req = Request.Builder().url(QqAccountApi.MUSICU_URL)
            .header("Referer", "https://y.qq.com/")
            .header("Origin", "https://y.qq.com")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
            .header("Cookie", QqAccountApi.cookie(c))
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body.string()
        }
    }

    private val _qqExpired = MutableStateFlow(false)
    /** 最近一次读账号时 QQ 说登录已失效（同步成功后复位）。 */
    val qqExpired: StateFlow<Boolean> = _qqExpired.asStateFlow()

    /** 读一次 QQ 账号歌单。失败时不动已有的歌单与缓存。 */
    suspend fun syncQq(): AccountSyncOutcome = withContext(Dispatchers.IO) {
        lock.withLock {
            val c = qqCredential() ?: return@withLock AccountSyncOutcome.NOT_SIGNED_IN
            _syncing.value = true
            try {
                val text = runCatching { post(c, QqAccountApi.playlistsBody(c)) }
                    .onFailure { android.util.Log.w("GYACCOUNT", "qq playlists request failed", it) }
                    .getOrNull() ?: return@withLock AccountSyncOutcome.FAILED
                when (val r = QqAccountApi.parsePlaylists(text)) {
                    is QqRead.Ok -> {
                        _qqPlaylists.value = r.value
                        _qqExpired.value = false
                        runCatching { qqFile.writeText(json.encodeToString(Snapshot.serializer(), Snapshot(c.uin, System.currentTimeMillis(), r.value))) }
                        android.util.Log.i("GYACCOUNT", "qq playlists synced n=${r.value.size}")
                        AccountSyncOutcome.SYNCED
                    }
                    QqRead.Expired -> { _qqExpired.value = true; AccountSyncOutcome.EXPIRED }
                    is QqRead.Failed -> {
                        android.util.Log.w("GYACCOUNT", "qq playlists refused: ${r.reason}")
                        AccountSyncOutcome.FAILED
                    }
                }
            } finally {
                _syncing.value = false
            }
        }
    }

    fun playlist(id: String): AccountPlaylist? = _qqPlaylists.value.firstOrNull { it.id == id }

    private fun AccountSong.toEntity(platform: String) = TrackEntity(
        key = "$platform:$id",
        title = title,
        artist = artist,
        album = album,
        artwork = artwork,
        durationMs = durationSec * 1000L,
        platform = platform,
        songId = id,
        source = TrackEntity.SOURCE_PLUGIN,
        rawJson = toRaw(platform).toString(),
    )

    /**
     * 一张账号歌单的全部曲目（一页 [PAGE] 首往下翻，最多 [MAX_SONGS] 首）。读不到时用上次缓存的；
     * 也没有缓存就抛出（登录失效抛 [AccountExpiredException]），集合页显示「加载失败，点此重试」。
     */
    suspend fun qqTracks(id: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        val platform = qqTrackPlatform()
        val p = playlist(id) ?: error("找不到这张歌单")
        val cached = runCatching { json.decodeFromString(TrackSnapshot.serializer(), qqTracksFile(id).readText()).songs }.getOrNull()
        val c = qqCredential()
        if (c == null) _qqExpired.value = true
        val fresh: Result<List<AccountSong>> = if (c == null) Result.failure(AccountExpiredException()) else runCatching {
            val acc = ArrayList<AccountSong>()
            var begin = 0
            while (begin < MAX_SONGS) {
                val page = when (val r = QqAccountApi.parseSongs(post(c, QqAccountApi.songsBody(c, p, begin, PAGE)))) {
                    is QqRead.Ok -> r.value
                    QqRead.Expired -> throw AccountExpiredException()
                    is QqRead.Failed -> error(r.reason)
                }
                acc += page.songs
                begin += page.listed
                val total = page.total
                if (page.listed == 0 || page.listed < PAGE && total == null || total != null && begin >= total) break
            }
            acc.distinctBy { it.id }
        }
        fresh.getOrNull()?.let { songs ->
            _qqExpired.value = false
            runCatching { qqTracksFile(id).writeText(json.encodeToString(TrackSnapshot.serializer(), TrackSnapshot(songs))) }
            return@withContext songs.map { it.toEntity(platform) }
        }
        android.util.Log.w("GYACCOUNT", "qq tracks $id failed", fresh.exceptionOrNull())
        if (fresh.exceptionOrNull() is AccountExpiredException) _qqExpired.value = true
        cached?.map { it.toEntity(platform) } ?: throw (fresh.exceptionOrNull() ?: IllegalStateException("读取失败"))
    }

    // ———————————————————————— 网易云 ————————————————————————

    /** 扫码成功：把 MUSIC_U / __csrf 存下来（只存在本机私有目录）。 */
    fun saveNeteaseCredential(fields: Map<String, String>) {
        val c = NeteaseCredential.fromFields(fields) ?: return
        prefs.edit().putString("ne_music_u", c.musicU).putString("ne_csrf", c.csrf).remove("ne_uid").apply()
    }

    /** 退出登录：凭据、歌单、曲目缓存一起清掉。 */
    fun clearNetease() {
        prefs.edit().remove("ne_music_u").remove("ne_csrf").remove("ne_uid").apply()
        _nePlaylists.value = emptyList()
        _neExpired.value = false
        scope.launch { dir.listFiles()?.filter { it.name.startsWith("netease_") }?.forEach { it.delete() } }
    }

    private fun nePluginsOrdered() = plugins.plugins.value
        .filter { (QrPlatform.of(it.platform) ?: QrPlatform.of(it.name)) == QrPlatform.NETEASE }
        .sortedByDescending { it.enabled && it.error == null }

    /** 存下的 MUSIC_U；0.0.30 及以前扫码只写进了网易云插件的账号变量，这里回头认一次（认到就补存）。 */
    private suspend fun neCredential(): NeteaseCredential? {
        val u = prefs.getString("ne_music_u", null).orEmpty()
        if (u.isNotBlank()) return NeteaseCredential(u, prefs.getString("ne_csrf", null).orEmpty())
        runCatching { plugins.load() }
        for (p in nePluginsOrdered()) {
            val vars = runCatching { bridge.readUserVariables(p.id) }.getOrDefault(emptyMap())
            val c = NeteaseCredential.fromFields(vars) ?: continue
            prefs.edit().putString("ne_music_u", c.musicU).putString("ne_csrf", c.csrf).apply()
            return c
        }
        return null
    }

    suspend fun hasNeteaseCredential(): Boolean = withContext(Dispatchers.IO) { neCredential() != null }

    /** 播放这些歌用哪个音源：已装的网易云插件的 platform（找不到时洛雪源按「网易云」认 wy）。 */
    fun neteaseTrackPlatform(): String = nePluginsOrdered().firstOrNull()?.platform?.takeIf { it.isNotBlank() } ?: "网易云"

    private val neDevice: String
        get() = prefs.getString("ne_device", null) ?: NeteaseCrypto.randomDeviceId().also { prefs.edit().putString("ne_device", it).apply() }

    /** 一次 eapi 请求（与扫码登录同一套 header / 加密）。 */
    private fun nePost(c: NeteaseCredential, path: String, data: Map<String, kotlinx.serialization.json.JsonElement>): String {
        val header = NeteaseCrypto.desktopHeader(neDevice, c.csrf, "${System.currentTimeMillis()}_${(1000..9999).random()}")
        val req = Request.Builder().url(NeteaseCrypto.eapiUrl(path))
            .header("User-Agent", NeteaseCrypto.USER_AGENT)
            .header("Referer", NeteaseCrypto.REFERER)
            .header("Cookie", NeteaseCrypto.cookieHeader(header, c.musicU))
            .post(FormBody.Builder().add("params", NeteaseAccountApi.params(path, data, header)).build())
            .build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            return r.body.string()
        }
    }

    private val _neExpired = MutableStateFlow(false)
    /** 最近一次读账号时网易云说登录已失效（同步成功后复位）。 */
    val neteaseExpired: StateFlow<Boolean> = _neExpired.asStateFlow()

    /** 读一次网易云账号歌单：先问谁登录着（拿 uid），再翻 /user/playlist。失败时不动已有的歌单与缓存。 */
    suspend fun syncNetease(): AccountSyncOutcome = withContext(Dispatchers.IO) {
        lock.withLock {
            val c = neCredential() ?: return@withLock AccountSyncOutcome.NOT_SIGNED_IN
            _syncing.value = true
            try {
                val outcome = runCatching {
                    val uid = when (val a = NeteaseAccountApi.parseAccount(nePost(c, NeteaseAccountApi.ACCOUNT_PATH, NeteaseAccountApi.accountData()))) {
                        is QqRead.Ok -> a.value
                        QqRead.Expired -> return@runCatching AccountSyncOutcome.EXPIRED
                        is QqRead.Failed -> { android.util.Log.w("GYACCOUNT", "netease account refused: ${a.reason}"); return@runCatching AccountSyncOutcome.FAILED }
                    }
                    val all = ArrayList<AccountPlaylist>()
                    var offset = 0
                    while (offset < MAX_PLAYLISTS) {
                        val page = when (val r = NeteaseAccountApi.parsePlaylistPage(nePost(c, NeteaseAccountApi.PLAYLISTS_PATH, NeteaseAccountApi.playlistsData(uid, offset)), uid)) {
                            is QqRead.Ok -> r.value
                            QqRead.Expired -> return@runCatching AccountSyncOutcome.EXPIRED
                            is QqRead.Failed -> { android.util.Log.w("GYACCOUNT", "netease playlists refused: ${r.reason}"); return@runCatching AccountSyncOutcome.FAILED }
                        }
                        all += page.playlists
                        offset += page.listed
                        if (!page.more || page.listed == 0) break
                    }
                    val list = NeteaseAccountApi.order(all)
                    _nePlaylists.value = list
                    prefs.edit().putString("ne_uid", uid).apply()
                    runCatching { neFile.writeText(json.encodeToString(NeSnapshot.serializer(), NeSnapshot(uid, System.currentTimeMillis(), list))) }
                    android.util.Log.i("GYACCOUNT", "netease playlists synced n=${list.size}")
                    AccountSyncOutcome.SYNCED
                }.onFailure { android.util.Log.w("GYACCOUNT", "netease playlists request failed", it) }
                    .getOrDefault(AccountSyncOutcome.FAILED)
                _neExpired.value = outcome == AccountSyncOutcome.EXPIRED
                outcome
            } finally {
                _syncing.value = false
            }
        }
    }

    fun neteasePlaylist(id: String): AccountPlaylist? = _nePlaylists.value.firstOrNull { it.id == id }

    private fun NeteaseSong.toEntity(platform: String) = TrackEntity(
        key = "$platform:$id",
        title = title,
        artist = artist,
        album = album,
        artwork = cover,
        durationMs = durationMs,
        platform = platform,
        songId = id,
        source = TrackEntity.SOURCE_PLUGIN,
        rawJson = toRaw(platform).toString(),
    )

    /**
     * 一张网易云账号歌单的全部曲目：v6/playlist/detail 拿 trackIds，缺的按 [NeteaseAccountApi.SONG_DETAIL_BATCH] 一批
     * 用 /song/detail 补（最多 [MAX_SONGS] 首）。读不到时用上次缓存；也没有缓存就抛出（登录失效抛 [AccountExpiredException]）。
     */
    suspend fun neteaseTracks(id: String): List<TrackEntity> = withContext(Dispatchers.IO) {
        val platform = neteaseTrackPlatform()
        neteasePlaylist(id) ?: error("找不到这张歌单")
        val cached = runCatching { json.decodeFromString(NeTrackSnapshot.serializer(), neTracksFile(id).readText()).songs }.getOrNull()
        val c = neCredential()
        if (c == null) _neExpired.value = true
        val fresh: Result<List<NeteaseSong>> = if (c == null) Result.failure(AccountExpiredException()) else runCatching {
            val detail = when (val r = NeteaseAccountApi.parseDetail(nePost(c, NeteaseAccountApi.DETAIL_PATH, NeteaseAccountApi.detailData(id)))) {
                is QqRead.Ok -> r.value.let { it.copy(trackIds = it.trackIds.take(MAX_SONGS)) }
                QqRead.Expired -> throw AccountExpiredException()
                is QqRead.Failed -> error(r.reason)
            }
            val extra = ArrayList<NeteaseSong>()
            for (batch in NeteaseAccountApi.missingBatches(detail)) {
                extra += when (val r = NeteaseAccountApi.parseSongDetail(nePost(c, NeteaseAccountApi.SONG_DETAIL_PATH, NeteaseAccountApi.songDetailData(batch)))) {
                    is QqRead.Ok -> r.value
                    QqRead.Expired -> throw AccountExpiredException()
                    is QqRead.Failed -> error(r.reason)
                }
            }
            NeteaseAccountApi.assemble(detail, extra)
        }
        fresh.getOrNull()?.let { songs ->
            _neExpired.value = false
            runCatching { neTracksFile(id).writeText(json.encodeToString(NeTrackSnapshot.serializer(), NeTrackSnapshot(songs))) }
            return@withContext songs.map { it.toEntity(platform) }
        }
        android.util.Log.w("GYACCOUNT", "netease tracks $id failed", fresh.exceptionOrNull())
        if (fresh.exceptionOrNull() is AccountExpiredException) _neExpired.value = true
        cached?.map { it.toEntity(platform) } ?: throw (fresh.exceptionOrNull() ?: IllegalStateException("读取失败"))
    }

    private companion object {
        const val PAGE = 500
        const val MAX_SONGS = 10_000
        const val MAX_PLAYLISTS = 5_000
    }
}
