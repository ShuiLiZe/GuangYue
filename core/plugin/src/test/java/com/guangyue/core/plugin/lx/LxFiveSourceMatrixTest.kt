package com.guangyue.core.plugin.lx

import com.guangyue.core.plugin.MusicItem
import com.guangyue.core.plugin.runtime.EmptyPreludeProvider
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 五源 × 四音质取链矩阵（真实网络）。
 *
 * 用 [docs/ui-0.0.8/lx/five-source-lx.js] 在 JVM 里跑真实的 LX 运行时，对
 * 酷我/酷狗/QQ/网易/咪咕 各取一首周杰伦的 128k/320k/flac/flac24bit 链接，
 * 结果原样写到 `docs/ui-0.0.8/lx/matrix-result.txt`，供 matrix.md 引用。
 *
 * 需要网络；脚本缺失就跳过。
 */
class LxFiveSourceMatrixTest {

    private fun scriptFile(): File? {
        val root = File(System.getProperty("guangyue.rootDir") ?: ".").absoluteFile
        return listOf(
            File(root, "build/lx/five-source-lx.js"),
            File(root, "docs/ui-0.0.8/lx/five-source-lx.js"),
            File(root, "core/plugin/build/lx/five-source-lx.js"),
        ).firstOrNull { it.isFile && it.length() > 1000 }
    }

    // platform 必须是宿主真实的平台键（kuwo/kugou/qq/netease/migu），并且写进 raw.platform：
    // 脚本用 raw.platform 判断「这份 musicInfo 到底是不是目标源搜出来的」，据此决定能不能
    // 跨源兜底。以前夹具把 songmid/hash/rid/songId 全填成同一个 id，等于告诉脚本
    // 「这歌在五个源里都有」，兜底才会拿别人家的 id 去要链、把试听片段当成功。
    private fun item(platform: String, id: String, title: String, raw: Map<String, String>): MusicItem =
        MusicItem(
            platform = platform,
            id = id,
            title = title,
            artist = "周杰伦",
            raw = JsonObject(
                (raw + ("platform" to platform)).mapValues { JsonPrimitive(it.value) },
            ),
        )

    @Test
    fun `五源四音质取链矩阵`() {
        val script = scriptFile() ?: run {
            println("跳过：找不到 five-source-lx.js")
            return
        }
        val root = File(System.getProperty("guangyue.rootDir") ?: ".").absoluteFile
        val runtime = LxRuntime(
            source = LxSource(id = "five-source", name = "五源合一"),
            code = script.readText(),
            http = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build(),
            preludeProvider = EmptyPreludeProvider(),
        )
        val logs = mutableListOf<String>()
        runtime.onLog = { level, msg -> synchronized(logs) { logs.add("[$level] $msg") } }
        runtime.onHttp = { m, u, s, ms -> synchronized(logs) { logs.add("[http] $m $s ${ms}ms ${u.take(160)}") } }

        val cases = listOf(
            Triple("kw", item("kuwo", "474678847", "花海", mapOf("songmid" to "474678847", "musicId" to "474678847", "copyrightId" to "474678847")), "酷我 花海(474678847)"),
            // 酷狗：周杰伦 studio 版全曲 pay_type=3（付费），未登录 tracker 一律 status=2；
            // 这是 120 首全量扫描里唯一 status=1（可出链）的周杰伦曲目。
            Triple("kg", item("kugou", "b9b3a55ffb5b95f165839a26120e9fec", "恒星不忘 Forever Forever", mapOf("hash" to "b9b3a55ffb5b95f165839a26120e9fec", "albumId" to "3521156714")), "酷狗 恒星不忘(周杰伦/言承旭/吴建豪/周渝民)"),
            Triple("tx", item("qq", "0039MnYb0qxYhV", "晴天", mapOf("songmid" to "0039MnYb0qxYhV", "mediaMid" to "003Qui1q2u1Zho", "strMediaMid" to "003Qui1q2u1Zho", "albumMid" to "000MkMni19ClKG")), "QQ 晴天(0039MnYb0qxYhV)"),
            Triple("wy", item("netease", "5257138", "屋顶", mapOf("songmid" to "5257138", "musicId" to "5257138", "id" to "5257138")), "网易 屋顶(5257138，周杰伦/温岚)"),
            // 咪咕：周杰伦 studio 版全曲 440013（需白金会员）；160 首扫描里 21 首 Live/合作版 PQ 出链。
            Triple("mg", item("migu", "600913000007163534", "晴天 (Live)", mapOf("songmid" to "600913000007163534", "contentId" to "600913000007163534", "songId" to "600913000007163534")), "咪咕 晴天(Live) contentId 600913000007163534"),
        )
        val qualities = listOf("128k", "320k", "flac", "flac24bit")

        val out = StringBuilder()
        out.appendLine("# 五源 × 四音质取链原始结果")
        out.appendLine("# 脚本 = ${script.relativeTo(root)}  生成时间 = ${java.time.LocalDateTime.now()}")
        out.appendLine("# 平台上报 = ${runtime.platforms}")
        out.appendLine()
        try {
            runtime.install()
            out.appendLine("# 平台上报 = ${runtime.platforms}")
            out.appendLine()
            for ((platform, music, label) in cases) {
                val info = LxMusicInfo.of(platform, music)
                out.appendLine("## $platform  $label")
                out.appendLine("musicInfo = $info")
                for (q in qualities) {
                    synchronized(logs) { logs.clear() }
                    val t0 = System.currentTimeMillis()
                    val line = try {
                        val url = runtime.musicUrl(platform, q, info ?: JsonObject(emptyMap()))
                        val ms = System.currentTimeMillis() - t0
                        "OK   ${url.take(400)}   (${ms}ms)"
                    } catch (t: Throwable) {
                        val ms = System.currentTimeMillis() - t0
                        "FAIL ${t.message?.take(400)}   (${ms}ms)"
                    }
                    out.appendLine("  $q : $line")
                    synchronized(logs) { logs.toList() }.takeLast(24).forEach { out.appendLine("        log $it") }
                    println("$platform $q => $line")
                }
                out.appendLine()
            }
        } finally {
            runtime.close()
            val dest = File(root, "docs/ui-0.0.8/lx/matrix-result.txt")
            dest.parentFile?.mkdirs()
            dest.writeText(out.toString())
            println("矩阵结果写入 ${dest.absolutePath}")
        }
    }
}