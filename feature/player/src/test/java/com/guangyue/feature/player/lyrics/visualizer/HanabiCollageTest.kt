package com.guangyue.feature.player.lyrics.visualizer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * R7 花火⑩：PV 拼贴与 Folia hanabi/collage 对拍。金标 `hanabi_collage_golden.txt`（docs/port/scripts/hanabi-collage-golden-gen.js）：
 * 69 个镜头（11 个族 × 数量 0 / 0.5 / 0.8 / 1 / 2、间奏、开场标题卡、上一个配方、字块样式、宽 / 窄字组、有无关键词）跑真的 planShotCollage，
 * 逐件逐字段比；每件再用假 Pixi 跑真的 createShotCollage，13 个时刻比显隐 / 位置 / 缩放 / 旋转 / 透明度 / 平铺偏移 / 剪刀。
 */
class HanabiCollageTest {
    private fun close(msg: String, want: Double, got: Double) = assertEquals(msg, want, got, 2e-6 + 2e-7 * abs(want))
    private val palette = HbPalette(0x1a1030, 0xfff6ea, listOf(0xe8344e, 0x2f6fe0, 0xf28c28), listOf(0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff), 0xff5d8f, 0xffe08a)

    private fun JsonElement.d() = jsonPrimitive.doubleOrNull ?: jsonPrimitive.content.toDouble()
    private fun JsonObject.d(k: String) = this[k]!!.d()
    private fun JsonObject.s(k: String) = this[k]?.let { if (it is JsonNull) null else it.jsonPrimitive.content }
    private fun rect(o: JsonObject) = HbCollageRect(o.d("x"), o.d("y"), o.d("width"), o.d("height"))

    private fun field(it: HbCollageItem, k: String): Any? = when (k) {
        "kind" -> it.kind; "x" -> it.x; "y" -> it.y; "at" -> it.at; "exitAt" -> it.exitAt; "exitDuration" -> it.exitDuration
        "style" -> it.style; "length" -> it.length; "thickness" -> it.thickness; "rotation" -> it.rotation; "color" -> it.color.toDouble()
        "speed" -> it.speed; "from" -> it.from.toDouble(); "width" -> it.width; "height" -> it.height; "text" -> it.text; "size" -> it.size
        "axis" -> it.axis; "alpha" -> it.alpha; "shape" -> it.shape; "label" -> it.label; "layer" -> it.layer; "pattern" -> it.pattern
        "dark" -> it.dark; "bar" -> it.bar; "title" -> it.title; "icon" -> it.icon; "duration" -> it.duration
        "fromX" -> it.fromX; "fromY" -> it.fromY; "toX" -> it.toX; "toY" -> it.toY; "rise" -> it.rise; "sway" -> it.sway
        else -> error("unknown field $k")
    }

    private fun tileOf(item: HbCollageItem) = when (item.kind) {
        "strip" -> when (item.style) { "film" -> 70.0; "cut" -> 13.0; else -> 37.0 }
        else -> 1.0
    }

