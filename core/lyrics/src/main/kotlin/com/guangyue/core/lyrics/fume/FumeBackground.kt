// 对应 Folia 文件：src/components/visualizer/fume/FumeBackground.ts（buildFumeBackgroundScene）、
//                fumeCameraStep.ts（resolveFumeBackgroundLayerView）
//
// V1b-5「浮名」世界背景物体：纸页四周的大几何（环 / 方 / 十字）+ 纸页内的小星芒，
// 随镜头视差、慢速自转。纯 Kotlin/JVM，数值逐条照抄 Folia；绘制在 feature 侧（FumeStage）。
package com.guangyue.core.lyrics.fume

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

enum class FumeBackgroundShapeKind { RING, SQUARE, CROSS, SPARK }

/** 对应 FumeBackgroundShapeColor：'secondary' | 'accent'。 */
enum class FumeBackgroundShapeColor { SECONDARY, ACCENT }

data class FumeBackgroundShape(
    val id: String,
    val kind: FumeBackgroundShapeKind,
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val rotation: Double,
    val rotationSpeed: Double,
    val strokeWidth: Double,
    val opacity: Double,
    val color: FumeBackgroundShapeColor,
    val depth: Double,
    val ringGapStart: Double? = null,
    val ringGapSize: Double? = null,
)

data class FumeBackgroundScene(
    val width: Double,
    val height: Double,
    val shapes: List<FumeBackgroundShape>,
)

/** 对应 resolveFumeBackgroundLayerView 的返回值。 */
data class FumeBackgroundLayerView(
    val centerX: Double,
    val centerY: Double,
    val cameraX: Double,
    val cameraY: Double,
    val scale: Double,
)

object FumeBackground {

    private const val PARALLAX_X = 0.9
    private const val PARALLAX_Y = 0.74
    private const val SCALE_FACTOR = 0.94
    private const val VERTICAL_OFFSET_RATIO = 0.22

    /** 对应 useFumeCanvasStage 的 `objectOpacityMultiplier: backgroundObjectOpacity * 2`。 */
    fun opacityMultiplier(backgroundObjectOpacity: Double): Double =
        FumeMath.clamp(backgroundObjectOpacity * 2.0, 0.0, 2.0)

    /**
     * FNV-1a，与 FumeBackground.ts 的 hashString 完全一致：按 UTF-16 码元整值异或
     * （注意 [FumeMath.hashString] 只取低 8 位，对中文种子不一致，这里不复用）。
     */
    internal fun hashString(input: String): Long {
        var hash = 2_166_136_261L
        for (ch in input) {
            hash = hash xor ch.code.toLong()
            hash = (hash * 16_777_619L) and 0xFFFFFFFFL
        }
        return hash
    }

    internal fun seeded(seed: String): Double = (hashString(seed) % 10_000L) / 10_000.0

    /** JS `${number}`：整数不带 `.0`（种子字符串里要与 Folia 同形）。 */
    internal fun jsNumber(value: Double): String =
        if (value == floor(value) && kotlin.math.abs(value) < 1e15) value.toLong().toString() else value.toString()

    private fun clamp(v: Double, min: Double, max: Double) = FumeMath.clamp(v, min, max)
    private fun mix(a: Double, b: Double, t: Double) = FumeMath.mix(a, b, t)

    private fun normalize(b: FumePaperBounds, w: Double, h: Double) = FumePaperBounds(
        left = clamp(minOf(b.left, b.right), 0.0, w),
        top = clamp(minOf(b.top, b.bottom), 0.0, h),
        right = clamp(maxOf(b.left, b.right), 0.0, w),
        bottom = clamp(maxOf(b.top, b.bottom), 0.0, h),
    )

