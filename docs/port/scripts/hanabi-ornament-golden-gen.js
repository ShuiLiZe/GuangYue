// 入口：hanabi-golden-entry.ts 末尾加 createOrnament（见该文件）。
// 用法同 hanabi-text-golden-gen.js；生成 hanabi_ornament_golden.txt：假 Pixi 跑真的 createOrnament（21 种 × 7 种落点 + 竖排 / 数量变体），
// 合成的字锚点（与 HanabiOrnamentTest 同式），逐节点比可见性 / 位置 / 尺寸 / 旋转 / 透明度 / 色；逐帧重画的线比点数与坐标和。
require('./hb.js');
const f = v => (Math.abs(v) < 5e-10 ? '0' : v.toPrecision(10));
class Pt { constructor(){ this.x=0; this.y=0; } set(x,y){ this.x=x; this.y=(y===undefined?x:y); } }
class Container { constructor(){ this.children=[]; this.position=new Pt(); this.scale=new Pt(); this.scale.set(1,1); this.anchor=new Pt(); this.rotation=0; this.alpha=1; this.visible=true; this.tint=0xffffff; }
  addChild(...cs){ cs.forEach(c=>this.children.push(c)); return cs[0]; } destroy(){} on(){} }
class Sprite extends Container { constructor(t){ super(); this.texture=t; this.width=0; this.height=0; this.kind='s'; } }
class Shape { constructor(){ this.n=0; this.sx=0; this.sy=0; }
  _p(x,y){ this.n++; this.sx+=x; this.sy+=y; return this; }
  moveTo(x,y){ return this._p(x,y);} lineTo(x,y){ return this._p(x,y);} arc(){ return this;} circle(){ return this;} ellipse(){ return this;} rect(){ return this;}
  closePath(){ return this;} stroke(){ return this;} fill(){ return this;} clear(){ this.n=0; this.sx=0; this.sy=0; return this;} destroy(){} }
class GraphicsContext extends Shape {}
class Graphics extends Container { constructor(ctx){ super(); this.kind='g'; this.s=ctx||new Shape(); }
  moveTo(x,y){ this.s.moveTo(x,y); return this;} lineTo(x,y){ this.s.lineTo(x,y); return this;} arc(){ return this;} circle(){ return this;} ellipse(){ return this;} rect(){ return this;}
  closePath(){ return this;} stroke(){ return this;} fill(){ return this;} clear(){ this.s.clear(); return this;} }
class Text extends Container { constructor(o){ super(); this.kind='t'; this.text=o.text; } }
const pixi = { Container, Sprite, Graphics, GraphicsContext, Text };
const textures = {}; ['dot','glow','star4','streak','ring','square','triangle','petal'].forEach(n => textures[n] = { name: n });
const palette = { ink: 0x1a1030, paper: 0xfff6ea, blocks: [0xe8344e, 0x2f6fe0, 0xf28c28], sparks: [0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff], accent: 0xff5d8f, glow: 0xffe08a };
// 合成字锚点：6 个字（第 4 个是空白），横排或竖排，带一点漂移。
const anchorsAt = vertical => time => Array.from({ length: 6 }, (_, i) => {
  const along = -150 + 60 * i + 4 * Math.sin(time * 1.3 + i);
  const across = 10 * Math.cos(time + i * 0.5);
  return { char: i === 3 ? ' ' : '花火夏夜空光'[i], x: vertical ? across : along, y: vertical ? along : across, size: 48 + 2 * i,
    sungAt: 10.3 + 0.35 * i, sungEnd: 10.6 + 0.35 * i, landedAt: 10 + 0.1 * i, alpha: Math.min(1, Math.max(0, (time - 9.9) / 0.4)), blank: i === 3 };
});
const KINDS = ['ring','spokes','sparkle','orbit','chevrons','brackets','cross','zigzag','wave','halftone','confetti','shards','dash-circle','squares','streaks','petals','stamp','rays','underline','dots-line','glints'];
const PLACES = ['glyphs','around','behind','corners','line-start','line-end','screen'];
const COLORS = ['spark','accent','block','paper','glow'];
const cases = [];
KINDS.forEach((kind, ki) => PLACES.forEach((placement, pi) => cases.push({ kind, placement, density: 0.6, scale: 1, color: COLORS[(ki + pi) % 5], delay: 0, vertical: false, amount: 1 })));
KINDS.forEach((kind, ki) => cases.push({ kind, placement: PLACES[ki % 7], density: 0.3 + (ki % 3) * 0.3, scale: 1.2, color: COLORS[ki % 5], delay: -0.2, vertical: true, amount: 1.6 }));
KINDS.forEach((kind, ki) => cases.push({ kind, placement: 'glyphs', density: 0.5, scale: 0.8, color: 'spark', delay: 0.1, vertical: false, amount: 0 }));
const TIMES = [9.95, 10.05, 10.2, 10.5, 11.0, 12.3, 14.7, 16.2, 16.5, 16.79];
const out = [];
cases.forEach((c, ci) => {
  const spec = { kind: c.kind, placement: c.placement, density: c.density, scale: c.scale, color: c.color, delay: c.delay, layer: 'back' };
  const bounds = c.vertical ? { x: -30, y: -160, width: 60, height: 340 } : { x: -160, y: -30, width: 340, height: 60 };
  const o = HB.createOrnament(spec, { pixi, textures, width: 800, height: 450, seed: `s${ci}`, palette, bounds, anchorsAt: anchorsAt(c.vertical), enterAt: 10, exitAt: 16, exitDuration: 0.8, amount: c.amount });
  const nodes = [];
  const walk = n => n.children.forEach(ch => { nodes.push(ch); if (ch.kind === undefined) walk(ch); });
  walk(o.view);
  out.push(`C ${ci} ${c.kind} ${c.placement} ${c.density} ${c.scale} ${c.color} ${c.delay} ${c.vertical ? 1 : 0} ${c.amount} ${nodes.length}`);
  const sample = nodes.length > 40 ? 5 : 1;
  TIMES.forEach(t => {
    o.update(t);
    out.push(`T ${f(t)} ${o.view.visible ? 1 : 0}`);
    if (!o.view.visible) return;
    let vis = 0;
    nodes.forEach((n, k) => {
      if (!n.visible) return;
      vis++;
      if (k % sample !== 0) return;
      const kind = n.kind || 'c';
      const w = kind === 's' ? n.width : n.scale.x, h = kind === 's' ? n.height : n.scale.y;
      let extra = '';
      if (kind === 'g' && !n.s.shared) extra = ` ${n.s.n} ${f(n.s.sx)} ${f(n.s.sy)}`;
      out.push(`${kind} ${k} ${f(n.position.x)} ${f(n.position.y)} ${f(w)} ${f(h)} ${f(n.rotation)} ${f(n.alpha)} ${n.tint}${extra}`);
    });
    out.push(`V ${vis}`);
  });
});
require('fs').writeFileSync('hanabi_ornament_golden.txt', out.join('\n') + '\n');
console.log(out.length, cases.length);
