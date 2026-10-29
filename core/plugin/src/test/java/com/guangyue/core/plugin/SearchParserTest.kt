package com.guangyue.core.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 插件返回值 → 模型对象的映射（0.0.7 从 [MusicPluginBridge] 里抽出来，好让它可测）。
 *
 * 这一层是「导入成功但搜不到」的重灾区：插件给的是 `{isEnd, data:[...]}`，字段名五花八门
 * （`songmid`/`songname`/`singer`/`interval` 秒），只要有一处没对上，UI 就会拿到空列表或
 * 「未知歌曲」，用户看到的就是空白搜索页。
 */
class SearchParserTest {

    private fun json(text: String) = Json.parseToJsonElement(text)

    @Test
    fun `array payload maps to music items`() {
        val result = SearchParser.parse(
            json("""[{"id":"1","title":"晴天","artist":"周杰伦","album":"叶惠美","artwork":"https://x/a.jpg"}]"""),
            "demo",
            SearchType.MUSIC,
        )
        assertEquals(1, result.items.size)
        val item = result.items.first()
        assertEquals("1", item.id)
        assertEquals("晴天", item.title)
        assertEquals("周杰伦", item.artist)
        assertEquals("叶惠美", item.album)
        assertEquals("https://x/a.jpg", item.artwork)
        assertEquals("demo", item.platform)
        assertTrue("数组返回且非空，应有下一页", !result.isEnd)
    }

    @Test
    fun `page envelope with isEnd and data is honoured`() {
        val result = SearchParser.parse(
            json("""{"isEnd":true,"data":[{"id":"2","title":"七里香","artist":"周杰伦"}]}"""),
            "demo",
            SearchType.MUSIC,
        )
        assertEquals(1, result.items.size)
        assertTrue("插件明确 isEnd=true 时不再翻页", result.isEnd)
    }

    @Test
    fun `empty page is treated as end`() {
        val result = SearchParser.parse(json("""{"data":[]}"""), "demo", SearchType.MUSIC)
        assertTrue(result.items.isEmpty())
        assertTrue("空页 = 没有下一页", result.isEnd)
    }

    @Test
    fun `field aliases used by real plugins are read`() {
        val result = SearchParser.parse(
            json("""[{"songmid":"003","songname":"稻香","singer":"周杰伦","albumName":"魔杰座","interval":223}]"""),
            "qq",
            SearchType.MUSIC,
        )
        val item = result.items.single()
        assertEquals("003", item.id)
        assertEquals("稻香", item.title)
        assertEquals("周杰伦", item.artist)
        assertEquals("魔杰座", item.album)
        assertEquals("秒为单位的时间要乘 1000", 223_000L, item.durationMs)
    }

    @Test
    fun `missing title and artist fall back instead of dropping the row`() {
        val result = SearchParser.parse(json("""[{"id":"9"}]"""), "demo", SearchType.MUSIC)
        val item = result.items.single()
        assertEquals("未知歌曲", item.title)
        assertEquals("未知艺术家", item.artist)
    }

    @Test
    fun `rows without id are dropped`() {
        val result = SearchParser.parse(json("""[{"title":"没有 id"},{"id":"10","title":"有 id"}]"""), "demo", SearchType.MUSIC)
        assertEquals(1, result.items.size)
        assertEquals("10", result.items.first().id)
    }

    @Test
    fun `album type lands in albums bucket`() {
        val result = SearchParser.parse(
            json("""{"isEnd":true,"data":[{"id":"al1","title":"叶惠美","artist":"周杰伦"}]}"""),
            "demo",
            SearchType.ALBUM,
        )
        assertTrue(result.items.isEmpty())
        assertEquals("al1", result.albums.single().id)
        assertEquals("叶惠美", result.albums.single().title)
    }

    @Test
    fun `artist type lands in artists bucket`() {
        val result = SearchParser.parse(
            json("""{"isEnd":true,"data":[{"id":"ar1","name":"周杰伦","avatar":"https://x/b.jpg"}]}"""),
            "demo",
            SearchType.ARTIST,
        )
        assertTrue(result.items.isEmpty())
        assertEquals("周杰伦", result.artists.single().name)
    }

    @Test
    fun `sheet type reuses album bucket`() {
        val result = SearchParser.parse(
            json("""{"isEnd":true,"data":[{"id":"sh1","title":"周杰伦精选"}]}"""),
            "demo",
            SearchType.SHEET,
        )
        assertEquals("sh1", result.albums.single().id)
    }

    @Test
    fun `media source keeps url headers and mime type`() {
        val source = SearchParser.mediaSource(
            json("""{"url":"https://x/a.mp3","headers":{"Referer":"https://music.163.com"},"mimeType":"audio/mpeg"}"""),
        )
        assertEquals("https://x/a.mp3", source?.url)
        assertEquals("https://music.163.com", source?.headers?.get("Referer"))
        assertEquals("audio/mpeg", source?.mimeType)
    }

    @Test
    fun `blank url means no playable source`() {
        assertNull(SearchParser.mediaSource(json("""{"url":"  "}""")))
        assertNull(SearchParser.mediaSource(json("""{"quality":"standard"}""")))
        assertNull(SearchParser.mediaSource(null))
    }

    @Test
    fun `url can also be returned as bare string`() {
        // 少数插件直接 return "https://..."（不是对象）
        assertNull("裸字符串不是契约里的形状，按取不到处理", SearchParser.mediaSource(JsonPrimitive("https://x/a.mp3")))
    }
}