    private fun haloAnchor(
        paper: FumePaperBounds,
        worldWidth: Double,
        worldHeight: Double,
        width: Double,
        height: Double,
        seedKey: String,
    ): Pair<Double, Double> {
        if (seeded("$seedKey:inside-chance") < 0.22) {
            return clamp(mix(paper.left + width * 0.12, paper.right - width * 0.12, seeded("$seedKey:inside-x")), 0.0, worldWidth) to
                clamp(mix(paper.top + height * 0.12, paper.bottom - height * 0.12, seeded("$seedKey:inside-y")), 0.0, worldHeight)
        }
        val side = floor(seeded("$seedKey:side") * 4).toInt() % 4
        val overflowX = width * mix(0.16, 0.24, seeded("$seedKey:overflow-x"))
        val overflowY = height * mix(0.16, 0.24, seeded("$seedKey:overflow-y"))
        val jitterX = width * mix(-0.18, 0.18, seeded("$seedKey:span-jitter-x"))
        val jitterY = height * mix(-0.18, 0.18, seeded("$seedKey:span-jitter-y"))
        val alongY = clamp(mix(paper.top - height * 0.12, paper.bottom + height * 0.12, seeded("$seedKey:y")) + jitterY, 0.0, worldHeight)
        val alongX = clamp(mix(paper.left - width * 0.12, paper.right + width * 0.12, seeded("$seedKey:x")) + jitterX, 0.0, worldWidth)
        return when (side) {
            0 -> clamp(paper.left - overflowX, 0.0, worldWidth) to alongY
            1 -> clamp(paper.right + overflowX, 0.0, worldWidth) to alongY
            2 -> alongX to clamp(paper.top - overflowY, 0.0, worldHeight)
            else -> alongX to clamp(paper.bottom + overflowY, 0.0, worldHeight)
        }
    }

    /** 对应 buildFumeBackgroundScene。尺寸单位与排版世界相同（dp = Folia 的 CSS px）。 */
    fun buildScene(
        viewport: FumeViewport,
        worldWidthIn: Double,
        worldHeightIn: Double,
        paperBounds: FumePaperBounds?,
        seed: String = "fume",
    ): FumeBackgroundScene {
        if (viewport.width <= 0 || viewport.height <= 0 || worldWidthIn <= 0 || worldHeightIn <= 0) {
            return FumeBackgroundScene(
                maxOf(worldWidthIn, viewport.width, 1.0),
                maxOf(worldHeightIn, viewport.height, 1.0),
                emptyList(),
            )
        }
        val worldWidth = maxOf(worldWidthIn, viewport.width * 1.2)
        val worldHeight = maxOf(worldHeightIn, viewport.height * 1.2)
        val baseUnit = clamp(minOf(viewport.width, viewport.height) * 0.72, 320.0, 760.0)
        val paper = normalize(
            paperBounds ?: FumePaperBounds(worldWidth * 0.24, worldHeight * 0.18, worldWidth * 0.76, worldHeight * 0.82),
            worldWidth,
            worldHeight,
        )
        val kinds = listOf(
            FumeBackgroundShapeKind.RING, FumeBackgroundShapeKind.SQUARE, FumeBackgroundShapeKind.CROSS,
            FumeBackgroundShapeKind.RING, FumeBackgroundShapeKind.SQUARE, FumeBackgroundShapeKind.CROSS,
        )
        val landscape = worldWidth > worldHeight
        val shapeCount = if (landscape) 8 else 7
        val sparkCount = if (landscape) 12 else 9
        val sparkLeft = worldWidth * 0.2
        val sparkRight = worldWidth * 0.8
        val sparkTop = worldHeight * 0.18
        val sparkBottom = worldHeight * 0.82
        val sparkColumns = ceil(sqrt(sparkCount * (worldWidth / maxOf(worldHeight, 1.0)))).toInt().coerceAtLeast(1)
        val sparkRows = ceil(sparkCount.toDouble() / sparkColumns).toInt()
        val cellW = (sparkRight - sparkLeft) / sparkColumns
        val cellH = (sparkBottom - sparkTop) / sparkRows
        val ww = jsNumber(worldWidth)
        val wh = jsNumber(worldHeight)
        val color = { key: String ->
            if (seeded("$key:color") > 0.5) FumeBackgroundShapeColor.ACCENT else FumeBackgroundShapeColor.SECONDARY
        }

        val base = (0 until shapeCount).map { index ->
            val key = "$seed:$ww:$wh:$index"
            val kind = kinds[index % kinds.size]
            val width = baseUnit * mix(0.82, 1.36, seeded("$key:size"))
            val (ax, ay) = haloAnchor(paper, worldWidth, worldHeight, width, width, key)
            val ring = kind == FumeBackgroundShapeKind.RING
            FumeBackgroundShape(
                id = "fume-bg-$index",
                kind = kind,
                x = ax,
                y = ay,
                width = width,
                height = width,
                rotation = mix(-PI * 0.2, PI * 0.2, seeded("$key:rotation")),
                rotationSpeed = mix(-0.045, 0.045, seeded("$key:rotation-speed")),
                strokeWidth = mix(0.25, 2.1, seeded("$key:stroke-width")),
                opacity = mix(0.01, 0.16, seeded("$key:opacity")),
                color = color(key),
                depth = seeded("$key:depth"),
                ringGapStart = if (ring) mix(-PI, PI, seeded("$key:gap-start")) else null,
                ringGapSize = if (ring) mix(PI * 0.12, PI * 0.24, seeded("$key:gap-size")) else null,
            )
        }
        val sparks = (0 until sparkCount).map { index ->
            val key = "$seed:$ww:$wh:spark:$index"
            val width = baseUnit * mix(0.1, 0.24, seeded("$key:size"))
            val column = index % sparkColumns
            val row = index / sparkColumns
            val jx = mix(-0.32, 0.32, seeded("$key:jitter-x")) * cellW
            val jy = mix(-0.32, 0.32, seeded("$key:jitter-y")) * cellH
            FumeBackgroundShape(
                id = "fume-spark-$index",
                kind = FumeBackgroundShapeKind.SPARK,
                x = clamp(sparkLeft + (column + 0.5) * cellW + jx, sparkLeft, sparkRight),
                y = clamp(sparkTop + (row + 0.5) * cellH + jy, sparkTop, sparkBottom),
                width = width,
                height = width,
                rotation = mix(-PI, PI, seeded("$key:rotation")),
                rotationSpeed = mix(-0.18, 0.18, seeded("$key:rotation-speed")),
                strokeWidth = mix(0.75, 1.7, seeded("$key:stroke-width")),
                opacity = mix(0.08, 0.22, seeded("$key:opacity")),
                color = color(key),
                depth = seeded("$key:depth"),
            )
        }
        return FumeBackgroundScene(worldWidth, worldHeight, (base + sparks).sortedBy { it.depth })
    }

