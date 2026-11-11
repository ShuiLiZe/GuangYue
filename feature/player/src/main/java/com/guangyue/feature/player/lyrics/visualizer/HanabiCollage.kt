// 花火 PV 拼贴（R7⑩）：逐函数对译 Folia hanabi/collage/collagePlan.ts（编排，纯数据）+ collageViews.ts（每件每帧的姿态）
// + unitCollage.ts（整单元的网点底纹与左缘竖排胶片跑马灯）。纹理（collagePainters.ts）在 HanabiCollagePaint.kt，绘制在 HanabiStage.kt。
// 日系动漫 PV 的手帐感：斜跨画面的装饰带（电影胶片 / 剪切线 / 蕾丝花边）、重复的空心字、贴纸、胶带、格子纸片、小窗口、编号标签、
// 开花时蹦出的图标贴纸、跟着尾迹升空的小图标、间奏的贴纸气球。坐标都在镜头容器里（与文字视图的本地坐标一致）。
package com.guangyue.feature.player.lyrics.visualizer

import com.guangyue.core.lyrics.Line
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/** 日系可爱 PV 的糖果色（CANDY）。 */
internal val HB_CANDY = intArrayOf(0xffb3d1, 0xb8f2e6, 0xfff1a8, 0xd7c4ff, 0xffc9a8, 0xa8dcff)
internal const val HB_CANDY_INK = 0x2b1f3d
internal const val HB_DEEP_CARD = 0x1d1838
internal const val HB_FALLBACK_ICON = "Flower"
internal val HB_ICON_INKS = intArrayOf(0xff5fa2, 0x3fb2ff, 0xffa31a, 0x9b6bff, 0x2ec9a0)
private val BUBBLE_LABELS = listOf("♡", "!!", "♪", "LOVE", "yay!", "(≧▽≦)", "☆彡")
private val WINDOW_TITLES = listOf("♡ hanabi.exe", "♪ lyric.txt", "✦ night_sky.png", "☆ summer.mp3", "♡ kirakira.app")
private val SHAPES = listOf("heart", "star", "sparkle", "flower", "cloud", "ribbon", "cat")
internal val HB_STRIP_STYLES = listOf("film", "cut", "lace")
private fun stripThickness(style: String) = when (style) { "film" -> 0.075; "cut" -> 0.045; else -> 0.06 }

private val FAMILY_RECIPES = mapOf(
    "uchiage" to listOf("strips", "scrapbook", "stickerbomb"),
    "kiku" to listOf("echo", "strips", "window"),
    "botan" to listOf("stickerbomb", "scrapbook", "window"),
    "yanagi" to listOf("quiet", "echo"),
    "senrin" to listOf("stickerbomb", "strips", "scrapbook"),
    "hoshikuzu" to listOf("window", "quiet", "stickerbomb"),
    "obi" to listOf("strips", "scrapbook", "echo"),
    "senko" to listOf("window", "echo", "strips"),
    "senkohanabi" to listOf("quiet", "scrapbook"),
    "shakudama" to listOf("echo", "strips", "stickerbomb"),
)

