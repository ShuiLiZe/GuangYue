// 用法：在 Folia 仓库旁建目录，entry.ts 里的 ../../folia 指向 Folia 源码；esbuild entry.ts --bundle --platform=node --format=cjs --outfile=hb.js 后 node 本脚本，生成 hanabi_text_golden.txt。
require('./hb.js');
const { A, B, buildHanabiText, estimateAdvance, HANABI_PROFILES, createRng } = HB;
const f = v => (typeof v === 'number' ? (Number.isNaN(v) ? 'NaN' : (Math.abs(v) < 5e-10 ? '0' : v.toPrecision(10))) : String(v));
const out = [];
// ── 假 Pixi ──
let allGraphics = [];
class Pt { constructor(){ this.x=0; this.y=0; } set(x,y){ this.x=x; this.y=(y===undefined?x:y); } }
class Container { constructor(){ this.children=[]; this.position=new Pt(); this.scale=new Pt(); this.scale.set(1,1); this.pivot=new Pt(); this.rotation=0; this.alpha=1; this.visible=true; this.parent=null; this.skew=new Pt(); this.anchor=new Pt(); }
  addChild(...cs){ cs.forEach(c=>{ this.children.push(c); c.parent=this; }); return cs[0]; } addChildAt(c,i){ this.children.splice(i,0,c); c.parent=this; return c; } getChildIndex(c){ return this.children.indexOf(c);} removeChild(c){ this.children=this.children.filter(x=>x!==c);} destroy(){} }
class Graphics extends Container { constructor(){ super(); this.ops=[]; this.tint=0xffffff; allGraphics.push(this); }
  _o(k,a){ this.ops.push([k,...a]); return this; }
  roundRect(...a){return this._o('RR',a);} circle(...a){return this._o('C',a);} rect(...a){return this._o('R',a);} poly(p){return this._o('P',p);}
  moveTo(...a){return this._o('M',a);} lineTo(...a){return this._o('L',a);} arc(...a){return this._o('A',a);} closePath(){return this;}
  fill(o){ this.ops.push(['F', o&&o.color!==undefined?o.color:-1, o&&o.alpha!==undefined?o.alpha:1]); return this;} stroke(o){ this.ops.push(['S', o.width]); return this;} clear(){ this.ops=[]; return this;} }
