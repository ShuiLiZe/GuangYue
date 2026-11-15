package com.guangyue.feature.library.viewmodel

import com.guangyue.core.data.account.AccountPlaylist
import com.guangyue.core.data.account.AccountSyncOutcome
import com.guangyue.feature.library.design.StatusToastType
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind

/**
 * 登录账号的歌单在首页「歌单」页签里的卡（Folia 手机版首页歌单页签 = 在线平台账号的歌单，我喜欢置顶）。
 * key 形如 `acct:qq:<歌单 id>`，点进去走集合层（AccountLibrary.qqTracks）。
 */
object AccountTiles {
    const val QQ_PREFIX = "acct:qq:"

    fun isAccount(key: String): Boolean = key.startsWith(QQ_PREFIX)

    fun qqIdOf(key: String): String? = key.takeIf { it.startsWith(QQ_PREFIX) }?.removePrefix(QQ_PREFIX)

    /** 卡片副标题照 Folia「N 首歌曲」；不知道曲目数时写平台名。 */
    fun tile(p: AccountPlaylist): HomeTile = HomeTile(
        key = QQ_PREFIX + p.id,
        title = p.name,
        subtitle = p.trackCount?.let { "$it 首歌曲" } ?: "QQ音乐",
        artwork = p.cover,
        kind = HomeTileKind.SHEET,
    )

    fun tiles(list: List<AccountPlaylist>): List<HomeTile> = list.map(::tile)

    /** 同步结果的提示（Folia zh-CN status.dataSynced / loginExpired / syncFailed）。 */
    fun toast(outcome: AccountSyncOutcome): Pair<String, StatusToastType> = when (outcome) {
        AccountSyncOutcome.SYNCED -> "数据同步成功" to StatusToastType.SUCCESS
        AccountSyncOutcome.EXPIRED, AccountSyncOutcome.NOT_SIGNED_IN -> "登录状态已失效，请重新登录" to StatusToastType.ERROR
        AccountSyncOutcome.FAILED -> "同步失败" to StatusToastType.ERROR
    }
}
