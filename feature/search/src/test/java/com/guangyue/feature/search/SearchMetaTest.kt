package com.guangyue.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

/** Folia `search.resultCount`「共 {{n}} 条」；还有下一页时 n 带「+」（SearchWorkspace countLabel）。 */
class SearchMetaTest {
    @Test fun countWithoutMore() = assertEquals("共 12 条", searchCountLabel(12, more = false))
    @Test fun countWithMore() = assertEquals("共 30+ 条", searchCountLabel(30, more = true))
}
