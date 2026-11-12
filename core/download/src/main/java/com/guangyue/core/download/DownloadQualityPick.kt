package com.guangyue.core.download

import com.guangyue.core.player.Quality
import com.guangyue.core.player.QualityLadder

/**
 * 2026-11-12：下载键「点一下直接下、长按选音质」的纯逻辑（JVM 单测覆盖）。
 *
 * - 点一下：照旧按「下载设置 · 下载音质」[DownloadConfig.quality] 取链（[DownloadManager.enqueue]）。
 * - 长按：弹层列出这首歌的音源真正给得出的档位，选哪档就按哪档下（取不到再往下降，同 [DownloadPolicy.qualityLadder]）。
 *
 * 档位来源按优先级：播放时插件回报的档位 → 插件 meta 声明的 `supportedQualities` →
 * 都没有时按 MusicFree 约定四档（low / standard / high / super = 标准 / 高品 / 很高 / 无损），
 * 同 Folia：没列档位的源只分得清这几档，多列只会给用户一个选了也没用的假档位。
 */
object DownloadQualityPick {

    /** 弹层标题与说明（Folia 口吻：短句、不加感叹号）。 */
    const val TITLE = "选择下载音质"
    const val HINT = "点一下下载键按默认音质，长按可以另选"
    const val DEFAULT_TAG = "默认"

    /** 弹层列出的档位：从高到低、去重；[declared] 为空就用 MusicFree 四档。 */
    fun options(vararg declared: List<String>?): List<Quality> {
        val raw = declared.firstOrNull { !it.isNullOrEmpty() && QualityLadder.of(it).isNotEmpty() }
            ?: DownloadPolicy.MUSICFREE_LEVELS
        return QualityLadder.of(raw)
    }

    /**
     * 弹层里标「默认」的那一档 = 点一下下载时实际会先试的档位：
     * 设置里的 [preferred] 在 [options] 里就是它，不在就往下取最近的一档；都比它高时取最低档。
     */
    fun defaultOf(options: List<Quality>, preferred: Quality): Quality? {
        if (options.isEmpty()) return null
        return options.filter { it.order <= preferred.order }.maxByOrNull { it.order }
            ?: options.minByOrNull { it.order }
    }

    /** 一行的主标题与副标题：主标题用档位名（标准 / 高品 / 很高 / 无损…），副标题给码率或格式。 */
    fun label(q: Quality): String = q.label

    fun subtitle(q: Quality): String = q.subtitle

    /** 选完后的 Toast。 */
    fun addedToast(q: Quality): String = "已加入下载（${q.label}），进度在「我的 → 下载」"

    /**
     * 换了音质，播放时取到的直链就不能再用（那是另一档的地址），只留本地文件；同一档才沿用。
     */
    fun directUrlFor(chosen: Quality, playing: Quality, directUrl: String?): String? {
        val url = directUrl ?: return null
        val local = url.startsWith("file:") || url.startsWith("content:") || url.startsWith("/")
        return if (local || chosen == playing) url else null
    }
}
