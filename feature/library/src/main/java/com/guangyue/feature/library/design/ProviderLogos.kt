package com.guangyue.feature.library.design

import androidx.annotation.DrawableRes
import com.guangyue.feature.library.R

/**
 * 平台「应用本尊」图标（照 folia-sonnet `src/assets/providers/appIcons.ts` 的 PROVIDER_APP_ICONS）。
 *
 * Folia 手机版的平台记号：有本尊图标的平台亮图标（铺满、裁成圆，不套彩色圆底），没有才回落「一个字 + 平台色」。
 * 广乐的音源来自插件，没有固定的 providerId，这里按插件 platform / 名称 / 地址里的关键字归到平台。
 * 图标文件取自 folia-sonnet（AGPL-3.0），缩到 192px 放在 drawable-nodpi。
 */
object ProviderLogos {
    /** 关键字 → 图标；先匹配先得（「QQ」要排在别的之前也无冲突）。 */
    private val table: List<Pair<List<String>, Int>> = listOf(
        listOf("网易", "netease", "163", "wy") to R.drawable.provider_netease,
        listOf("qq", "腾讯", "tx") to R.drawable.provider_qq,
        listOf("酷狗", "kugou", "kg") to R.drawable.provider_kugou,
        listOf("波点", "bodian", "酷我", "kuwo", "kw") to R.drawable.provider_bodian,
        listOf("汽水", "qishui") to R.drawable.provider_qishui,
        listOf("飞牛", "feiniu", "fnmusic") to R.drawable.provider_feiniu,
        listOf("洛雪", "lxmusic", "lx music", "lx-music") to R.drawable.provider_lxmusic,
    )

    /** 两三个字母的短代号（wy/tx/kg/kw）只在整段就是它时才算，免得误中别的名字里的字母。 */
    private val shortCodes = setOf("wy", "tx", "kg", "kw")

    /** 给出任意几段描述（platform、名称、地址），返回第一段能认出的平台图标；都认不出返回 null。 */
    @DrawableRes
    fun resFor(vararg texts: String?): Int? {
        for (raw in texts) {
            val t = raw?.trim()?.lowercase().orEmpty()
            if (t.isEmpty()) continue
            for ((keys, res) in table) {
                if (keys.any { k -> if (k in shortCodes) t == k else k in t }) return res
            }
        }
        return null
    }
}
