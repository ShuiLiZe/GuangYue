package com.guangyue.core.plugin.lx

import android.content.Context
import androidx.annotation.WorkerThread
import com.guangyue.core.plugin.Json2
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 洛雪自定义源的磁盘存储。
 *
 * ```
 * filesDir/lx/
 *   <sourceId>/
 *     script.js      // 原始 .js 文本
 *   index.json       // 已安装列表（LxSource）
 * ```
 *
 * 与 [com.guangyue.core.plugin.PluginStore] 平行、互不干扰：洛雪脚本没有 `module.exports`，
 * 元信息只能从脚本头注释 `@name` / `@version` / `@author` 里抠，落盘结构也不同。
 */
@Singleton
class LxStore @Inject constructor(@ApplicationContext private val context: Context) {

    val root: File get() = File(context.filesDir, "lx").apply { if (!exists()) mkdirs() }

    val indexFile: File get() = File(root, "index.json")

    fun dirOf(id: String): File = File(root, id)

    fun scriptFile(id: String): File = File(dirOf(id), "script.js")

    @WorkerThread
    fun readIndex(): List<LxSource> {
        val text = runCatching { indexFile.readText() }.getOrNull() ?: return emptyList()
        return runCatching { Json2.json.decodeFromString(LIST, text) }.getOrElse { emptyList() }
    }

    @WorkerThread
    fun writeIndex(list: List<LxSource>) {
        runCatching { indexFile.writeText(Json2.json.encodeToString(LIST, list)) }
    }

    @WorkerThread
    fun readScript(id: String): String? = scriptFile(id).takeIf { it.isFile }?.readText()

    @WorkerThread
    fun writeScript(id: String, code: String) {
        val dir = dirOf(id).apply { if (!exists()) mkdirs() }
        val target = File(dir, "script.js")
        val tmp = File(dir, "script.js.tmp")
        tmp.writeText(code)
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    @WorkerThread
    fun delete(id: String) {
        dirOf(id).deleteRecursively()
    }

    @WorkerThread
    fun totalBytes(): Long = root.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    /** 稳定的 id：脚本名 + 源码哈希，重复导入同一条不会堆两份。 */
    fun idFor(name: String, source: String): String {
        val base = name.ifBlank { "lx" }.lowercase().replace(Regex("[^a-z0-9_.-]"), "-").take(24)
        val h = MessageDigest.getInstance("SHA-1").digest(source.toByteArray()).take(4)
            .joinToString("") { "%02x".format(it) }
        return "$base-$h"
    }

    private companion object {
        val SERIALIZER = kotlinx.serialization.serializer<LxSource>()
        val LIST = ListSerializer(SERIALIZER)
    }
}

/**
 * 从洛雪脚本头部的块注释里抠元信息。
 *
 * 洛雪自定义源的标准头部长这样（块注释里每行一个 `@name 值`）：
 * ```
 * @name 五源合一
 * @description QQ音乐/网易云/酷狗/酷我/咪咕
 * @version 1.6.0
 * @author 研发1号
 * @homepage https://github.com/…
 * ```
 * 解析失败不抛异常：名字退回「未命名取链源」，其余留空。
 */
object LxMetaParser {

    fun parse(code: String, fallbackName: String = ""): LxSource {
        val header = headerBlock(code)
        fun field(vararg keys: String): String? {
            for (key in keys) {
                val re = Regex(
                    "[@*]\\s*" + Regex.escape(key) + "\\s*[:：]?\\s*(.+)",
                    RegexOption.IGNORE_CASE,
                )
                val hit = re.find(header)?.groupValues?.getOrNull(1)?.trim()
                if (!hit.isNullOrBlank()) return hit.trim('*', ' ', '\t', '/')
            }
            return null
        }
        val name = field("name", "标题") ?: fallbackName
        return LxSource(
            id = "",
            name = name,
            version = field("version", "版本") ?: "",
            author = field("author", "作者") ?: "",
            description = field("description", "简介") ?: "",
            homepage = field("homepage", "home", "地址"),
            sizeBytes = code.toByteArray().size.toLong(),
        )
    }

    /** 取脚本最前面的块注释；没有块注释就扫前 4KB 行注释。 */
    private fun headerBlock(code: String): String {
        val head = code.take(6000)
        val block = Regex("/\\*[\\s\\S]*?\\*/").find(head)
        if (block != null) return block.value
        return head.lineSequence().filter { it.trimStart().startsWith("//") }.joinToString("\n")
    }
}