package com.guangyue.music.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.Buffer
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * JVM 截图内的假图标源：Robolectric 环境没有网络，Coil 没法真去抓 favicon。
 * 对任意 http(s) URL 现画一张确定性的彩色圆点（颜色由 URL 哈希决定），
 * 只用于让「插件列表图标」验收图能看到图标，不参与任何生产逻辑。
 */
internal object FakePluginIcons {

    private val LABELS = listOf(
        "kugou.com" to "酷",
        "kuwo.cn" to "我",
        "y.qq.com" to "Q",
        "qq.com" to "Q",
        "163.com" to "网",
        "douyin.com" to "汽",
        "migu.cn" to "咪",
        "bilibili.com" to "B",
        "91q.com" to "千",
        "fanqieting.com" to "番",
    )

    private val cache = ConcurrentHashMap<String, ByteArray>()

    fun png(url: String): ByteArray = cache.getOrPut(url) { draw(url) }

    private fun draw(url: String): ByteArray {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val hue = ((url.hashCode() % 360) + 360) % 360
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.72f, 0.88f))
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.color = Color.WHITE
        paint.textSize = size * 0.52f
        paint.textAlign = Paint.Align.CENTER
        val label = LABELS.firstOrNull { url.contains(it.first) }?.second ?: "?"
        canvas.drawText(label, size / 2f, size / 2f + paint.textSize / 3f, paint)
        val out = File.createTempFile("fake-icon", ".png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return out.readBytes()
    }
}

internal class FakePluginIconFetcher(private val data: String) : Fetcher {
    override suspend fun fetch(): FetchResult =
        SourceFetchResult(
            source = ImageSource(Buffer().apply { write(FakePluginIcons.png(data)) }, okio.FileSystem.SYSTEM),
            mimeType = "image/png",
            dataSource = DataSource.NETWORK,
        )
}

internal class FakePluginIconFetcherFactory : Fetcher.Factory<Uri> {
    override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
        val text = data.toString()
        if (!text.startsWith("http")) return null
        return FakePluginIconFetcher(text)
    }
}
