import { planBursts, createCrossBurst, burstLightBoost } from './src/components/visualizer/lumiere/light/crossBurst';
import { writeFileSync } from 'fs';
class Sprite { anchor={set(){}}; position={x:0,y:0,set(x:number,y:number){this.x=x;this.y=y;}}; width=0; height=0; alpha=0; tint=0; rotation=0; visible=true; constructor(public tex:any){} }
class Container { children:any[]=[]; visible=true; position={x:0,y:0,set(x:number,y:number){this.x=x;this.y=y;}}; addChild(c:any){this.children.push(c);} destroy(){} }
const pixi:any={Sprite, Container};
const f=(x:number)=>Number.isInteger(x)?String(x):x.toPrecision(12);
const out:string[]=[];
const lines=[0,1,2,3,4].map(li=>({glyphs:Array.from({length:3+li*2},(_,g)=>({glyphIndex:g*(li%2+1),start:10+li*3+g*0.21}))}));
const specs:any[]=[{mode:'sung',density:0.35,every:1,offset:0,size:3.6,tint:[1,.6,.45]},{mode:'sweep',density:0.5,every:3,offset:1,size:3,tint:[1,.8,.7]},{mode:'sung',density:0.05,every:2,offset:1,size:3.2,tint:[.8,.9,1]}];
specs.forEach((s,si)=>{ const tr=planBursts(s,lines,`probe-0:lumiere-p6:${si}`); out.push(['PN',si,tr.length].join(' ')); tr.forEach(t=>out.push(['PB',si,t.lineIndex,t.glyphIndex,t.time,t.size,t.dy].map(v=>typeof v==='number'?f(v):v).join(' '))); });
const sprites:any={dot:'dot',bokeh:'bokeh',streak:'streak',star:'star'};
const layer=createCrossBurst(pixi,{sprites});
const names=['ring','vg','hg','vc','hc','flash'];
for (const age of [0,0.01,0.025,0.05,0.09,0.2,0.45,0.59]) {
  layer.update([{age,x:120.5,y:200.25,length:64,color:[1,0.78,0.52]}]);
  const h=(layer.view as any).children[0];
  out.push(['BL',f(age),f(burstLightBoost(age))].join(' '));
  h.children.forEach((s:any,i:number)=>out.push(['BS',f(age),names[i],s.position.x,s.position.y,s.width,s.height,s.alpha,s.tint,s.rotation].map(v=>typeof v==='number'?f(v):v).join(' ')));
}
writeFileSync('./lumiere_burst_golden.txt', out.join('\n')+'\n'); console.log(out.slice(0,12).join('\n'), out.length);