    @Test
    fun collageMatchesFolia() {
        val lines = javaClass.classLoader!!.getResourceAsStream("hanabi_collage_golden.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
        var plan: HbCollagePlan? = null
        var tag = ""
        var items = 0; var poses = 0
        for (ln in lines) {
            val o = Json.parseToJsonElement(ln).jsonObject
            if (o.containsKey("rail")) {
                o["rail"]!!.jsonArray.forEachIndexed { i, v -> assertEquals("rail $i", v.d().toInt(), HbUnitCollage(800.0, 360.0, "u$i", 0xffffff, 1.0, 0.0).railColor) }
                continue
            }
            if (o.containsKey("c")) {
                val c = o["c"]!!.jsonObject
                val screen = rect(c["screen"]!!.jsonObject)
                val drift = c.d("drift"); val enterAt = c.d("enterAt")
                val opening = c["opening"]?.takeIf { it !is JsonNull }?.jsonObject
                val input = HbCollagePlanInput(
                    seed = c.s("seed")!!, family = c.s("family")!!, isBridge = c["isBridge"]!!.jsonPrimitive.booleanOrNull!!, block = c.s("block")!!,
                    shotNumber = c.d("shotNumber").toInt(), bounds = rect(c["bounds"]!!.jsonObject), screen = screen,
                    screenAt = { t -> HbCollageRect(screen.x, screen.y - drift * (t - enterAt), screen.width, screen.height) },
                    unit = c.d("unit"), palette = palette, enterAt = enterAt, exitAt = c.d("exitAt"), exitDuration = c.d("exitDuration"),
                    start = c.d("start"), end = c.d("end"), keyword = c.s("keyword"), motifs = c["motifs"]!!.jsonArray.map { it.jsonPrimitive.content },
                    bursts = c["bursts"]!!.jsonArray.map { b -> val q = b.jsonObject; HbCollageBurst(q.d("x"), q.d("y"), q.d("at"), q.s("kind")!!, q["main"]!!.jsonPrimitive.booleanOrNull!!, q.d("radius")) },
                    launches = c["launches"]!!.jsonArray.map { b -> val q = b.jsonObject; HbLaunchEvent(q.d("fromX"), q.d("fromY"), q.d("toX"), q.d("toY"), q.d("at"), q.d("duration"), 0, "") },
                    amount = c.d("amount"), icons = null, previous = c.s("previous"),
                    openingTitle = opening?.s("title"), openingArtist = opening?.s("artist"),
                )
                tag = input.seed
                val p = hbPlanShotCollage(input)
                plan = p
                assertEquals("$tag recipe", o.s("recipe"), p.recipe)
                val want = o["items"]!!.jsonArray
                assertEquals("$tag item count", want.size, p.items.size)
                want.forEachIndexed { k, w ->
                    for ((key, v) in w.jsonObject) {
                        val got = field(p.items[k], key)
                        val msg = "$tag item $k ${p.items[k].kind}.$key"
                        when {
                            v is JsonNull -> assertEquals(msg, null, got)
                            v is JsonPrimitive && v.isString -> {
                                val sv = v.content
                                if (sv == "Infinity" || sv == "-Infinity") error("$msg infinite") else assertEquals(msg, sv, got)
                            }
                            v is JsonPrimitive && v.booleanOrNull != null -> assertEquals(msg, v.booleanOrNull, got)
                            else -> close(msg, v.d(), got as Double)
                        }
                    }
                    items++
                }
                continue
            }
            val item = plan!!.items[o["item"]!!.jsonPrimitive.content.toInt()]
            val pose = HbCollagePose()
            for (pe in o["poses"]!!.jsonArray) {
                val q = pe.jsonObject
                val t = q.d("t")
                hbCollagePose(item, t, pose, tileOf(item))
                val msg = "$tag ${item.kind} t=$t"
                assertEquals("$msg visible", q.d("v") == 1.0, pose.visible)
                if (!pose.visible) continue
                when (item.kind) {
                    "strip" -> {
                        close("$msg bandX0", q.d("bx"), pose.bandX0); close("$msg bandW", q.d("bw"), pose.bandWidth); close("$msg tileX", q.d("tx"), pose.tileX)
                        if (q.containsKey("lv")) {
                            assertEquals("$msg blade", q.d("lv") == 1.0, pose.bladeVisible)
                            if (pose.bladeVisible) { close("$msg bladeX", q.d("lx"), pose.bladeX); close("$msg bladeSx", q.d("lsx"), pose.bladeSx); close("$msg bladeSy", q.d("lsy"), pose.bladeSy); close("$msg bladeA", q.d("la"), pose.bladeAlpha) }
                        }
                    }
                    "echo" -> { close("$msg a", q.d("a"), pose.alpha); close("$msg s", q.d("s"), pose.sx); close("$msg tx", q.d("tx"), pose.tileX); close("$msg ty", q.d("ty"), pose.tileY) }
                    "tape" -> { close("$msg s", q.d("s"), pose.sx); close("$msg a", q.d("a"), pose.alpha) }
                    else -> {
                        close("$msg x", q.d("x"), pose.x); close("$msg y", q.d("y"), pose.y); close("$msg s", q.d("s"), pose.sx)
                        close("$msg rot", q.d("rot"), pose.rotation); close("$msg a", q.d("a"), pose.alpha)
                    }
                }
                poses++
            }
        }
        assertTrue("checked $items items / $poses poses", items > 300 && poses > 2000)
    }
}
