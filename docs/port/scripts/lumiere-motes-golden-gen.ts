import { createMotes } from './src/components/visualizer/lumiere/light/motes';
import { MOTES, FRONT } from './src/components/visualizer/lumiere/rigs/base';
import { writeFileSync } from 'fs';
class Sprite { anchor={set(){}}; position={x:0,y:0,set(x:number,y:number){this.x=x;this.y=y;}}; width=0; height=0; alpha=0; tint=0; visible=true; }
class Container { children:any[]=[]; addChild(c:any){this.children.push(c);} destroy(){} }
const pixi:any={Sprite, Container};
const f=(x:number)=>Number.isInteger(x)?String(x):x.toPrecision(12);
const out:string[]=[];
for (const [name,spec] of [['motes',MOTES],['front',FRONT]] as const) {
  const m=createMotes(pixi,{width:800,height:360,seed:`probe-0:lumiere-p6:${name}`,spec,texture:null as any});
  for (const t of [0,37.5,123.4]) {
    m.update(t,[],0xffeecc,0.9);
    const ss=(m.view as any).children as Sprite[];
    let sx=0,sy=0,sa=0,sw=0,nv=0;
    ss.forEach((s,i)=>{ if(!s.visible) return; nv++; sx+=s.position.x*(i%3+1); sy+=s.position.y; sa+=s.alpha; sw+=s.width; });
    out.push(['MO',name,t,ss.length,nv,sx,sy,sa,sw].map(v=>typeof v==='number'?f(v):v).join(' '));
    const s0=ss[0]!; out.push(['M0',name,t,s0.position.x,s0.position.y,s0.alpha,s0.width].map(v=>typeof v==='number'?f(v):v).join(' '));
  }
}
writeFileSync('/workspace/work/lumiere/lumiere_motes_golden.txt', out.join('\n')+'\n'); console.log(out.join('\n'));
