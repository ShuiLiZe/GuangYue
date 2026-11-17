package com.guangyue.feature.library.design

import com.guangyue.feature.library.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderLogosTest {
    @Test fun recognisesPlatforms() {
        assertEquals(R.drawable.provider_netease, ProviderLogos.resFor("网易云音乐"))
        assertEquals(R.drawable.provider_qq, ProviderLogos.resFor("QQ音乐"))
        assertEquals(R.drawable.provider_kugou, ProviderLogos.resFor(null, "酷狗"))
        assertEquals(R.drawable.provider_qq, ProviderLogos.resFor("tx"))
        assertEquals(R.drawable.provider_lxmusic, ProviderLogos.resFor("洛雪音源"))
    }

    @Test fun shortCodesOnlyWhole() {
        assertNull(ProviderLogos.resFor("Spotify"))
        assertNull(ProviderLogos.resFor("kgb 电台"))
        assertNull(ProviderLogos.resFor("", null))
    }
}
