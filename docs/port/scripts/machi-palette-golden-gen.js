// 町景 palette.ts 金标：rolldown machi-board-golden-entry.ts -f cjs -o mc.js 后 node machi-palette-golden-gen.js > machi_palette_golden.txt。
require('./mc.js');
const P = MCP;
// 确定性的主题集合：暗 / 亮背景、彩色 / 偏灰、带关键词色。
let s = 7;
const rnd = () => { s = (s * 1103515245 + 12345) % 2147483648; return s / 2147483648; };
const hex = () => '#' + [0, 0, 0].map(() => Math.floor(rnd() * 256).toString(16).padStart(2, '0')).join('');
const themes = [
  { backgroundColor: '#0b0d12', primaryColor: '#f4f4f2', secondaryColor: '#9aa0ab', accentColor: '#ff5d8f' },
  { backgroundColor: '#ffffff', primaryColor: '#1a1a1a', secondaryColor: '#777777', accentColor: '#888888' },
  { backgroundColor: '#f7efe2', primaryColor: '#3b2b1a', secondaryColor: '#c47b2a', accentColor: '#2f6fe0', wordColors: [{ color: '#e8344e' }, { color: '#20c997' }] },
  { backgroundColor: '#1d2a44', primaryColor: '#cfe3ff', secondaryColor: '#6f8fbf', accentColor: '#6f8fbf' },
  { backgroundColor: '#202020', primaryColor: '#eeeeee', secondaryColor: '#999999', accentColor: '#aaaaaa' },
  { backgroundColor: '#3a1030', primaryColor: '#ffd6f0', secondaryColor: '#ff7ac8', accentColor: '#ffe14d', wordColors: [{ color: '#ffffff' }, { color: '#4dd2ff' }, { color: '#ff3355' }] },
];
for (let i = 0; i < 40; i++) {
  const t = { backgroundColor: hex(), primaryColor: hex(), secondaryColor: hex(), accentColor: hex() };
  if (i % 3 === 0) t.wordColors = [{ color: hex() }, { color: hex() }];
  themes.push(t);
}
const seeds = ['a', 'song:晴天', '12345', '夜曲'];
const out = [];
themes.forEach((t, ti) => seeds.forEach((seed, si) => {
  if ((ti + si) % 2 === 1 && ti >= 6) return;
  const p = P.resolveMachiPalette(t, seed);
  const { art, ...rest } = p;
  out.push({ theme: { ...t, wordColors: (t.wordColors ?? []).map(w => w.color) }, seed, palette: rest });
}));
const vv = [];
for (const c of ['#000000', '#ffffff', '#ff0000', '#123456', '#abcdef', '#7f7f80', '#08f']) vv.push([c, P.vivid(c), P.vivid(c, 0.85, 0.58, 0.66), P.vivid(c, 0.45, 0.45, 0.7)]);
out.push({ vivid: vv });
console.log(out.map(o => JSON.stringify(o)).join('\n'));
