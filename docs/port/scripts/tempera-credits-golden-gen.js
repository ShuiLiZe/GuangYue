// 用法：在 Folia 仓库旁建 tg/tc，entry.ts 见文件尾注释；esbuild entry.ts --bundle --platform=node --outfile=tc.js --external:@chenglou/pretext（pretext 给个空桩）；node 本文件 → tempera_credits_golden.txt
// 凝彩片尾海报金标：假 Pixi 跑 Folia buildTemperaCreditsPoster / resolveTemperaCreditsFrame，逐节点记录。
require('./tc.js');
const f = v => (Math.abs(v) < 5e-10 ? '0' : Number(v).toPrecision(10));
class Pt { constructor(x=0,y=0){ this.x=x; this.y=y; } set(x,y){ this.x=x; this.y=(y===undefined?x:y); } }
class Container { constructor(){ this.children=[]; this.position=new Pt(); this.pivot=new Pt(); this.scale=new Pt(1,1); this.anchor=new Pt(); this.alpha=1; this.visible=true; this.filters=null; this.rec=[]; }
  get x(){return this.position.x} get y(){return this.position.y}
  addChild(...cs){ cs.forEach(c=>this.children.push(c)); return cs[0]; } destroy(){} }
class Graphics extends Container { constructor(){ super(); this.kind='g'; this.segs=0; this.polys=[]; }
  poly(p){ this.polys.push(p.slice()); return this; } fill(o){ this.op='fill'; this.o=o; return this; } stroke(o){ this.op=this.op?this.op+'+stroke':'stroke'; this.o=o; return this; }
  moveTo(){ this.segs++; return this; } lineTo(){ return this; } }
class TextStyle { constructor(o){ Object.assign(this,o); } }
class Text extends Container { constructor({text,style}){ super(); this.kind='t'; this.text=text; this.style=style; this.height=style.fontSize*1.2; } }
const Color = { shared: { v:'', setValue(c){ this.v=c; return this; }, toNumber(){ return parseInt(String(this.v).replace('#',''),16); } } };
const pixi = { Container, Graphics, Text, TextStyle, Color, Filter: class{} };
const palette = { paper:'#f3efe6', ink:'#141414', accent:'#c0392b', tone1:'#e6dfd2', tone2:'#c9bfae', tone3:'#9c8f7c', tone4:'#5a5045', line:'#777777', gradient:null, textGradient:null };
const theme = { fontStyle:'sans', fontWeight: 600 };
const tuning = { showDecor:true, textInversion:false };
const out=[];
const toneOf={}; ['paper','ink','tone1','tone2','tone3','tone4'].forEach(k=>toneOf[parseInt(palette[k].slice(1),16)]=k);
const cases=[ [{title:'夜曲',artist:'周杰伦',album:'十一月的萧邦'},800,360,1], [{title:'Blue Bird',artist:'いきものがかり',album:null},360,800,1.2], [{title:null,artist:null,album:'Album Only'},640,480,0.9] ];
cases.forEach(([meta,W,H,fs],c)=>{
  const v = TC.buildTemperaCreditsPoster(pixi,{theme,tuning,palette,metadata:meta,width:W,height:H,lyricsFontScale:fs});
  const ch = v.container.children;
  out.push(`CASE ${c} ${W} ${H} ${fs} ${ch.length}`);
  ch.forEach((n,i)=>{
    if (n.kind==='g') {
      const p=n.polys[0]||[];
      out.push(`N ${c} ${i} g ${n.op} ${toneOf[n.o.color]} ${f(n.o.width||0)} ${f(n.o.alpha)} ${p.length} ${p.slice(0,4).map(f).join(' ')} segs=${n.segs} pv ${f(n.pivot.x)} ${f(n.pivot.y)}`);
    } else {
      const t=n.children.map(x=>`${x.text}|${f(x.style.fontSize)}|${f(x.style.wordWrapWidth)}|${f(x.position.y)}|${f(x.alpha)}`).join(' ; ');
      out.push(`N ${c} ${i} title ${t}`);
    }
  });
  [-0.5,0.05,0.3,0.7,1.0,1.6,2.5,9,30].forEach(e=>{
    v.updateTime(e);
    out.push(`T ${c} ${e} `+ch.map(n=>`${f(n.alpha)},${n.visible?1:0},${f(n.position.x)},${f(n.position.y)},${f(n.scale.x)}`).join(' '));
  });
});
[118,119.9,120.3,120.6,121,121.5,122,123,125].forEach(t=>{ const fr=TC.resolveTemperaCreditsFrame(t,119.9); out.push(`F ${t} ${fr.active?1:0} ${f(fr.lyricAlpha)} ${f(fr.posterAlpha)} ${f(fr.posterOffsetY)} ${f(fr.posterScale)}`); });
require('fs').writeFileSync('tempera_credits_golden.txt', out.join('\n')+'\n'); console.log(out.length);

/* entry.ts:
import { buildTemperaCreditsPoster } from '../../folia/src/components/visualizer/tempera/temperaSceneBuilder';
import { resolveTemperaCreditsFrame } from '../../folia/src/components/visualizer/tempera/temperaCreditsLayer';
(globalThis as any).TC = { buildTemperaCreditsPoster, resolveTemperaCreditsFrame };
*/
