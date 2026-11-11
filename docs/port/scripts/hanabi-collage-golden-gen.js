// 花火 PV 拼贴金标：真的 planShotCollage（编排）+ createShotCollage（假 Pixi，逐件逐时刻姿态）。
// 用法：hanabi-collage-golden-entry.ts（../../folia 指向 Folia v0.7.25 源码）用 rolldown/esbuild 打成 hb.js（cjs），node 本脚本 → hanabi_collage_golden.txt（每行一个 JSON）。
require('./hb.js');
const { planShotCollage, createShotCollage, createRng, CANDY } = HB;
class Pt { constructor(){ this.x=0; this.y=0; } set(x,y){ this.x=x; this.y=(y===undefined?x:y); } }
class Container { constructor(){ this.children=[]; this.position=new Pt(); this.scale=new Pt(); this.scale.set(1,1); this.anchor=new Pt(); this.rotation=0; this.alpha=1; this.visible=true; this.tint=0xffffff; this.tilePosition=new Pt(); }
  get x(){ return this.position.x; } set x(v){ this.position.x=v; } get y(){ return this.position.y; } set y(v){ this.position.y=v; }
  addChild(...cs){ cs.forEach(c=>this.children.push(c)); return cs[0]; } destroy(){} }
class Sprite extends Container { constructor(t){ super(); this.texture=t||{width:1,height:1}; this.width=this.texture.width; this.height=this.texture.height; this.kind='s'; } }
class TilingSprite extends Container { constructor(o){ super(); this.texture=o.texture; this.width=o.width; this.height=o.height; this.kind='t'; } }
const pixi = { Container, Sprite, TilingSprite, Texture: { WHITE: { width: 1, height: 1 } } };
const TEX = { strip: (style) => ({ width: style === 'film' ? 70 : style === 'cut' ? 13 : 37, height: 20 }), scissors: () => ({ width: 40, height: 40 }),
  echo: (t, c, s, axis) => ({ width: axis === 'x' ? 210 : 60, height: 50 }), sticker: () => ({ width: 112, height: 112 }), tape: () => ({ width: 160, height: 48 }),
  label: () => ({ width: 80, height: 20 }), sound: () => ({ width: 200, height: 70 }), icon: () => ({ width: 40, height: 40 }), pattern: () => ({ width: 24, height: 24 }) };
