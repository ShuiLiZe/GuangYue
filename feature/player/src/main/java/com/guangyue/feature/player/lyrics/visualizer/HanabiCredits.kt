// 花火片尾卡：逐函数对译 Folia hanabi/credits.ts（resolveHanabiCreditsFrame、titleLine、烟花 / 升空 / 曲名 / 艺人与专辑的编排）
// + hanabiCreditsLayer.ts（最后一句唱完前 4 s 预建、以画面中心为枢轴）。
// 最后一句唱完：一发升空尾迹从底边升起，1.1 s 在曲名上方开金冠（kamuro 150 星），两侧千輪，2.2 s 后再一朵柳环；
// 曲名当一行「歌词」逐字弹出（同一个文字引擎，色带 band / pop / color），艺人（大写、字距 0.3）在下、专辑在上慢慢显出。
// PV 拼贴见 hbCreditsCollage（HanabiCollage.kt，R7⑪），歌词退场模糊 lyricBlur 由舞台 HbBlurSlots 套。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import com.guangyue.core.lyrics.Word
import kotlin.math.max
import kotlin.math.min

/** CreditsFrame（hanabiKernel.ts）。 */
internal class HbCreditsFrame(
    val active: Boolean,
    val lyricAlpha: Double,
    val lyricBlur: Double,
    val posterAlpha: Double,
    val posterOffsetY: Double,
    val posterScale: Double,
)

private val HB_CREDITS_INACTIVE = HbCreditsFrame(false, 1.0, 0.0, 0.0, 0.0, 1.0)

private fun hbCreditsSmooth(value: Double): Double {
    val t = min(1.0, max(0.0, value))
    return t * t * (3 - 2 * t)
}

/** 大烟花开花 / 曲名第一个字被唱到的时刻（片尾卡内的时间）。 */
internal const val HB_CREDITS_BLOOM_AT = 1.1
internal const val HB_CREDITS_TITLE_START = 1.45
private const val TITLE_STEP = 0.12
private const val TITLE_SPAN = 2.0
/** 最后一句唱完前多少秒开始预建（hanabiCreditsLayer PREBUILD_SECONDS）。 */
internal const val HB_CREDITS_PREBUILD = 4.0
/** 片尾卡烟花层的精灵上限（createFireworks maxParticles）。 */
internal const val HB_CREDITS_PARTICLES = 1400

internal fun hbCreditsClean(value: String?) = value?.trim() ?: ""

internal fun hasHanabiCredits(title: String?, artist: String?, album: String?) =
    hbCreditsClean(title).isNotEmpty() || hbCreditsClean(artist).isNotEmpty() || hbCreditsClean(album).isNotEmpty()

/** 歌词 1.2 秒内淡出；片尾卡 0.3 秒起 0.8 秒淡入。 */
internal fun resolveHanabiCreditsFrame(time: Double, finalEndTime: Double): HbCreditsFrame {
    val elapsed = time - finalEndTime
    if (elapsed <= 0) return HB_CREDITS_INACTIVE
    val exit = hbCreditsSmooth(elapsed / 1.2)
    return HbCreditsFrame(true, 1 - exit, exit * 6, hbCreditsSmooth((elapsed - 0.3) / 0.8), 0.0, 1.0)
}

/** hanabiCreditsLayer.resolveFrame：没有歌词结束时刻或没有曲目信息时恒为 inactive（歌词不淡出）。 */
internal fun resolveHanabiCreditsLayerFrame(time: Double, lyricEndTime: Double?, title: String?, artist: String?, album: String?): HbCreditsFrame =
    if (lyricEndTime == null || !hasHanabiCredits(title, artist, album)) HB_CREDITS_INACTIVE else resolveHanabiCreditsFrame(time, lyricEndTime)

private fun codePointChars(text: String): List<String> = text.codePoints().toArray().map { String(Character.toChars(it)) }

/** 曲名做成一行「歌词」：每个字依次被唱到。 */
internal fun hbCreditsTitleLine(title: String): Line {
    val chars = codePointChars(title)
    val step = min(TITLE_STEP, TITLE_SPAN / max(chars.size, 1))
    return Line(
        words = chars.mapIndexed { i, text -> Word(text, HB_CREDITS_TITLE_START + i * step, HB_CREDITS_TITLE_START + (i + 1) * step) },
        startTime = HB_CREDITS_TITLE_START,
        endTime = HB_CREDITS_TITLE_START + step * chars.size + 0.4,
        fullText = title,
    )
}

internal val HB_CREDITS_TITLE_SPEC = HbTextSpec("center", 0.11, 0.2, 0.0, "band", "pop", "color", "ascend", 0.05, false, true)

