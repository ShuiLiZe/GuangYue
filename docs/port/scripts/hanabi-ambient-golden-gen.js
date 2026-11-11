// 用法同 hanabi-text-golden-gen.js；生成 hanabi_ambient_golden.txt。
require('./hb.js');
const f = v => (Math.abs(v) < 5e-10 ? '0' : v.toPrecision(10));
class Pt { constructor(){ this.x=0; this.y=0; } set(x,y){ this.x=x; this.y=(y===undefined?x:y); } }
class Container { constructor(){ this.children=[]; this.position=new Pt(); this.anchor=new Pt(); this.rotation=0; this.alpha=1; this.visible=true; }
  addChild(...cs){ cs.forEach(c=>this.children.push(c)); return cs[0]; } destroy(){} }
class Sprite extends Container { constructor(t){ super(); this.texture=t; this.tint=0xffffff; this.width=0; this.height=0; } }
class Rectangle { constructor(x,y,w,h){ this.x=x; this.y=y; this.width=w; this.height=h; } }
class Texture { constructor(o){ this.o=o; this.name='smoke'; } destroy(){} }
const pixi = { Container, Sprite, Rectangle, Texture };
const tex = n => ({ name: n, frame: new Rectangle(0, 0, 64, 64), source: {} });
const textures = { glow: tex('glow'), dot: tex('dot'), star4: tex('star4'), streak: tex('streak') };
const palette = { ink: 0x1a1030, paper: 0xfff6ea, blocks: [0xe8344e, 0x2f6fe0, 0xf28c28], sparks: [0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff], accent: 0xff5d8f, glow: 0xffe08a };
const out = [];
[[1, 0.2, 'p0'], [1.6, 0.35, 'p3'], [0.5, 0.08, 'p7']].forEach(([density, rate, seed], c) => {
  const W = 800, H = 360;
  const amb = HB.createAmbient({ pixi, textures, width: W, height: H, seed, palette, startTime: 20, endTime: 46, density, distantBursts: rate });
  const [smokeL, starL, distL, flashL] = amb.back.children; const [bokehL, emberL] = amb.front.children;
  out.push(`AMB ${c} ${density} ${rate} ${seed} ${starL.children.length} ${smokeL.children.length} ${emberL.children.length} ${bokehL.children.length}`);
  [20.1, 22.7, 25.3, 31.9, 38.4, 44.2].forEach((t, i) => {
    const rise = i * 173.5 - 40;
    amb.update(t, rise);
    const dump = (tag, L) => L.children.forEach((s, k) => { if (s.visible) out.push(`${tag} ${f(t)} ${f(rise)} ${k} ${f(s.position.x)} ${f(s.position.y)} ${f(s.width)} ${f(s.alpha)} ${s.tint}`); });
    dump('st', starL); dump('sm', smokeL); dump('em', emberL); dump('bo', bokehL);
    let n = 0;
    [distL, flashL].forEach((L, p) => L.children.forEach((s, k) => { if (s.visible) { n++; if (k % 7 === 0) out.push(`d${p} ${f(t)} ${f(rise)} ${k} ${f(s.position.x)} ${f(s.position.y)} ${f(s.width)} ${f(s.height)} ${f(s.rotation)} ${f(s.alpha)} ${s.tint}`); } }));
    out.push(`dn ${f(t)} ${n}`);
  });
});
require('fs').writeFileSync('hanabi_ambient_golden.txt', out.join('\n') + '\n');
console.log(out.length, out.filter(l=>l.startsWith('dn')).join(' | '));