const textures = { dot: { width: 8, height: 8 } };
const r = v => (typeof v === 'number' ? (Number.isFinite(v) ? Number(v.toPrecision(12)) : String(v)) : v);
const FAMILIES = ['uchiage', 'kiku', 'botan', 'yanagi', 'senrin', 'hoshikuzu', 'obi', 'senko', 'senkohanabi', 'shakudama', 'other'];
const BLOCKS = ['pill', 'none', 'outline', 'marker', 'ticket'];
const PREVS = [null, 'strips', 'echo', 'window', 'stickerbomb', 'scrapbook', 'quiet'];
const palette = { ink: 0x1a1030, paper: 0xfff6ea, blocks: [0xe8344e, 0x2f6fe0, 0xf28c28], sparks: [0xffd166, 0x06d6a0, 0xef476f, 0x118ab2, 0xffffff], accent: 0xff5d8f, glow: 0xffe08a };
const cases = [];
let n = 0;
const mk = (o) => {
  const rng = createRng(`case${n}`);
  const unit = o.unit ?? 360;
  const screen = { x: -400 + rng() * 60, y: -180 + rng() * 40, width: 800, height: 360 };
  const wide = o.wide ?? (rng() < 0.5);
  const bounds = wide ? { x: -300, y: -40, width: 600, height: 90 } : { x: -90, y: -110, width: 150, height: 230 };
  const bursts = [{ x: -60 + rng() * 120, y: -150, at: 10.1, kind: 'kiku', main: true, radius: 90 }, { x: 200, y: -120, at: 10.4, kind: 'botan', main: true, radius: 70 },
    { x: -220, y: -100, at: 11.0, kind: 'ring', main: true, radius: 60 }, { x: 10, y: 0, at: 10.5, kind: 'botan', main: false, radius: 20 }];
  const launches = [{ fromX: -50, fromY: 300, toX: -60, toY: -150, at: 9.0, duration: 1.1 }, { fromX: 180, fromY: 320, toX: 200, toY: -120, at: 9.3, duration: 1.1 }, { fromX: 0, fromY: 320, toX: 10, toY: -100, at: 9.6, duration: 1.1 }];
  const input = { seed: `s${n}`, family: o.family, mood: 'calm', isBridge: !!o.bridge, block: o.block, shotNumber: n % 13, bounds, screen, drift: o.drift ?? 30,
    unit, palette, enterAt: 10, exitAt: 14, exitDuration: 0.8, start: 9.5, end: 15, keyword: o.keyword, motifs: o.motifs ?? ['HANABI', '花火'],
    bursts: o.noBursts ? [] : bursts, launches, amount: o.amount, previous: o.previous, opening: o.opening ?? null };
  n++;
  return input;
};
FAMILIES.forEach((family, fi) => {
  [1, 0.5, 2, 0.8].forEach((amount, ai) => {
    cases.push(mk({ family, amount, block: BLOCKS[(fi + ai) % BLOCKS.length], previous: PREVS[(fi * 3 + ai) % PREVS.length], keyword: ai % 2 === 0 ? '星河' : null, wide: ai % 2 === 1 }));
  });
  cases.push(mk({ family, amount: 0, block: 'pill', previous: null, keyword: null }));
  cases.push(mk({ family, amount: 1, block: 'pill', previous: null, keyword: 'LOVE SONG', bridge: true }));
});
cases.push(mk({ family: 'kiku', amount: 1, block: 'pill', previous: null, keyword: null, bridge: true, opening: { title: '十一月的萧邦', artist: '周杰伦' } }));
cases.push(mk({ family: 'kiku', amount: 1.6, block: 'pill', previous: null, keyword: null, bridge: true, opening: { title: 'Lantern Tide', artist: null } }));
cases.push(mk({ family: 'botan', amount: 1, block: 'none', previous: null, keyword: null, motifs: ['夜曲', 'HANABI', '花火'], noBursts: true }));
const TIMES = [9.0, 9.82, 9.9, 10.05, 10.2, 10.5, 11.1, 12.0, 13.9, 14.2, 14.5, 14.9, 15.5];
const out = [];
cases.forEach(input => {
  const plan = planShotCollage({ ...input, screenAt: t => ({ x: input.screen.x, y: input.screen.y - input.drift * (t - input.enterAt), width: input.screen.width, height: input.screen.height }) });
  const items = plan.items.map(it => { const o = {}; for (const [k, v] of Object.entries(it)) o[k] = r(v); return o; });
  out.push(JSON.stringify({ c: { ...input, palette: undefined }, recipe: plan.recipe, items }));
  plan.items.forEach((item, k) => {
    const cache = new Proxy({}, { get: (_, name) => TEX[name] });
    const view = createShotCollage(pixi, { recipe: plan.recipe, items: [item] }, cache, textures);
    const root = (view.back.children[0] || view.front.children[0]);
    const poses = TIMES.map(t => {
      view.update(t);
      const p = { t };
      if (item.kind === 'strip') {
        const kids = root.children; const band = kids.find(c => c.kind === 't'); const blade = kids.find(c => c.kind === 's' && c.texture.width === 40);
        p.v = root.visible ? 1 : 0;
        if (root.visible) { p.bx = r(band.position.x); p.bw = r(band.width); p.tx = r(band.tilePosition.x); if (blade) { p.lv = blade.visible ? 1 : 0; if (blade.visible) { p.lx = r(blade.position.x); p.lsx = r(blade.scale.x); p.lsy = r(blade.scale.y); p.la = r(blade.alpha); } } }
      } else if (item.kind === 'echo') {
        p.v = root.visible ? 1 : 0; if (root.visible) { p.a = r(root.alpha); p.s = r(root.scale.x); p.tx = r(root.tilePosition.x); p.ty = r(root.tilePosition.y); }
      } else if (item.kind === 'tape') {
        p.v = root.visible ? 1 : 0; if (root.visible) { p.s = r(root.scale.x / (item.length / 160)); p.a = r(root.alpha); }
      } else {
        p.v = root.visible ? 1 : 0; if (root.visible) { p.x = r(root.position.x); p.y = r(root.position.y); p.s = r(root.scale.x); p.rot = r(root.rotation); p.a = r(root.alpha); }
      }
      return p;
    });
    out.push(JSON.stringify({ item: k, poses }));
  });
});
const rail = []; for (let i = 0; i < 12; i++) { const rng = createRng(`u${i}:unit-collage`); rail.push(CANDY[Math.floor(rng() * CANDY.length)]); }
out.push(JSON.stringify({ rail }));
require('fs').writeFileSync('hanabi_collage_golden.txt', out.join('\n') + '\n');
console.log(out.length, cases.length);