/** 艺人 / 专辑一行（Pixi Text：居中、字距 = 字号 × spacing、换行宽 0.7 屏宽）。 */
internal class HbCreditsDetail(val text: String, val y: Double, val size: Double, val spacing: Double, val delay: Double) {
    var alpha = 0.0
    var offsetY = 0.0
}

internal class HbCredits(
    val width: Double,
    val height: Double,
    val palette: HbPalette,
    val text: HbText?,
    val bursts: List<HbBurstEvent>,
    val launches: List<HbLaunchEvent>,
    val details: List<HbCreditsDetail>,
) {
    val unit = min(width, height)
    val plans = HashMap<HbBurstEvent, HbBurstPlan>()

    /** update(elapsed) 里艺人 / 专辑的淡入上浮；烟花与曲名由舞台按 max(0, elapsed) 画（drawText 自己 update）。 */
    fun update(elapsed: Double) {
        val time = max(0.0, elapsed)
        for (d in details) {
            val k = hbCreditsSmooth((time - d.delay) / 0.9)
            d.alpha = k * 0.92
            d.offsetY = (1 - k) * height * 0.02
        }
    }
}

/** createHanabiCredits（不含 Pixi 节点）：调色、烟花与升空事件、曲名文字、艺人 / 专辑。 */
internal fun createHanabiCredits(
    width: Double,
    height: Double,
    theme: HbTheme,
    title: String?,
    artist: String?,
    album: String?,
    tuning: HbTuning,
    fontScale: Double = 1.0,
    measure: (String) -> Double = ::hbEstimateAdvance,
    colorMode: String = "mixed",
): HbCredits {
    val palette = resolveHanabiPalette("kawaii", theme, colorMode)
    val t = hbCreditsClean(title)
    val a = hbCreditsClean(artist)
    val al = hbCreditsClean(album)
    val crown = hbBurstPreset("kamuro", listOf(0, 1, 3)).let { HbBurstSpec(it.kind, 150.0, 0.46, 4.2, it.colors, it.trail, it.twinkle) }
    val bursts = listOf(
        HbBurstEvent(width * 0.5, height * 0.34, HB_CREDITS_BLOOM_AT, crown, "hanabi:credits:crown", 1.0),
        HbBurstEvent(width * 0.2, height * 0.3, HB_CREDITS_BLOOM_AT + 0.55, hbBurstPreset("senrin", listOf(1, 2, 3)), "hanabi:credits:left", 0.8),
        HbBurstEvent(width * 0.8, height * 0.27, HB_CREDITS_BLOOM_AT + 0.8, hbBurstPreset("senrin", listOf(2, 3, 0)), "hanabi:credits:right", 0.8),
        HbBurstEvent(width * 0.5, height * 0.22, HB_CREDITS_BLOOM_AT + 2.2, hbBurstPreset("willow-ring", listOf(3, 0, 1)), "hanabi:credits:finale", 0.9),
    )
    val launches = bursts.mapIndexed { k, b ->
        HbLaunchEvent(b.x + (if (k % 2 == 0) -1 else 1) * width * 0.02, height * 1.03, b.x, b.y, b.at - 0.45, 0.45, palette.glow, "hanabi:credits:launch$k")
    }
    val text = if (t.isNotEmpty()) HbText(HbTextOptions(
        width, height, hbCreditsTitleLine(t), width * 0.72, height * 0.26,
        HbTextSpec(HB_CREDITS_TITLE_SPEC.layout, HB_CREDITS_TITLE_SPEC.heroSize * fontScale, HB_CREDITS_TITLE_SPEC.emphasis, HB_CREDITS_TITLE_SPEC.tilt,
            HB_CREDITS_TITLE_SPEC.block, HB_CREDITS_TITLE_SPEC.enter, HB_CREDITS_TITLE_SPEC.sing, HB_CREDITS_TITLE_SPEC.exit,
            HB_CREDITS_TITLE_SPEC.stagger, HB_CREDITS_TITLE_SPEC.outline, HB_CREDITS_TITLE_SPEC.hardShadow),
        palette, "hanabi:credits:title", HB_CREDITS_BLOOM_AT - 0.1, 1e6, 1.0,
        max(0.0, tuning.textMotion), min(1.0, max(0.0, tuning.blockOpacity)), measure,
    )) else null
    val detailSize = max(13.0, height * 0.03)
    val titleDone = HB_CREDITS_TITLE_START + min(TITLE_SPAN, TITLE_STEP * codePointChars(t).size)
    val base = if (t.isNotEmpty()) titleDone else 1.2
    val details = listOfNotNull(
        if (a.isNotEmpty()) HbCreditsDetail(a.uppercase(), 0.66, detailSize, 0.3, base) else null,
        if (al.isNotEmpty()) HbCreditsDetail(al, 0.35, detailSize * 0.8, 0.16, base + 0.3) else null,
    )
    return HbCredits(width, height, palette, text, bursts, launches, details).also { it.update(0.0) }
}
