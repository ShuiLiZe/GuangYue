// 町景 lens.ts / clock.ts / camera.ts resolveMachiDrift 金标：rolldown machi-lens-golden-entry.ts -f cjs -o lens.js 后 node machi-lens-golden-gen.js > machi_lens_golden.txt（rolldown 会摇掉 three）。
require('./lens.js');
const L = MCL;
const r = v => Number(v.toPrecision(12));
const out = [];
for (const aperture of [1.4, 2, 2.8, 4, 5.6, 8, 11, 16]) for (const dof of [0, 0.5, 1, 2]) for (const dofRange of [0.3, 1, 3]) for (const tiltShift of [0, 0.4, 1]) for (const zoom of [0, 0.75, 1, 2.3, 3.6]) {
  const v = L.resolveMachiLens({ aperture, dof, dofRange, tiltShift }, zoom);
  out.push({ kind: 'lens', aperture, dof, dofRange, tiltShift, zoom, v: [r(v.focusBand), r(v.focusFalloff), r(v.coc), r(v.tilt)] });
}
for (let t = -10; t <= 400; t += 0.37) { const d = L.resolveMachiDrift(t); out.push({ kind: 'drift', t, v: [r(d.azimuth), r(d.elevation), r(d.zoom), r(d.panX), r(d.panZ)] }); }
// 时钟：确定性的时间戳序列，夹暂停、改速度、resync、回拨。
const c = new L.MachiClock();
let now = 1000, s = 11;
const rnd = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648; };
const seq = [];
for (let i = 0; i < 300; i++) {
  const op = Math.floor(rnd() * 20);
  if (op === 0) c.paused = !c.paused;
  else if (op === 1) c.timeScale = [0, 0.5, 1, 1.7][Math.floor(rnd() * 4)];
  else if (op === 2) c.resync();
  now += op === 3 ? -50 : op === 4 ? 2500 : rnd() * 140;
  c.tick(now);
  seq.push([i, op, now, c.paused, c.timeScale, r(c.elapsed), r(c.delta), r(c.realDelta)]);
}
out.push({ kind: 'clock', seq });
console.log(out.map(o => JSON.stringify(o)).join('\n'));
