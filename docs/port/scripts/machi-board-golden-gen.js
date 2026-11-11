// 町景 director + boardLayout 金标：node gen.js > machi_board_golden.txt（每行一个 JSON）。
// 用法：entry.ts（import Folia v0.7.25 machi/engine/director.ts、boardLayout.ts）用 rolldown 打成 mc.js（cjs）。
require('./mc.js');
const M = MC;
const r = v => (typeof v === 'number' ? (Number.isFinite(v) ? Number(v.toPrecision(12)) : String(v)) : v);
// 假测量：参考字号 100 px；中日韩 / 全角 100，空格 28，ASCII 字母数字 56，其它 40（与 Kotlin 测试里的 fakeMeasure 一致）。
const cw = ch => /[\u3000-\u9fff\uac00-\ud7af\uff00-\uffef]/.test(ch) ? 100 : ch === ' ' ? 28 : /[A-Za-z0-9]/.test(ch) ? 56 : 40;
const measure = text => { let s = 0; for (const ch of text) s += cw(ch); return s; };
const W = (text, s, e, syl) => ({ text, startTime: s, endTime: e, ...(syl ? { syllables: syl } : {}) });
const mk = (fullText, s, e, words, extra = {}) => ({ fullText, startTime: s, endTime: e, words, ...extra });
const even = (text, s, e) => { const cs = [...text]; const d = (e - s) / cs.length; return cs.map((c, i) => W(c, s + i * d, s + (i + 1) * d)); };
const lines = [
  mk('故事的小黄花', 1.0, 3.2, even('故事的小黄花', 1.0, 3.0)),
  mk('从出生那年就飘着', 3.5, 6.0, [W('从出生', 3.5, 4.4), W('那年', 4.4, 5.0), W('就飘着', 5.0, 5.9)], { translation: 'Since the year I was born' }),
  mk('......', 6.2, 12.0, []),
  mk('童年的荡秋千，随记忆一直晃到现在', 12.1, 16.5, even('童年的荡秋千，随记忆一直晃到现在', 12.1, 16.4), { isChorus: true }),
  mk('Re So So Si Do Si La', 16.8, 19.6, [W('Re ', 16.8, 17.2), W('So ', 17.2, 17.6), W('So ', 17.6, 18.0), W('Si ', 18.0, 18.4), W('Do ', 18.4, 18.8), W('Si ', 18.8, 19.1), W('La', 19.1, 19.5)], { songPart: 'Chorus' }),
  mk('吹着前奏望着天空 I hold on', 19.8, 23.0, [W('吹着前奏', 19.8, 20.8, [{ text: '吹', startTime: 19.8, endTime: 20.0 }, { text: '着', startTime: 20.0, endTime: 20.3 }, { text: '前', startTime: 20.3, endTime: 20.5 }, { text: '奏', startTime: 20.5, endTime: 20.8 }]), W('望着天空', 20.8, 21.8), W(' ', 21.8, 21.8), W('I ', 21.8, 22.1), W('hold ', 22.1, 22.5), W('on', 22.5, 22.9)], { songPart: '副歌' }),
  mk('', 23.5, 24.0, []),
  mk('我想就这样牵着你的手不放开，爱能不能够永远单纯没有悲哀', 30.0, 36.0, even('我想就这样牵着你的手不放开，爱能不能够永远单纯没有悲哀', 30.0, 35.8)),
  mk('啊', 40.0, 40.4, even('啊', 40.0, 40.3)),
];
const out = [];
const times = [];
for (let t = -1; t <= 45; t += 0.37) times.push(Number(t.toFixed(4)));
times.push(3.08, 3.38, 5.8, 6.1, 12.0, 12.05, 19.7, 25.4, 25.5, 25.6, 26.0, 29.9, 38.4, 38.5, 43.2);
const ft = l => ({ fullText: l.fullText, startTime: l.startTime, endTime: l.endTime, translation: l.translation ?? null, isChorus: !!l.isChorus, songPart: l.songPart ?? null,
  words: l.words.map(w => ({ text: w.text, startTime: w.startTime, endTime: w.endTime, syllables: w.syllables ?? [] })) });
out.push({ kind: 'lines', lines: lines.map(ft) });
const song = { title: '晴天', artist: '周杰伦' };
for (const t of times) {
  const target = M.resolveMachiBoardTarget(lines, t);
  out.push({ kind: 'target', t, target: target.kind === 'card' ? -1 : target.index, chorus: M.resolveMachiChorus(lines, target), ticker: M.resolveMachiTickerText(target, lines, song, 'Untitled') });
}
const lt = M.buildMachiLightningTimes(lines);
out.push({ kind: 'lightningTimes', times: lt.map(r) });
for (let t = 10; t <= 22; t += 0.023) out.push({ kind: 'lightning', t, v: r(M.resolveMachiLightning(lt, t)) });
for (const seed of ['abc', 'song:晴天', 12345]) for (const rate of [1, 0.5, 2.2]) {
  const vs = []; for (let t = 0; t < 60; t += 0.031) vs.push(r(M.resolveMachiFlicker(t, seed, rate)));
  out.push({ kind: 'flicker', seed: String(seed), rate, step: 0.031, vs });
}
{ const f = new M.MachiAudioFollower(); const raws = [0, 0.5, 1, 0.2, 0, 120, 255, 30, 0.9, 0]; const vs = [];
  raws.forEach((raw, i) => { for (let k = 0; k < 5; k++) vs.push(r(f.step(raw, k === 4 ? 0 : 0.016 + 0.01 * k))); });
  out.push({ kind: 'audio', raws, vs }); }
const boxes = [{ width: 1024, height: 256, paddingX: 0.06, referencePx: 100 }, { width: 512, height: 512, paddingX: 0.1, referencePx: 100 }, { width: 900, height: 120, paddingX: 0.04, referencePx: 64 }];
for (const [bi, box] of boxes.entries()) {
  for (const [li, line] of lines.entries()) {
    const L = M.layoutMachiBoardLine(line, box, measure);
    const reveal = [];
    for (let t = line.startTime - 0.3; t <= line.endTime + 0.5; t += 0.07) reveal.push([t, M.resolveMachiBoardReveal(L, t).map(r)]);
    out.push({ kind: 'layout', box: bi, line: li, fontPx: r(L.fontPx), width: L.width, height: L.height,
      rows: L.rows.map(row => ({ fontPx: r(row.fontPx), centerY: r(row.centerY), top: r(row.top), bottom: r(row.bottom),
        glyphs: row.glyphs.map(g => [g.text, g.offset, g.length, r(g.x0), r(g.x1), r(g.startTime), r(g.endTime)]) })), reveal });
  }
  for (const [title, sub] of [['晴天', '周杰伦'], ['A Very Long English Song Title Indeed', 'Some Artist'], ['夜曲', '']]) {
    const L = M.layoutMachiBoardCard(title, sub, box, measure);
    out.push({ kind: 'card', box: bi, title, subtitle: sub, fontPx: r(L.fontPx),
      rows: L.rows.map(row => ({ fontPx: r(row.fontPx), centerY: r(row.centerY), top: r(row.top), bottom: r(row.bottom),
        glyphs: row.glyphs.map(g => [g.text, g.offset, g.length, r(g.x0), r(g.x1), r(g.startTime), r(g.endTime)]) })) });
  }
}
console.log(out.map(o => JSON.stringify(o)).join('\n'));
