package com.guangyue.feature.library.viewmodel

import com.guangyue.core.data.account.AccountPlaylist
import com.guangyue.core.data.account.AccountSyncOutcome
import com.guangyue.core.plugin.login.QrPlatform
import com.guangyue.feature.library.design.StatusToastType
import com.guangyue.feature.library.home.HomeTile
import com.guangyue.feature.library.home.HomeTileKind

/**
 * 登录账号的歌单在首页「歌单」页签里的卡（Folia 手机版首页歌单页签 = 在线平台账号的歌单，我喜欢置顶）。
 * key 形如 `acct:qq:<歌单 id>` / `acct:ne:<歌单 id>` / `acct:kg:<global_collection_id>`，点进去走集合层
 * （AccountLibrary.qqTracks / neteaseTracks / kugouTracks）。
 */
object AccountTiles {
    const val QQ_PREFIX = "acct:qq:"
    const val NE_PREFIX = "acct:ne:"
    const val KG_PREFIX = "acct:kg:"

    fun isAccount(key: String): Boolean = key.startsWith(QQ_PREFIX) || key.startsWith(NE_PREFIX) || key.startsWith(KG_PREFIX)

    fun qqIdOf(key: String): String? = key.takeIf { it.startsWith(QQ_PREFIX) }?.removePrefix(QQ_PREFIX)

    fun neteaseIdOf(key: String): String? = key.takeIf { it.startsWith(NE_PREFIX) }?.removePrefix(NE_PREFIX)

    fun kugouIdOf(key: String): String? = key.takeIf { it.startsWith(KG_PREFIX) }?.removePrefix(KG_PREFIX)

    private fun prefixOf(platform: QrPlatform) = when (platform) {
        QrPlatform.QQ -> QQ_PREFIX
        QrPlatform.NETEASE -> NE_PREFIX
        QrPlatform.KUGOU -> KG_PREFIX
    }

    /** 卡片副标题照 Folia「N 首歌曲」；不知道曲目数时写平台名。 */
    fun tile(p: AccountPlaylist, platform: QrPlatform = QrPlatform.QQ): HomeTile = HomeTile(
        key = prefixOf(platform) + p.id,
        title = p.name,
        subtitle = p.trackCount?.let { "$it 首歌曲" } ?: platform.label,
        artwork = p.cover,
        kind = HomeTileKind.SHEET,
    )

    fun tiles(list: List<AccountPlaylist>, platform: QrPlatform = QrPlatform.QQ): List<HomeTile> = list.map { tile(it, platform) }

    /**
     * 歌单页签显示哪些账号歌单。Folia 首页读的是「当前平台」的账号（omni withActiveProvider）：当前音源是 QQ / 网易云 / 酷狗
     * 且这个账号读到了歌单，就只列它的；当前音源不是这三家（洛雪源、别的插件）或这个平台没登录，就把已登录的都列上
     * （QQ、网易云、酷狗依次，各自我喜欢置顶），免得切到别的音源歌单页签就空了。
     */
    fun forHome(
        current: QrPlatform?,
        qq: List<AccountPlaylist>,
        netease: List<AccountPlaylist>,
        kugou: List<AccountPlaylist> = emptyList(),
    ): List<HomeTile> = when {
        current == QrPlatform.QQ && qq.isNotEmpty() -> tiles(qq, QrPlatform.QQ)
        current == QrPlatform.NETEASE && netease.isNotEmpty() -> tiles(netease, QrPlatform.NETEASE)
        current == QrPlatform.KUGOU && kugou.isNotEmpty() -> tiles(kugou, QrPlatform.KUGOU)
        else -> tiles(qq, QrPlatform.QQ) + tiles(netease, QrPlatform.NETEASE) + tiles(kugou, QrPlatform.KUGOU)
    }

    /** 同步结果的提示（Folia zh-CN status.dataSynced / loginExpired / syncFailed）。 */
    fun toast(outcome: AccountSyncOutcome): Pair<String, StatusToastType> = when (outcome) {
        AccountSyncOutcome.SYNCED -> "数据同步成功" to StatusToastType.SUCCESS
        AccountSyncOutcome.EXPIRED, AccountSyncOutcome.NOT_SIGNED_IN -> "登录状态已失效，请重新登录" to StatusToastType.ERROR
        AccountSyncOutcome.FAILED -> "同步失败" to StatusToastType.ERROR
    }
}