class Sprite extends Container { constructor(t){ super(); this.texture=t; this.tint=0xffffff; this.blendMode='normal'; } }
const pixi = { Container, Graphics, Sprite };
// ── 用例 ──
const palette = { ink: 0x1a1030, paper: 0xfff6ea, blocks: [0xe8344e, 0x2f6fe0, 0xf28c28], sparks: [0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff], accent: 0xff5d8f, glow: 0xffe08a };
const mkLine = (segs, t0, per) => { const words=[]; let t=t0; segs.forEach(s=>{ const d = s.trim().length? per*[...s].length : 0.0; words.push({text:s,startTime:t,endTime:t+d}); t+=d; }); return { fullText: segs.join(''), words, startTime:t0, endTime:t, wordSegments: segs }; };
const lines = [
  mkLine(['星河','与','风','一起','落在','很远','很远','的','地方'], 10, 0.22),
  mkLine(['我们','慢慢','走','回去','，'], 10, 0.3),
  mkLine(['Hold','　','me','　','tonight'], 10, 0.25),
];
const cases = [];
const blocksList = ['none','tile','band','slant','double','pill','split','ticket','marker','outline','circle','stair'];
const enters = A.ENTER_FX, sings = A.SING_FX, exits = A.EXIT_FX;
const layouts = ['center','left','right','stagger','diagonal','scatter','vertical','vertical-split','arc'];
for (let i = 0; i < 24; i++) {
  cases.push({ id: i, line: i % 3, spec: { layout: layouts[i % 9], heroSize: 0.08 + (i % 4) * 0.02, emphasis: (i % 5) * 0.2, tilt: (i % 3 - 1) * 0.12,
    block: blocksList[i % 12], enter: enters[i % 17], sing: sings[i % 9], exit: exits[i % 7], stagger: 0.03 + (i % 3) * 0.02, outline: i % 4 === 1, hardShadow: i % 3 !== 2 } });
}
// 再补几组：outline 弧排带、ticket 竖排等
cases.push({ id: 24, line: 0, spec: { layout: 'arc', heroSize: 0.1, emphasis: 0.4, tilt: 0, block: 'band', enter: 'rise', sing: 'bounce', exit: 'ascend', stagger: 0.045, outline: false, hardShadow: true } });
cases.push({ id: 25, line: 0, spec: { layout: 'center', heroSize: 0.1, emphasis: 0.45, tilt: 0, block: 'outline', enter: 'wipe', sing: 'underline', exit: 'slide', stagger: 0.04, outline: true, hardShadow: false } });
cases.push({ id: 26, line: 1, spec: { layout: 'vertical', heroSize: 0.11, emphasis: 0.5, tilt: 0, block: 'ticket', enter: 'type', sing: 'glow', exit: 'dissolve', stagger: 0.05, outline: false, hardShadow: true } });
const W = 800, H = 360;
cases.forEach(c => {
  allGraphics = [];
  const line = lines[c.line];
  const opts = { pixi, width: W, height: H, resolution: 1, theme: {}, line, regionWidth: W * 0.7, regionHeight: H * 0.4, spec: c.spec, palette, seed: `case${c.id}`,
    enterAt: 9.5, exitAt: 13.2, exitDuration: 0.7, motion: 1 + (c.id % 3) * 0.2, blockOpacity: 0.95 };
  const atlasDeps = {
    measure: estimateAdvance,
    buildAtlas: (_p, o) => {
      const mk = (ch, px) => ({ px, ch, fill: { ch }, fat: o.outline ? { ch, fat: 1 } : null, glow: o.glow ? { ch, glow: 1 } : null, halves: o.halves ? [{ ch, h: 1 }, { ch, h: 2 }] : null });
      const scr = o.scramble ? o.scramble.chars.map(ch => mk(ch, o.scramble.px)) : [];
      return { entry: ch => mk(ch, o.chars.get(ch)), scramble: scr, destroy() {} };
    },
  };
  const text = buildHanabiText(opts, atlasDeps);
  const masked = text.glyphs.children[0].children[0];
  const [shadowL, outlineL, haloL, fxL, mainL] = masked.children;
  out.push(`CASE ${c.id} ${c.line} ${c.spec.layout} ${c.spec.heroSize} ${c.spec.emphasis} ${c.spec.tilt} ${c.spec.block} ${c.spec.enter} ${c.spec.sing} ${c.spec.exit} ${c.spec.stagger} ${c.spec.outline} ${c.spec.hardShadow} ${opts.motion} ${mainL.children.length} ${allGraphics.length}`);
  const times = [9.4, 9.55, 9.7, 9.9, 10.05, 10.3, 10.6, 11.0, 11.4, 12.0, 12.7, 13.25, 13.45, 13.7, 13.95];
  times.forEach(t => {
    text.update(t);
    const sp = (tag, k, s) => { if (!s || !s.visible) return; out.push(`${tag} ${f(t)} ${k} ${f(s.position.x)} ${f(s.position.y)} ${f(s.rotation)} ${f(s.scale.x)} ${f(s.scale.y)} ${f(s.skew.x)} ${f(s.alpha)} ${s.tint} ${s.texture.ch}`); };
    mainL.children.forEach((s, k) => sp('M', k, s));
    shadowL.children.forEach((s, k) => sp('S', k, s));
    outlineL.children.forEach((s, k) => sp('O', k, s));
    haloL.children.forEach((s, k) => sp('H', k, s));
    fxL.children.forEach((s, k) => sp('X', k, s));
    allGraphics.forEach((g, k) => { if (!g.visible || !g.parent || g.parent === null) return; if (g === allGraphics[k] && g.ops.length===0 && g.parent && g.parent.mask===g) return;
      const nums = g.ops.filter(op => op[0]==='P').map(op => op.slice(1).map(f).join(',')).join(';');
      out.push(`G ${f(t)} ${k} ${f(g.position.x)} ${f(g.position.y)} ${f(g.rotation)} ${f(g.scale.x)} ${f(g.scale.y)} ${f(g.alpha)} ${g.tint} ${nums}`); });
    const anchors = text.anchors(t);
    anchors.forEach((a, k) => { if (!a.blank && a.alpha > 0) out.push(`A ${f(t)} ${k} ${f(a.x)} ${f(a.y)} ${f(a.size)} ${f(a.alpha)}`); });
  });
});
// ── 纯函数 ──
const ctx = (motion, time) => ({ index: 2, count: 7, rand: [0.31, 0.77, 0.12, 0.58], toCenterX: 1.7, toCenterY: -0.6, dirX: 0.8, dirY: 0.6, time, motion, slideDistance: 9 });
const pose = p => [p.dx, p.dy, p.scaleX, p.scaleY, p.rotation, p.alpha, p.skew, p.scramble, p.rgbSplit, p.blur, p.split, p.backside ? 1 : 0, p.reveal].map(f).join(' ');
[0, 0.03, 0.2, 0.37, 0.5, 0.66, 0.79, 0.95, 1].forEach(p => {
  enters.forEach(fx => out.push(`EN ${fx} ${p} ${pose(A.enterPose(fx, p, ctx(1.3, p * 0.7 + 0.013)))}`));
  exits.forEach(fx => out.push(`EX ${fx} ${p} ${pose(A.exitPose(fx, p, ctx(0.8, p * 0.6 + 0.021)))}`));
});
[-0.1, 0, 0.02, 0.07, 0.15, 0.29, 0.33, 0.5, 0.71, 1.1].forEach(u => sings.forEach(fx => {
  const s = A.singPose(fx, u, 0.45, 0.37, 1.25); out.push(`SG ${fx} ${u} ${[s.dx, s.dy, s.scale, s.scaleX, s.scaleY, s.rotation, s.flash, s.glow, s.color].map(f).join(' ')}`);
}));
enters.forEach((fx, i) => {
  const r = createRng(`ord${i}`); const ranks = A.enterOrder(fx, 9, r);
  out.push(`OR ${fx} ${ranks.map(f).join(' ')}`);
  const withBlank = [...ranks.slice(0, 4), -1, ...ranks.slice(4)];
  [[0.04, 12], [0.06, 10.4], [0.05, 10.05]].forEach(([st, dl]) => { const s = A.scheduleEnter(fx, withBlank, 10, st, dl); out.push(`SE ${fx} ${st} ${dl} ${f(s.duration)} ${s.start.map(f).join(' ')}`); });
});
[[0.7, [0, 1, 2, -1, 3, 4]], [0.3, [3, 0, 2, 1]], [1.2, [0]]].forEach(([d, r]) => { const s = A.scheduleExit(r, 20, d); out.push(`SX ${d} ${r.join(',')} ${s.start.map(f).join(' ')} | ${s.end.map(f).join(' ')}`); });
[0, 0.02, 0.1, 0.3, 0.5].forEach(t => { const s = A.stampShake(10 + t, [10, 10.08, 10.2], 4.5); out.push(`SH ${t} ${f(s.x)} ${f(s.y)}`); });
for (let i = 0; i < 12; i++) out.push(`HS ${i * 37 - 50} ${i * 1013 + 7} ${f(A.hash01(i * 37 - 50, i * 1013 + 7))}`);
['none','tile','band','slant','double','pill','split','ticket','marker','outline','circle','stair'].forEach(s => [false, true].forEach(h => { const p = B.blockLayoutParams(s, h); out.push(`BP ${s} ${h} ${[p.tracking,p.wordGap,p.lineHeight,p.minAdvance,p.stairStep,p.padding].map(f).join(' ')}`); }));
[0x123456, 0xff8000, 0xe8344e].forEach(c => [0.35, 0.42].forEach(a => out.push(`DK ${c} ${a} ${B.darken(c, a)}`)));
require('fs').writeFileSync('hanabi_text_golden.txt', out.join('\n') + '\n');
console.log(out.length, require('fs').statSync('hanabi_text_golden.txt').size);
