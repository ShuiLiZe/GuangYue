// 凝彩后处理金样：照 Folia sonnetLensFilter / sonnetPrintFilters(vignette) / Pixi NoiseFilter 的 GLSL 公式（float64）逐点算。
// 运行：node docs/port/scripts/tempera-post-golden-gen.mjs
const lens = (u, v, aspect, d) => {
  let cx = (u - 0.5) * aspect; const cy = v - 0.5;
  const r2 = cx * cx + cy * cy; const k = d * 0.32;
  const rs = 1 - k * r2 + k * 0.16 * r2 * r2;
  return [cx * rs / aspect + 0.5, cy * rs + 0.5];
};
const smooth = (a, b, x) => { const t = Math.min(1, Math.max(0, (x - a) / (b - a))); return t * t * (3 - 2 * t); };
const vig = (u, v, aspect, amt) => { const cx = (u - 0.5) * aspect, cy = v - 0.5; return Math.min(1, Math.max(0, smooth(0.52, 1.08, Math.hypot(cx, cy)) * amt * 0.6)); };
const rand = (x, y) => { const s = Math.sin(x * 12.9898 + y * 78.233) * 43758.5453; return s - Math.floor(s); };
const out = [];
for (const [u, v, a] of [[0, 0, 2.2222], [1, 1, 0.45], [0.25, 0.8, 1.7778], [0.5, 0.5, 1], [0.9, 0.1, 2.2222]]) {
  const [lu, lv] = lens(u, v, a, 0.3);
  out.push(`L ${u} ${v} ${a} ${lu.toFixed(12)} ${lv.toFixed(12)} ${vig(u, v, a, 0.85).toFixed(12)}`);
}
for (const [x, y] of [[0.5, 0.5], [123.5, 77.5], [1599.5, 719.5]]) out.push(`R ${x} ${y} ${rand(x * 0.4321, y * 0.4321).toFixed(9)}`);
console.log(out.join('\n'));
