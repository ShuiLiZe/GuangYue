require('./hb.js');
const P = HB.HANABI_PROFILES;
const n = v => { const s = String(v); return /[.e]/.test(s) ? s : s + '.0'; };
const q = s => JSON.stringify(s);
const burst = b => b ? `HbBurstSpec(${q(b.kind)}, ${n(b.count)}, ${n(b.radius)}, ${n(b.duration)}, listOf(${b.colors.join(', ')}), ${n(b.trail)}, ${n(b.twinkle)})` : 'null';
let out = `package com.guangyue.feature.player.lyrics.visualizer

/*
 * R7 花火⑥b：Folia hanabi/catalog.ts 的 50 个镜头预设（10 族 × 5），逐字段照搬（docs/port/scripts/hanabi-catalog-gen.js 由 Folia 源码求值后生成，勿手改）。
 * 顺序与 HANABI_PROFILE_LITE 一致（castShot 依赖顺序）。
 */

internal class HbTextSpec(
    val layout: String, val heroSize: Double, val emphasis: Double, val tilt: Double,
    val block: String, val enter: String, val sing: String, val exit: String,
    val stagger: Double, val outline: Boolean, val hardShadow: Boolean,
)

internal class HbRegion(val cx: Double, val cy: Double, val w: Double, val h: Double)

internal class HbOrnamentSpec(val kind: String, val placement: String, val density: Double, val scale: Double, val color: String, val delay: Double, val layer: String)

internal class HbProfile(
    val kind: String, val label: String, val family: String, val mood: String,
    val text: HbTextSpec, val region: HbRegion,
    val burst: HbBurstSpec?, val burstAt: String, val sungBurst: HbBurstSpec?,
    val ornaments: List<HbOrnamentSpec>, val launch: Boolean, val palette: String,
    val rise: Double, val push: Double, val roll: Double, val punch: Double,
)

/** catalog.ts BURST_PRESETS（burstPreset(kind, colors)）。 */
internal fun hbBurstPreset(kind: String, colors: List<Int> = listOf(0, 1, 2)): HbBurstSpec = when (kind) {
`;
for (const k of HB.BURST_KINDS) { const b = HB.burstPreset(k); out += `    ${q(k)} -> HbBurstSpec(kind, ${n(b.count)}, ${n(b.radius)}, ${n(b.duration)}, colors, ${n(b.trail)}, ${n(b.twinkle)})\n`; }
out += `    else -> HbBurstSpec(kind, 96.0, 0.3, 1.9, colors, 0.65, 0.2)\n}\n\ninternal val HANABI_PROFILES: List<HbProfile> = listOf(\n`;
for (const p of P) {
  const t = p.text;
  out += `    HbProfile(${q(p.kind)}, ${q(p.label)}, ${q(p.family)}, ${q(p.mood)},\n        HbTextSpec(${q(t.layout)}, ${n(t.heroSize)}, ${n(t.emphasis)}, ${n(t.tilt)}, ${q(t.block)}, ${q(t.enter)}, ${q(t.sing)}, ${q(t.exit)}, ${n(t.stagger)}, ${t.outline}, ${t.hardShadow}),\n`;
  out += `        HbRegion(${n(p.region.cx)}, ${n(p.region.cy)}, ${n(p.region.w)}, ${n(p.region.h)}),\n`;
  out += `        ${burst(p.burst)}, ${q(p.burstAt)}, ${burst(p.sungBurst)},\n`;
  out += `        listOf(${p.ornaments.map(o => `HbOrnamentSpec(${q(o.kind)}, ${q(o.placement)}, ${n(o.density)}, ${n(o.scale)}, ${q(o.color)}, ${n(o.delay)}, ${q(o.layer)})`).join(', ')}),\n`;
  out += `        ${p.launch}, ${q(p.palette)}, ${n(p.camera.rise)}, ${n(p.camera.push)}, ${n(p.camera.roll)}, ${n(p.camera.punch)}),\n`;
}
out += `)\n\nprivate val HB_PROFILE_BY_KIND: Map<String, HbProfile> = HANABI_PROFILES.associateBy { it.kind }\n\n/** profileOf：未知 kind 退回第一个。 */\ninternal fun hanabiProfileOf(kind: String): HbProfile = HB_PROFILE_BY_KIND[kind] ?: HANABI_PROFILES[0]\n`;
require('fs').writeFileSync('HanabiCatalog.kt', out);
console.log(out.length, P.map(p=>p.text.block).filter((v,i,a)=>a.indexOf(v)===i).join(','), P.map(p=>p.text.enter).filter((v,i,a)=>a.indexOf(v)===i).length);
