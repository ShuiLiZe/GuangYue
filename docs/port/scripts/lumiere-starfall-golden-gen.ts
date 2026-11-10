import { createStarfall } from './src/components/visualizer/lumiere/light/starfall';
import { writeFileSync } from 'fs';
class Sprite { anchor={set(){}}; position={x:0,y:0,set(x:number,y:number){this.x=x;this.y=y;}}; width=0; height=0; alpha=0; tint=0; rotation=0; visible=true; constructor(public tex:any){} }
class Container { children:any[]=[]; addChild(...c:any[]){this.children.push(...c);} destroy(){} }
const pixi:any={Sprite, Container};
const f=(x:number)=>Number.isInteger(x)?String(x):x.toPrecision(12);
const out:string[]=[];
const specs:any[]=[{stars:300,opening:3.6,rain:50,rainSpeed:0.16,rainSpread:0.25,brightness:0.85},{stars:620,opening:2.4,rain:60,rainSpeed:0.6,rainSpread:1.8,brightness:1.2}];
specs.forEach((spec,si)=>{
  const s=createStarfall(pixi,{width:800,height:360,seed:`probe-0:lumiere-p${si}`,spec,dot:'dot',star:'star',streak:'streak'});
  const [trails,points]=(s.view as any).children;
  out.push(['SN',si,points.children.length,points.children.filter((p:any)=>p.tex==='star').length].join(' '));
  for (const [local,time] of [[0.05,0.05],[0.6,0.6],[1.2,1.2],[2.5,14.2],[25,37.5]]) {
    s.update(local,time,[],0xffeecc,0.9);
    let nv=0,sx=0,sy=0,sa=0,sw=0,tv=0,tw=0,th=0,ta=0;
    points.children.forEach((p:any,i:number)=>{ if(!p.visible) return; nv++; sx+=p.position.x*(i%3+1); sy+=p.position.y; sa+=p.alpha; sw+=p.width; });
    trails.children.forEach((p:any)=>{ if(!p.visible) return; tv++; tw+=p.width; th+=p.height; ta+=p.alpha; });
    out.push(['SF',si,local,time,nv,sx,sy,sa,sw,tv,tw,th,ta].map(v=>typeof v==='number'?f(v):v).join(' '));
  }
});
writeFileSync('./lumiere_starfall_golden.txt', out.join('\n')+'\n'); console.log(out.join('\n'));