    /** 对应 resolveFumeBackgroundLayerView：背景层拖在镜头后（视差）、略低、缩放少一点。 */
    fun view(scene: FumeBackgroundScene, cameraX: Double, cameraY: Double, cameraScale: Double, viewport: FumeViewport): FumeBackgroundLayerView {
        val cx = scene.width * 0.5
        val cy = scene.height * 0.5
        val vOffset = clamp(viewport.height * VERTICAL_OFFSET_RATIO / maxOf(cameraScale, 0.001), 48.0, 180.0)
        return FumeBackgroundLayerView(
            centerX = cx,
            centerY = cy,
            cameraX = mix(cx, cameraX, PARALLAX_X),
            cameraY = mix(cy, cameraY, PARALLAX_Y) - vOffset,
            scale = clamp(cameraScale * SCALE_FACTOR, FumeCamera.SCALE_MIN, FumeCamera.SCALE_MAX),
        )
    }

    /** 对应 drawFumeBackground 里每个形状的视差偏移（strength 0.72）。 */
    fun parallaxOffset(shape: FumeBackgroundShape, view: FumeBackgroundLayerView, strength: Double = 0.72): Pair<Double, Double> {
        val layerResponse = mix(0.58, 1.16, shape.depth)
        return (view.cameraX - view.centerX) * (1 - layerResponse) * strength to
            (view.cameraY - view.centerY) * (1 - layerResponse) * strength
    }

    /** 对应 drawFumeBackground：无音频时 audioOpacityBoost = 1，透明度上限 0.42。 */
    fun drawOpacity(shape: FumeBackgroundShape, multiplier: Double): Double =
        clamp(shape.opacity * multiplier, 0.0, 0.42)
}
