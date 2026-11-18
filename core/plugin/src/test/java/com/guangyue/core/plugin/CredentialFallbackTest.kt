package com.guangyue.core.plugin

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 登录后取链取不到要回落到未登录取链（用户 11-18：登录 QQ 后全都听不了）。 */
class CredentialFallbackTest {
    @Test fun noAccountOnlyAnonymous() = runBlocking {
        var withCalls = 0
        val r = CredentialFallback.run(false, { withCalls++; "acc" }, { "anon" })
        assertEquals("anon", r); assertEquals(0, withCalls)
    }

    @Test fun accountResultWins() = runBlocking {
        var anonCalls = 0
        val r = CredentialFallback.run(true, { "acc" }, { anonCalls++; "anon" })
        assertEquals("acc", r); assertEquals(0, anonCalls)
    }

    @Test fun nullWithAccountFallsBack() = runBlocking {
        assertEquals("anon", CredentialFallback.run<String>(true, { null }, { "anon" }))
    }

    @Test fun errorWithAccountFallsBack() = runBlocking {
        assertEquals("anon", CredentialFallback.run<String>(true, { error("purl 为空") }, { "anon" }))
    }

    @Test fun bothEmptyIsNull() = runBlocking {
        assertNull(CredentialFallback.run<String>(true, { null }, { null }))
    }
}
