import { LUMIERE_PROFILES } from './src/components/visualizer/lumiere/catalog';
import { createRng } from './src/components/visualizer/lumiere/lumiereRandom';
import { writeFileSync } from 'fs';
const f=(x:number)=>Number.isInteger(x)?String(x):x.toPrecision(12);
const out:string[]=[];
for (const [ai,aspect] of [[0,800/360],[1,360/800]] as const) for (const p of LUMIERE_PROFILES) {
  const s=p.lineArt({aspect, random: createRng(`golden:${p.kind}:art`)});
  let pts=0,sx=0,sy=0,sw=0,sa=0,sd=0,ss=0,nd=0,ds=0,wx=0;
  s.paths.forEach((q,i)=>{pts+=q.points.length; for(const [x,y] of q.points){sx+=x;sy+=y;} wx+=(i%7+1)*(q.points[0]![0]+q.points[q.points.length-1]![1]); sw+=q.width;sa+=q.alpha;sd+=q.delay;ss+=q.span; if(q.dash){nd++;ds+=q.dash[0]+q.dash[1];}});
  let nx=0,ny=0,nz=0,ndl=0,nt=0; s.nodes.forEach((n,i)=>{nx+=n.at[0]*(i%5+1);ny+=n.at[1];nz+=n.size;ndl+=n.delay;nt+=n.twinklePhase;});
  out.push(['LA',ai,p.kind,s.paths.length,s.nodes.length,pts,...[sx,sy,wx,sw,sa,sd,ss],nd,...[ds,nx,ny,nz,ndl,nt]].map(v=>typeof v==='number'?f(v):v).join(' '));
}
// 基准单元（probe-0:lumiere-p6，镜头 0 optics-raytrace）在 800x360 下的线稿前两条完整点列
const s=LUMIERE_PROFILES.find(p=>p.kind==='optics-raytrace')!.lineArt({aspect:800/360, random:createRng('probe-0:lumiere-p6:0:optics-raytrace:art')});
s.paths.slice(0,4).forEach((q,i)=>out.push(`RT ${i} ${q.points.map(([x,y])=>f(x)+','+f(y)).join(' ')}`));
writeFileSync('/workspace/work/lumiere/lumiere_lineart_golden.txt', out.join('\n')+'\n');
console.log(out.length);
