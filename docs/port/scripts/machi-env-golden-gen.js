// 町景 environment.ts 金标：rolldown machi-board-golden-entry.ts -f cjs -o mc.js 后 node machi-env-golden-gen.js > machi_env_golden.txt。
require('./mc.js');
const E = MCE;
const r = v => Number(v.toPrecision(12));
const out = [];
for (let h = -3; h <= 27; h += 0.0625) out.push({ kind: 'daylightAt', h, v: r(E.resolveMachiDaylightAt(h)) });
for (const s of ['a', 'b', 'song:晴天', '12345', 'x'.repeat(30), '夜曲周杰伦', 'q1', 'q2', 'q3', 'q4', 'q5', 'q6', 'q7', 'q8']) out.push({ kind: 'weather', seed: s, v: E.resolveMachiWeatherKind('auto', s) });
const look = st => Object.fromEntries(Object.entries(E.resolveMachiEnvironmentLook(st)).map(([k, v]) => [k, r(v)]));
const st2 = st => Object.fromEntries(Object.entries(st).map(([k, v]) => [k, r(v)]));
const kinds = ['clear', 'rain', 'snow', 'fog'];
for (const from of kinds) for (const to of kinds) for (const [d0, d1] of [[0, 1], [0.45, 0], [1, 0.45]]) {
  let st = E.createMachiEnvironmentState(E.resolveMachiEnvironmentTarget(d0, from));
  const tgt = E.resolveMachiEnvironmentTarget(d1, to);
  const seq = [];
  for (let i = 0; i < 90; i++) { st = E.stepMachiEnvironment(st, tgt, i % 7 === 0 ? 0 : 0.05 + (i % 5) * 0.11); if (i % 3 === 0) seq.push([st2(st), look(st)]); }
  out.push({ kind: 'env', from, to, d0, d1, seq });
}
console.log(out.map(o => JSON.stringify(o)).join('\n'));
