package com.guangyue.music.ui.home

import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// 类名带 CollectionLayout，CI「集合页布局单测」--tests '*CollectionLayout*' 会一起跑。
class CollectionLayoutFilterTest {
    private fun t(k: String, title: String, sub: String) = HomeTile(k, title, sub, kind = HomeTileKind.TRACK)
    private val tiles = listOf(
        t("a", "发如雪", "周杰伦 · 十一月的萧邦"),
        t("b", "Yesterday", "The Beatles · Help!"),
        t("c", "七里香", "周杰伦 · 七里香"),
        t("d", "Hey Jude", "The Beatles"),
    )

    @Test fun `空词或空白 = 没有筛选（null），不是空表`() {
        assertNull(CollectionFilter.matchIndexes(tiles, ""))
        assertNull(CollectionFilter.matchIndexes(tiles, "   "))
        assertEquals(tiles, CollectionFilter.apply(tiles, " "))
    }

    @Test fun `命中歌名或副标题，保持原顺序`() {
        assertEquals(listOf(0, 2), CollectionFilter.matchIndexes(tiles, "周杰伦"))
        assertEquals(listOf(2), CollectionFilter.matchIndexes(tiles, "七里"))
        assertEquals(listOf("b", "d"), CollectionFilter.apply(tiles, "beatles").map { it.key })
    }

    @Test fun `大小写不敏感、先 trim`() {
        assertEquals(listOf(1), CollectionFilter.matchIndexes(tiles, "  YESTER "))
        assertEquals(listOf(3), CollectionFilter.matchIndexes(tiles, "hey j"))
    }

    @Test fun `一首都没命中是空表`() {
        assertEquals(emptyList<Int>(), CollectionFilter.matchIndexes(tiles, "不存在"))
        assertEquals(emptyList<HomeTile>(), CollectionFilter.apply(tiles, "不存在"))
    }

    @Test fun `匹配数标签`() {
        assertEquals("2 / 4", CollectionFilter.matchLabel(2, 4))
    }
}
