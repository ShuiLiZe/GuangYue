package com.guangyue.core.plugin

/**
 * 带账号取不到时回落到不带账号（GYACCOUNT 日志）：没存账号就只取一次；带账号拿到非 null 就用它；
 * 带账号返回 null 或抛错时再不带账号取一次，结果以它为准（两次都失败时抛的是不带账号那次的错）。
 */
object CredentialFallback {
    suspend fun <T : Any> run(
        hasAccount: Boolean,
        withAccount: suspend () -> T?,
        anonymous: suspend () -> T?,
        log: (String) -> Unit = {},
    ): T? {
        if (!hasAccount) return anonymous()
        val first = try { withAccount() } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            log("with account failed: ${e.message}"); null
        }
        if (first != null) return first
        log("with account empty, retry without account")
        return anonymous().also { log(if (it != null) "anonymous ok" else "anonymous empty") }
    }
}
