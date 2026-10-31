package com.guangyue.core.plugin

import java.security.MessageDigest

/**
 * P1：插件**内容指纹**（sha256），用来发现「版本号没变、内容被原地改掉」的插件。
 *
 * 背景：上游 `hebijunge/musicfree-plugins` 在 2026-10-31 06:51–07:34 直接覆盖了 6 个常用插件的
 * .js（文件名里的版本号一字未改，例如 `qq-source.plugin.v1.9.26.js`），只比 `version` 的
 * 更新检测（[PluginManager.checkUpdates] / [PluginManager.checkUpdate]）必然整批漏更新。
 * 所以安装时把下载内容的 sha256 存进 [PluginMeta.contentHash]，之后拿远程内容的 sha256 比对：
 *  - 版本号变大 → 更新；
 *  - 版本号相同但 sha256 不同 → 也要更新（本文件解决的就是这一条）；
 *  - 版本号相同且 sha256 相同 → 已最新，跳过。
 *
 * 纯 JVM 实现（只用 MessageDigest），可在 JVM 单测里直接断言，无需 Context。
 */
object PluginContentHash {

    /** 下载内容的 sha256（小写 hex，长度 64）。[code] 按 UTF-8 取字节。 */
    fun of(code: String): String = of(code.toByteArray(Charsets.UTF_8))

    /** 字节级 sha256（小写 hex，长度 64）。 */
    fun of(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /**
     * 内容是否**变了**（需要重装）。
     *
     * [local] 为 null / 空白表示本地没有指纹（0.0.8 之前装的插件、或从本地文件装的）——
     * 这种情况无法证明内容一致，按「变了」处理，走一次重装把指纹补上；下一次就是正常比分。
     * [remote] 为空白（解析不出内容）时不做判断，返回 false，避免误更新。
     */
    fun changed(local: String?, remote: String): Boolean {
        val r = remote.trim().lowercase()
        if (r.isEmpty()) return false
        val l = local?.trim()?.lowercase().orEmpty()
        return l.isEmpty() || l != r
    }

    /** 内容指纹一致（版本号相同的前提下即「已最新」）。 */
    fun same(local: String?, remote: String): Boolean = !changed(local, remote)
}