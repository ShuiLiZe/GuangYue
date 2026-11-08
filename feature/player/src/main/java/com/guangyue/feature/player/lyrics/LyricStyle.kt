package com.guangyue.feature.player.lyrics

/**
 * V1a：歌词渲染样式。
 *
 * 对应 Folia `src/components/visualizer/registry.tsx` 的 `VISUALIZER_REGISTRY`：那边是一张
 * 「id → 渲染器」的表，手机端先落两项：
 *
 * - [CLASSIC]：「流光」（Folia classic。zh-CN.ts:915 `visualizerClassic`）；
 * - [FUME]：「浮名」（Folia fume。zh-CN.ts `ui.visualizerFume`）。
 *
 * [id] 是**落盘用的稳定串**：`core/data` 的偏好层不能依赖 `feature:player`，所以那边只存
 * String（默认 "classic"），解析（`String? → LyricStyle`）由这一层负责，枚举本身留在 feature 侧。
 *
 * 注意：这里的 id 只用于持久化，**不要**当文案显示；界面上显示的中文一律走
 * `app/src/main/res/values/strings.xml` 的资源 id（`lyrics_style_classic` / `lyrics_style_fume`）。
 */
enum class LyricStyle(val id: String) {
    /** 「流光」：Folia classic 渲染器（逐词布局 / 逐字辉光 / 呼吸浮动）。 */
    CLASSIC("classic"),

    /** 「浮名」：Folia fume 渲染器（V1a 只接样式切换与持久化，渲染由 fume-draw 那一层落）。 */
    FUME("fume"),

    /** 「云阶」：Folia partita 渲染器（V2a：当前句切 chunk 左右错落 + 引导线，词仍是 GlowWord）。 */
    PARTITA("partita"),

    /** 「心象」：Folia cadenza 渲染器（V2b：整句拆词散落、主词居中放大、碰撞避让）。Folia 无参数面板。 */
    CADENZA("cadenza"),

    /** 「静止」：Folia still（V2c：三行静态居中 + 暗角，不做动画，最省资源）。无参数。 */
    STILL("still"),

    /** 「倾诉」：Folia tilt（V2d：长句断成 1–4 段，随机一段斜体强调，逐字脉动）。 */
    TILT("tilt"),

    /** 「回环」：Folia claddagh（V2e：歌词排在倾斜椭圆轨道上，句间隔 180°，当前字转到焦点）。 */
    CLADDAGH("claddagh"),

    /** 「莫奈」：Folia monet（P3：海报版面——斜体歌手 + 竖线 + 大标题 + 专辑 + 左侧歌词轨；手机不画肖像）。无参数。 */
    MONET("monet"),

    /** 「时计」：Folia pendolo（P4：歌词沿钟轮弧线一格格推进 + 线框钟芯；手机不做 WebGL 机芯）。无参数。 */
    PENDOLO("pendolo"),

    /** 「雾笺」：Folia nebbia（P5：夜色玻璃起雾，歌词逐字被手指擦开、水珠下淌、旧句被雾吞回；手机不做 WebGL 折射）。无参数。 */
    NEBBIA("nebbia"),

    /** 「商籁」：Folia sonnet（P6：歌词编成 PV 镜头——段落→镜头→7 种版式，主角词放大 / 竖排、词按唱到逐个打进、开口框、五线谱外框；手机不做 Pixi 滤镜与 MG 图库）。无参数。 */
    SONNET("sonnet"),

    /** 「花火」：Folia hanabi（P7：夜空里每句开唱放一朵烟花，长空隙一串スターマイン；字底色块 + 逐字升入、唱到弹跳染金、换句向上飞走；手机不做 Pixi 精灵池与拼贴 MG）。无参数。 */
    HANABI("hanabi"),
    /** 「凝彩」：Folia tempera（P8：网点图形 MG，纸 / 墨 / 强调色六族构图、slice 切镜与无字 bridge、hero 词放大、七种逐词入场与浮影、Difference 反色、超大水印、纵向推镜交接；手机不做 Pixi runtime / 121 种镜头 / 后处理）。无参数。 */
    TEMPERA("tempera"),
    /** 「绘光」：Folia lumiere（P9：暗场里十族光位——天光 / 窗隙 / 棱镜 / 焦散 / 光路 / 衍射 / 叶脉 / 星象 / 追光 / 萤尘——按镜头轮换、交叉淡化，段首星空点亮；字在文字区横排或竖排，唱到点亮带光晕与十字爆闪，换句崩解上漂；手机不做 Pixi runtime / 100 种光位 / bloom）。无参数。 */
    LUMIERE("lumiere"),
    ;

    companion object {
        /**
         * 由落盘串还原样式；未知 / 空 / 非法值一律回落 [CLASSIC]。
         *
         * 与 Folia registry 的默认行为一致：用户偏好里的样式被删掉（或手改坏）时，
         * 界面要开得起来，落到默认渲染器而不是崩在解析上。
         */
        fun fromId(id: String?): LyricStyle {
            val key = id?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.id == key } ?: CLASSIC
        }
    }
}