private fun <T> pick(list: List<T>, random: () -> Double): T = list[floor(random() * list.size).toInt() % list.size]
private fun clampD(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

/** 调色板颜色的粉彩版：往白里混，保持色相。 */
internal fun hbPastel(color: Int, amount: Double = 0.45) = hbMixColor(color, 0xffffff, amount)

/**
 * 一个拼贴件（Folia 的 CollageItem 联合类型摊平成一个类；没用到的字段留默认）。
 * kind：strip / echo / sticker / tape / patch / window / label / headline / pop / whoosh / floater。
 */
internal class HbCollageItem(
    val kind: String,
    var x: Double = 0.0,
    var y: Double = 0.0,
    var at: Double = 0.0,
    var exitAt: Double = 0.0,
    var exitDuration: Double = 0.0,
    val style: String = "",
    val length: Double = 0.0,
    val thickness: Double = 0.0,
    val rotation: Double = 0.0,
    val color: Int = 0,
    val speed: Double = 0.0,
    val from: Int = 1,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val text: String = "",
    val size: Double = 0.0,
    val axis: String = "x",
    val alpha: Double = 1.0,
    val shape: String = "",
    val label: String? = null,
    val layer: String = "front",
    val pattern: String = "",
    val dark: Boolean = false,
    val bar: Double = 0.0,
    val title: String = "",
    val icon: String = "",
    val duration: Double = 0.0,
    val fromX: Double = 0.0,
    val fromY: Double = 0.0,
    val toX: Double = 0.0,
    val toY: Double = 0.0,
    val rise: Double = 0.0,
    val sway: Double = 0.0,
) {
    fun copyTimed(at: Double, exitAt: Double, exitDuration: Double) = HbCollageItem(kind, x, y, at, exitAt, exitDuration, style, length, thickness, rotation,
        color, speed, from, width, height, text, size, axis, alpha, shape, label, layer, pattern, dark, bar, title, icon, duration, fromX, fromY, toX, toY, rise, sway)

    /** 这一件最后可见到什么时刻（scene.ts 的 collageEnd）。 */
    val end: Double get() = when (kind) {
        "pop", "floater" -> at + duration
        "whoosh" -> at + duration + 0.55
        else -> exitAt + exitDuration
    }
}

internal class HbCollageRect(val x: Double, val y: Double, val width: Double, val height: Double)
internal class HbCollageBurst(val x: Double, val y: Double, val at: Double, val kind: String, val main: Boolean, val radius: Double)

internal class HbCollagePlanInput(
    val seed: String,
    val family: String,
    val isBridge: Boolean,
    val block: String,
    val shotNumber: Int,
    val bounds: HbCollageRect,
    val screen: HbCollageRect,
    val screenAt: ((Double) -> HbCollageRect)?,
    val unit: Double,
    val palette: HbPalette,
    val enterAt: Double,
    val exitAt: Double,
    val exitDuration: Double,
    val start: Double,
    val end: Double,
    val keyword: String?,
    val motifs: List<String>,
    val bursts: List<HbCollageBurst>,
    val launches: List<HbLaunchEvent>,
    val amount: Double,
    val icons: List<String>?,
    val previous: String?,
    val openingTitle: String? = null,
    val openingArtist: String? = null,
    /** Folia 逻辑像素（CSS px）→ 本画布像素的倍率：只乘在几个绝对下限（12 / 14 / 16 / 18 px）上；金标对拍时为 1。 */
    val px: Double = 1.0,
)

internal class HbCollagePlan(val recipe: String?, val items: List<HbCollageItem>)

private fun cpLen(s: String) = s.codePointCount(0, s.length)

private val LATIN = Regex("^[\\p{IsLatin}\\p{N}'’\\- ]+$")

/**
 * pickCollageKeyword：很短的句子（2–5 个字）整句；否则取最长的词（2–10 个字），拉丁字母转大写。
 * split = 分词（segmentLyricWords，Android 上 BreakIterator 由 ICU 实现，与 Intl.Segmenter 同源），返回（片段，是否像词）。
 */
internal fun hbPickCollageKeyword(line: Line?, split: (Line) -> List<Pair<String, Boolean>>): String? {
    if (line == null) return null
    val text = line.fullText.trim()
    val graphemes = com.guangyue.core.lyrics.GraphemeTimings.splitLyricGraphemes(text).filter { it.trim().isNotEmpty() }
    fun shout(word: String) = if (LATIN.matches(word)) word.uppercase() else word
    if (graphemes.size in 2..5) return shout(text)
    var best: String? = null
    var bestLength = 0
    for ((segment, wordLike) in split(line)) {
        val word = segment.trim()
        val length = com.guangyue.core.lyrics.GraphemeTimings.splitLyricGraphemes(word).size
        if (!wordLike || length < 2 || length > 10) continue
        if (length > bestLength) { best = word; bestLength = length }
    }
    return best?.let { shout(it) }
}

/** chooseCollageRecipe：族偏好里避开上一个镜头的配方；间奏固定 bridge。 */
internal fun hbChooseCollageRecipe(family: String, isBridge: Boolean, previous: String?, random: () -> Double): String {
    if (isBridge) return "bridge"
    val preferred = FAMILY_RECIPES[family] ?: listOf("scrapbook", "strips", "stickerbomb")
    val choices = preferred.filter { it != previous }
    val list = if (choices.isNotEmpty()) choices else preferred
    val total = (list.size * (list.size + 1)) / 2.0
    var roll = random() * total
    for (index in list.indices) {
        roll -= (list.size - index)
        if (roll < 0) return list[index]
    }
    return list[0]
}

private fun countOf(base: Double, amount: Double, min: Int = 1) = if (amount <= 0) 0 else max(min, jsRound(base * amount))
private fun jsRound(v: Double) = floor(v + 0.5).toInt()

/** planShotCollage：一个镜头的拼贴。确定性：同样的输入（含 seed）永远同样的件。 */
internal fun hbPlanShotCollage(input: HbCollagePlanInput): HbCollagePlan {
    if (input.amount <= 0) return HbCollagePlan(null, emptyList())
    val rng = lumiereRng("${input.seed}:collage")
    val random = { rng.next() }
    val opening = input.openingTitle
    val recipe = if (input.isBridge && opening != null) "opening" else hbChooseCollageRecipe(input.family, input.isBridge, input.previous, random)
    val bounds = input.bounds; val screen = input.screen; val unit = input.unit; val amount = input.amount
    val items = ArrayList<HbCollageItem>()
    val candy = HB_CANDY.toList() + listOf(hbPastel(input.palette.accent), hbPastel(input.palette.sparks.getOrElse(1) { input.palette.accent }))
    fun candyColor() = pick(candy, random)
    val railGap = unit * 0.14
    val cx = bounds.x + bounds.width / 2
    val cy = bounds.y + bounds.height / 2
    val sx = screen.x + screen.width / 2
    fun clampX(x: Double, pad: Double) = clampD(x, screen.x + railGap * 0.5 + pad, screen.x + screen.width - pad)
    fun clampY(y: Double, pad: Double) = clampD(y, screen.y + pad, screen.y + screen.height - pad)
    fun signed() = if (random() < 0.5) -1 else 1
    val motifs = if (input.motifs.isNotEmpty()) input.motifs else listOf("HANABI", "花火")
    fun motif() = pick(motifs, random)
    val exitAt = input.exitAt; val exitDuration = input.exitDuration

    fun sticker(shape: String, x: Double, y: Double, size: Double, delay: Double, layer: String = "front", label: String? = null): HbCollageItem {
        val px = clampX(x, size * 0.6)
        val py = clampY(y, size * 0.6)
        val rotation = (random() - 0.5) * 0.7
        val color = candyColor()
        return HbCollageItem("sticker", x = px, y = py, size = size, rotation = rotation, color = color, shape = shape, label = label, layer = layer,
            at = input.enterAt + delay, exitAt = exitAt, exitDuration = exitDuration)
    }

    fun corner(index: Int, out: Double): Pair<Double, Double> {
        val dx = if (index == 0 || index == 3) -1 else 1
        val dy = if (index < 2) -1 else 1
        return ((if (dx < 0) bounds.x else bounds.x + bounds.width) + dx * out) to ((if (dy < 0) bounds.y else bounds.y + bounds.height) + dy * out * 0.6)
    }

    fun strip(style: String, side: Int, delay: Double): HbCollageItem {
        val thickness = max((if (style == "cut") 14.0 else 18.0) * input.px, unit * stripThickness(style))
        val gap = unit * 0.05 + thickness / 2
        var y = if (side < 0) bounds.y - gap else bounds.y + bounds.height + gap
        y = clampY(y, thickness)
        val rotation = side * (0.06 + random() * 0.1)
        val color = candyColor()
        val s1 = signed()
        val speed = s1 * unit * (if (style == "film") 0.12 else 0.05) * (0.8 + random() * 0.4)
        val from = signed()
        return HbCollageItem("strip", style = style, x = sx, y = y, length = hypot(screen.width, screen.height) * 1.25, thickness = thickness,
            rotation = rotation, color = color, speed = speed, from = from, at = input.enterAt + delay, exitAt = exitAt, exitDuration = exitDuration)
    }

    fun echo(axis: String, side: Int, alpha: Double): HbCollageItem? {
        val text = input.keyword ?: motif()
        if (text.trim().isEmpty()) return null
        val size = unit * (if (axis == "x") 0.13 else 0.11)
        if (axis == "x") {
            val y = if (side < 0) bounds.y - size * 0.75 else bounds.y + bounds.height + size * 0.75
            val rotation = (random() - 0.5) * 0.08
            val color = if (random() < 0.5) input.palette.paper else candyColor()
            return HbCollageItem("echo", axis = axis, text = text, size = size, alpha = alpha, x = sx, y = clampY(y, size * 0.5),
                width = screen.width * 1.3, height = size * 1.25, rotation = rotation, color = color, speed = side * unit * 0.07,
                at = input.enterAt - 0.15, exitAt = exitAt, exitDuration = exitDuration)
        }
        val width = size * max(1, cpLen(text)) * 0.9 + size * 0.4
        val free = (screen.width - bounds.width) / 2
        val x = if (side < 0) bounds.x - width * 0.55 else bounds.x + bounds.width + width * 0.55
        val raw = if (free > width * 0.6) x else (if (side < 0) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY)
        val color = if (random() < 0.5) input.palette.paper else candyColor()
        return HbCollageItem("echo", axis = axis, text = text, size = size, alpha = alpha,
            x = clampD(raw, screen.x + railGap + width * 0.5, screen.x + screen.width - unit * 0.06 - width * 0.5), y = cy,
            width = width, height = screen.height * 1.3, rotation = 0.0, color = color, speed = side * unit * 0.06,
            at = input.enterAt - 0.15, exitAt = exitAt, exitDuration = exitDuration)
    }

    fun patch(pad: Double, offset: Double, pattern: String, delay: Double): HbCollageItem {
        val dark = input.block == "none" || input.block == "outline" || input.block == "marker"
        val x = cx + offset * signed()
        val y = cy + offset * signed() * 0.6
        val rotation = (random() - 0.5) * 0.09
        val color = if (dark) HB_DEEP_CARD else candyColor()
        return HbCollageItem("patch", x = x, y = y, width = bounds.width + pad * 2, height = bounds.height + pad * 2, rotation = rotation, color = color,
            pattern = pattern, dark = dark, at = input.enterAt + delay, exitAt = exitAt, exitDuration = exitDuration)
    }

    fun tape(index: Int, delay: Double, around: HbCollageRect): HbCollageItem {
        val length = unit * (0.12 + random() * 0.04)
        val x = if (index == 0 || index == 3) around.x else around.x + around.width
        val y = if (index < 2) around.y else around.y + around.height
        val diagonal = if (index == 0 || index == 2) -1 else 1
        val rotation = diagonal * (0.62 + random() * 0.2)
        val color = candyColor()
        val pattern = pick(listOf("stripe", "dot", "check"), random)
        return HbCollageItem("tape", x = x, y = y, length = length, width = length * 0.3, rotation = rotation, color = color, pattern = pattern,
            at = input.enterAt + delay, exitAt = exitAt, exitDuration = exitDuration)
    }

    fun label(delay: Double): HbCollageItem {
        val size = max(12.0 * input.px, unit * 0.026)
        val n = (input.shotNumber + 1).toString().padStart(2, '0')
        val text = if (random() < 0.5) "No.$n" else "#$n ${pick(listOf("♪", "♡", "✦"), random)}"
        val (ax, ay) = corner(3, size * 0.4)
        val color = candyColor()
        val rotation = (random() - 0.5) * 0.2
        return HbCollageItem("label", x = clampX(ax + size * 2.2, size * 3), y = clampY(ay + size * 0.9, size), text = text, color = color, size = size,
            rotation = rotation, at = input.enterAt + delay, exitAt = exitAt, exitDuration = exitDuration)
    }

    fun stickerSize() = unit * (0.065 + random() * 0.04)

    when (recipe) {
        "scrapbook" -> {
            val pad = unit * 0.045
            val card = patch(pad, unit * 0.012, pick(listOf("grid", "dots", "gingham"), random), -0.12)
            items += patch(pad * 0.6, unit * 0.04, "plain", -0.2)
            items += card
            val around = HbCollageRect(card.x - card.width / 2, card.y - card.height / 2, card.width, card.height)
            val first = floor(random() * 2).toInt()
            items += tape(first, 0.05, around)
            items += tape(first + 2, 0.12, around)
            for (k in 0 until countOf(1.5, amount)) {
                val (ax, ay) = corner((first + 1 + k * 2) % 4, unit * 0.03)
                val shape = pick(SHAPES, random)
                items += sticker(shape, ax, ay, stickerSize(), 0.25 + k * 0.1)
            }
            items += label(0.3)
        }
        "strips" -> {
            val side = if (random() < 0.5) -1 else 1
            val first = pick(HB_STRIP_STYLES, random)
            items += strip(first, side, -0.1)
            if (amount >= 0.75) {
                val second = pick(HB_STRIP_STYLES.filter { it != first }, random)
                items += strip(second, if (side == -1) 1 else -1, 0.05)
            }
            for (k in 0 until countOf(2.0, amount)) {
                val (ax, ay) = corner((k * 2 + (if (side < 0) 1 else 0)) % 4, unit * 0.04)
                items += sticker(if (k % 2 == 0) "sparkle" else "star", ax, ay, stickerSize() * 0.85, 0.2 + k * 0.12)
            }
        }
        "echo" -> {
            val vertical = screen.width - bounds.width > unit * 0.5
            val first = echo(if (vertical) "y" else "x", -1, 0.3)
            val second = echo(if (vertical) "y" else "x", 1, 0.3)
            if (first != null) items += first
            if (second != null && amount >= 0.75) items += second
            val (ax, ay) = corner(1, unit * 0.04)
            val size = stickerSize() * 1.6
            items += sticker("bubble", ax, ay, size, 0.3, "front", pick(BUBBLE_LABELS, random))
        }
        "window" -> {
            val pad = unit * 0.05
            val bar = max(16.0 * input.px, unit * 0.04)
            val width = bounds.width + pad * 2
            val height = bounds.height + pad * 2 + bar
            val rotation = (random() - 0.5) * 0.05
            val title = pick(WINDOW_TITLES, random)
            items += HbCollageItem("window", x = cx, y = cy - bar / 2, width = width, height = height, rotation = rotation, bar = bar, title = title,
                at = input.enterAt - 0.18, exitAt = exitAt, exitDuration = exitDuration)
            for (k in 0 until countOf(2.0, amount)) {
                val (ax, ay) = corner(if (k == 0) 0 else 2, unit * 0.035)
                val shape = pick(SHAPES, random)
                items += sticker(shape, ax, ay, stickerSize(), 0.25 + k * 0.12)
            }
        }
        "stickerbomb" -> {
            val count = countOf(4.0, amount, 2)
            for (k in 0 until count) {
                val (ax, ay) = corner(k % 4, unit * (0.03 + 0.03 * floor(k / 4.0)))
                val jitter = unit * 0.03
                val shape = pick(SHAPES, random)
                val jx = ax + (random() - 0.5) * jitter
                val jy = ay + (random() - 0.5) * jitter
                items += sticker(shape, jx, jy, stickerSize(), 0.15 + k * 0.08)
            }
            val (ax, ay) = corner(floor(random() * 4).toInt(), unit * 0.09)
            val size = stickerSize() * 1.5
            items += sticker("bubble", ax, ay, size, 0.4, "front", pick(BUBBLE_LABELS, random))
        }
        "quiet" -> {
            val row = echo("x", if (random() < 0.5) -1 else 1, 0.16)
            if (row != null) items += row
            val (ax, ay) = corner(floor(random() * 4).toInt(), unit * 0.03)
            items += sticker("sparkle", ax, ay, stickerSize() * 0.7, 0.35)
        }
        "opening" -> {
            val title = opening!!
            val artist = input.openingArtist
            val leaveAt = max(input.start + 1.2, input.end - 0.7); val leaveDur = 0.5
            val titleSize = min(unit * 0.11, (screen.width * 0.5) / max(4.0, cpLen(title) * 0.62))
            val width = min(screen.width * 0.72, max(unit * 0.6, titleSize * cpLen(title) * 0.7 + unit * 0.2))
            val bar = max(16.0 * input.px, unit * 0.045)
            val height = titleSize * 1.9 + bar + (if (artist != null) unit * 0.06 else 0.0)
            val y = screen.y + screen.height * 0.42
            val at = input.start + 0.25
            items += HbCollageItem("window", x = sx, y = y, width = width, height = height, rotation = -0.02, bar = bar, title = "♪ now playing",
                at = at, exitAt = leaveAt, exitDuration = leaveDur)
            items += HbCollageItem("headline", x = sx, y = y + bar / 2 - (if (artist != null) unit * 0.02 else 0.0), text = title, color = candyColor(),
                size = titleSize, rotation = -0.03, at = at + 0.18, exitAt = leaveAt, exitDuration = leaveDur)
            if (artist != null) {
                items += HbCollageItem("label", x = sx, y = y + height / 2 - unit * 0.045, text = artist, color = candyColor(), size = max(12.0 * input.px, unit * 0.028),
                    rotation = 0.02, at = at + 0.4, exitAt = leaveAt, exitDuration = leaveDur)
            }
            val cardX = sx - width / 2; val cardY = y - height / 2
            for (k in 0..3) {
                val x = if (k == 0 || k == 3) cardX else cardX + width
                val cornerY = if (k < 2) cardY else cardY + height
                val shape = pick(listOf("heart", "star", "ribbon", "sparkle"), random)
                items += sticker(shape, x, cornerY, unit * 0.075, 0.0).copyTimed(at + 0.3 + k * 0.08, leaveAt, leaveDur)
            }
            val floaters = countOf(5.0, amount, 2)
            for (k in 0 until floaters) {
                val shape = pick(listOf("heart", "star", "sparkle"), random)
                val x = screen.x + screen.width * (0.1 + random() * 0.8)
                val rise = screen.height * (1.25 + random() * 0.3)
                val size = unit * (0.045 + random() * 0.04)
                val color = candyColor()
                val fat = input.start + 0.6 + (k.toDouble() / floaters) * max(0.5, input.end - input.start - 1.5)
                val duration = 3.4 + random() * 1.4
                val sway = unit * (0.02 + random() * 0.03)
                items += HbCollageItem("floater", shape = shape, x = x, y = screen.y + screen.height * 1.08, rise = rise, size = size, color = color,
                    at = fat, duration = duration, sway = sway)
            }
        }
        "bridge" -> {
            val film = strip("film", 1, 0.0)
            film.y = screen.y + screen.height * (0.62 + random() * 0.12)
            film.at = input.start + 0.1
            items += film
            val floaters = countOf(7.0, amount, 3)
            val span = max(0.5, input.end - input.start)
            for (k in 0 until floaters) {
                val at = input.start + (k.toDouble() / floaters) * span * 0.8 + random() * 0.3
                val shape = pick(listOf("heart", "star", "sparkle", "flower"), random)
                val x = screen.x + screen.width * (0.08 + random() * 0.84)
                val rise = screen.height * (1.25 + random() * 0.3)
                val size = unit * (0.05 + random() * 0.05)
                val color = candyColor()
                val duration = 3.2 + random() * 1.6
                val sway = unit * (0.02 + random() * 0.03)
                items += HbCollageItem("floater", shape = shape, x = x, y = screen.y + screen.height * 1.08, rise = rise, size = size, color = color,
                    at = at, duration = duration, sway = sway)
            }
        }
    }

    // 图标贴纸：主烟花开花的一瞬（间奏的连发隔一发一个，最多 5 个）。
    val icons = if (!input.icons.isNullOrEmpty()) input.icons else listOf(HB_FALLBACK_ICON)
    var iconTurn = floor(random() * icons.size).toInt()
    fun nextIcon() = icons[iconTurn++ % icons.size]
    val mains = input.bursts.filter { it.main }
    val popped = if (recipe == "opening") emptyList() else if (input.isBridge) mains.filterIndexed { i, _ -> i % 2 == 0 }.take(5) else mains.take(1)
    for (burst in popped) {
        if (random() > min(1.0, 0.55 + 0.35 * amount)) continue
        val size = unit * (if (input.isBridge) 0.1 else 0.115) * (0.9 + random() * 0.25)
        val view = input.screenAt?.invoke(burst.at + 0.35) ?: screen
        val halfWidth = size * 0.6
        val left = view.x + railGap * 0.5 + halfWidth
        val right = view.x + view.width - halfWidth - size * 0.3
        val top = view.y + size * 0.8
        val bottom = view.y + view.height * 0.8
        val preferRight = if (input.isBridge) burst.x > sx else burst.x >= cx
        fun beside(dir: Int) = (if (dir > 0) bounds.x + bounds.width + halfWidth + size * 0.3 else bounds.x - halfWidth - size * 0.3) to (bounds.y + size * 0.2)
        val candidates = if (input.isBridge) listOf((burst.x + (if (preferRight) 1 else -1) * burst.radius * 0.45) to (burst.y - burst.radius * 0.35))
        else listOf(beside(if (preferRight) 1 else -1), beside(if (preferRight) -1 else 1), (cx + (random() - 0.5) * bounds.width * 0.3) to (bounds.y - size * 0.75))
        fun fits(p: Pair<Double, Double>) = p.first >= left && p.first <= right && p.second >= top && p.second <= bottom
        val spot = candidates.firstOrNull { fits(it) } ?: candidates[0]
        val icon = nextIcon()
        val color = pick(HB_ICON_INKS.toList(), random)
        val rotation = (if (preferRight) 1 else -1) * (0.08 + random() * 0.18)
        items += HbCollageItem("pop", icon = icon, x = clampD(spot.first, left, max(left, right)), y = clampD(spot.second, top, max(top, bottom)),
            color = color, size = size, rotation = rotation, at = burst.at + 0.04, duration = 1.3)
    }
    for (launch in input.launches.take(2)) {
        if (random() > 0.45 + 0.25 * amount) continue
        val icon = nextIcon()
        val color = pick(HB_ICON_INKS.toList(), random)
        items += HbCollageItem("whoosh", icon = icon, fromX = launch.fromX, fromY = launch.fromY, toX = launch.toX, toY = launch.toY,
            at = launch.at, duration = launch.duration, color = color, size = max(18.0 * input.px, unit * 0.055))
    }
    return HbCollagePlan(recipe, items)
}

// ───────────────────────────── collageViews.ts：每件每帧的姿态 ─────────────────────────────

private fun c01(v: Double) = hbClamp01(v)
private fun easeOutCubicC(t: Double) = 1 - (1 - c01(t)).pow(3)
private fun easeInCubicC(t: Double) = c01(t).pow(3)
private fun easeOutBackC(t: Double, o: Double = 1.70158): Double { val x = c01(t) - 1; return 1 + x * x * ((o + 1) * x + o) }
private fun popIn(time: Double, at: Double, duration: Double = 0.32) = easeOutBackC((time - at) / duration, 2.4)
private fun leave(time: Double, exitAt: Double, exitDuration: Double) = c01((time - exitAt) / max(exitDuration, 0.05))
/** 贴纸纹理基准边长（STICKER_PX）。 */
internal const val HB_STICKER_PX = 112.0
private fun jsMod(a: Double, b: Double) = a - b * kotlin.math.truncate(a / b)

/**
 * 一件这一帧的姿态。x/y 是锚点（中心）位置、sx/sy 缩放、rotation 弧度、alpha。
 * strip：bandX0/bandWidth = 带子可见段的左缘与宽（带子本地坐标，中心在 0）、tileX = 平铺偏移；shadow 跟带子；bladeX / bladeVisible / bladeSy / bladeAlpha 是剪刀。
 * echo：tileX / tileY 平铺偏移。
 */
internal class HbCollagePose {
    var visible = false
    var x = 0.0; var y = 0.0; var sx = 1.0; var sy = 1.0; var rotation = 0.0; var alpha = 1.0
    var bandX0 = 0.0; var bandWidth = 0.0; var tileX = 0.0; var tileY = 0.0
    var bladeVisible = false; var bladeX = 0.0; var bladeSx = 1.0; var bladeSy = 1.0; var bladeAlpha = 1.0
}

/** collageViews.ts 每件的 update(time)；tileWidth = 平铺纹理的宽（带子用）。 */
internal fun hbCollagePose(item: HbCollageItem, time: Double, out: HbCollagePose, tileWidth: Double = 1.0): HbCollagePose {
    out.visible = false; out.sx = 1.0; out.sy = 1.0; out.alpha = 1.0; out.rotation = item.rotation; out.x = item.x; out.y = item.y
    when (item.kind) {
        "strip" -> {
            val half = item.length / 2
            val wipe = if (item.style == "cut") 1.25 else if (item.style == "lace") 0.55 else 0.42
            val raw = c01((time - item.at) / wipe)
            val enter = if (item.style == "cut") raw * raw * (3 - 2 * raw) else easeOutCubicC(raw)
            val exit = easeInCubicC(leave(time, item.exitAt, item.exitDuration))
            out.visible = time > item.at && exit < 1
            if (!out.visible) return out
            val startAt = if (item.from < 0) exit else 1 - enter
            val endAt = if (item.from < 0) enter else 1 - exit
            val x0 = -half + item.length * min(startAt, endAt)
            val width = item.length * max(0.0, abs(endAt - startAt))
            out.bandX0 = x0; out.bandWidth = width
            val tile = if (tileWidth > 0) tileWidth else 1.0
            out.tileX = jsMod(jsMod(item.speed * time - (x0 + half), tile) + tile, tile)
            out.bladeVisible = item.style == "cut" && raw < 1 && exit <= 0
            if (out.bladeVisible) {
                val age = time - item.at
                out.bladeX = if (item.from < 0) x0 + width else x0
                out.bladeSx = if (item.from < 0) 1.0 else -1.0
                out.bladeSy = 1 - 0.3 * abs(sin(age * 16))
                out.bladeAlpha = c01(age / 0.12)
            }
        }
        "echo" -> {
            val enter = easeOutCubicC((time - item.at) / 0.6)
            val exit = leave(time, item.exitAt, item.exitDuration)
            val alpha = item.alpha * enter * (1 - exit)
            out.visible = alpha > 0.002
            if (!out.visible) return out
            out.alpha = alpha
            out.sx = 1 + 0.06 * (1 - enter); out.sy = out.sx
            if (item.axis == "x") { out.tileX = item.speed * time; out.tileY = 0.0 } else { out.tileY = item.speed * time; out.tileX = 0.0 }
        }
        "patch" -> {
            val enter = popIn(time, item.at, 0.36)
            val exit = leave(time, item.exitAt, item.exitDuration)
            out.visible = time >= item.at && exit < 1
            if (!out.visible) return out
            out.alpha = c01((time - item.at) / 0.12) * (1 - exit)
            out.sx = 1.14 - 0.14 * enter; out.sy = out.sx
            out.rotation = item.rotation + 0.12 * (1 - min(1.0, enter))
            out.y = item.y + exit * item.height * 0.15
        }
        "window" -> {
            val enter = popIn(time, item.at, 0.34)
            val exit = leave(time, item.exitAt, item.exitDuration)
            out.visible = time >= item.at && exit < 1
            if (!out.visible) return out
            out.alpha = c01((time - item.at) / 0.1) * (1 - easeInCubicC(exit))
            out.sx = (0.86 + 0.14 * enter) * (1 - 0.12 * exit); out.sy = out.sx
        }
        "sticker" -> {
            val exit = leave(time, item.exitAt, min(0.3, item.exitDuration))
            out.visible = time >= item.at && exit < 1
            if (!out.visible) return out
            val base = item.size / (HB_STICKER_PX * 0.8)
            val phase = jsMod(item.x * 0.013 + item.y * 0.007, HB_TAU)
            val enter = popIn(time, item.at)
            val age = time - item.at
            out.sx = base * enter * (1 - easeInCubicC(exit)); out.sy = out.sx
            out.rotation = item.rotation + (1 - min(1.0, enter)) * 0.8 + sin(age * 2.6 + phase) * 0.07 + exit * 0.9
            out.y = item.y + sin(age * 1.9 + phase) * item.size * 0.05
        }
        "tape" -> {
            val exit = leave(time, item.exitAt, item.exitDuration)
            out.visible = time >= item.at && exit < 1
            if (!out.visible) return out
            val stick = easeOutCubicC((time - item.at) / 0.16)
            // sx / sy 是相对「原尺寸」（length × width）的倍率。
            out.sx = 1.25 - 0.25 * stick; out.sy = out.sx
            out.alpha = stick * (1 - exit)
        }
        "label" -> {
            val exit = leave(time, item.exitAt, item.exitDuration)
            out.visible = time >= item.at && exit < 1
            if (!out.visible) return out
            out.sx = popIn(time, item.at, 0.28) * (1 - easeInCubicC(exit)); out.sy = out.sx
        }
        "headline" -> {
            val exit = leave(time, item.exitAt, item.exitDuration)
            out.visible = time >= item.at && exit < 1
            if (!out.visible) return out
            val age = time - item.at
            out.sx = popIn(time, item.at, 0.4) * (1 - easeInCubicC(exit)); out.sy = out.sx
            out.rotation = item.rotation + sin(age * 1.6) * 0.015
            out.y = item.y + sin(age * 2.1) * item.size * 0.04
        }
        "pop" -> {
            val age = time - item.at
            out.visible = age >= 0 && age <= item.duration
            if (!out.visible) return out
            val fade = c01((age - (item.duration - 0.35)) / 0.35)
            val pop = easeOutBackC(age / 0.24, 3.0)
            val swing = exp(-age / 0.18) * sin(age * 38) * 0.22
            out.sx = max(0.0, pop) * (1 + 0.06 * fade); out.sy = out.sx
            out.rotation = item.rotation + swing
            out.y = item.y - fade * item.size * 0.6
            out.alpha = 1 - easeInCubicC(fade)
        }
        "whoosh" -> {
            val age = time - item.at
            val life = item.duration + 0.55
            out.visible = age >= 0 && age <= life
            if (!out.visible) return out
            val p = easeOutCubicC(age / item.duration)
            out.x = item.fromX + (item.toX - item.fromX) * p + item.size * 0.8
            out.y = item.fromY + (item.toY - item.fromY) * p + item.size * 0.3
            out.alpha = c01(age / 0.08) * (1 - c01((age - item.duration - 0.2) / 0.35))
            out.rotation = p * PI * 1.5
            out.sx = 0.7 + 0.3 * c01(age / item.duration); out.sy = out.sx
        }
        "floater" -> {
            val age = time - item.at
            out.visible = age >= 0 && age <= item.duration
            if (!out.visible) return out
            val base = item.size / (HB_STICKER_PX * 0.8)
            val phase = jsMod(item.x * 0.017, HB_TAU)
            val p = age / item.duration
            out.x = item.x + sin(age * 2.2 + phase) * item.sway
            out.y = item.y - item.rise * p
            out.rotation = sin(age * 1.7 + phase) * 0.25
            out.sx = base * min(1.0, popIn(time, item.at, 0.3)); out.sy = out.sx
            out.alpha = 1 - c01((p - 0.82) / 0.18)
        }
    }
    return out
}

// ───────────────────────────── unitCollage.ts：整单元一份 ─────────────────────────────

/** 网点底纹 + 左缘竖排胶片：参数在建单元时定好，每帧只由 t 与 rise(t) 决定。 */
internal class HbUnitCollage(val width: Double, val height: Double, seed: String, val paper: Int, val amount: Double, val startTime: Double, val px: Double = 1.0) {
    val unit = min(width, height)
    private val rng = lumiereRng("$seed:unit-collage")
    /** 胶片主色（CANDY 里随机一种）。 */
    val railColor: Int = HB_CANDY[floor(rng.next() * HB_CANDY.size).toInt()]
    val dotAlpha = 0.05 + 0.03 * min(1.0, amount)
    val railThickness = max(20.0 * px, unit * 0.055)
    val railLength = height * 1.3
    val railX = max(railThickness * 0.9, width * 0.022)
    val railY = height / 2
    val railAlpha = 0.92
    val drift = unit * 0.03

    /** 网点平铺的纵向偏移。 */
    fun dotsOffsetY(rise: Double) = rise * 0.35

    /** 胶片沿带子（本地 +x 指向画面上方）的平铺偏移，tile = 纹理宽。 */
    fun railTileX(time: Double, rise: Double, tile: Double): Double {
        val t = if (tile > 0) tile else 1.0
        return jsMod(jsMod(-(rise + drift * (time - startTime)), t) + t, t)
    }
}

/** 网点底纹的小格边长（逻辑像素）。 */
internal const val HB_DOT_TILE = 26.0

@Suppress("unused")
private fun roundD(v: Double) = round(v)
