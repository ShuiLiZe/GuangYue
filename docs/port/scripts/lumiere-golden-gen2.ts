import { compileLumiereProgram } from './src/components/visualizer/lumiere/lumiereProgram';
import { LUMIERE_PROFILES, profileOf } from './src/components/visualizer/lumiere/catalog';
import { createRng, createRngAt } from './src/components/visualizer/lumiere/lumiereRandom';
import { segmentWords, wordScales, wordJags } from './src/components/visualizer/lumiere/text/wordStyle';
import { segmentLyricWords } from './src/utils/lyrics/wordSegmentation';
import { lumiereTypographyOfLine, resolveLumiereLeadShot, createLumiereCamera } from './src/components/visualizer/lumiere/lumiereUnitLayout';
import { resolveBeams, lightAt } from './src/components/visualizer/lumiere/light/rig';
import { fixedSlot, freePlacements } from './src/components/visualizer/lumiere/text/windowSlots';
import { CHAMPAGNE, mixRgb, rgbOf, scaleRgb, WHITE } from './src/components/visualizer/lumiere/color';
import { appendFileSync } from 'fs';
const T = ["迷失在无边际这幽深的森林","风吹过","我们慢慢走回去，星河与风一起落在很远很远的地方","光落在晨雾里","你说夜色会把所有的名字都轻轻藏起来","晚安","在城市尽头等一场迟到的雨","把回忆折成纸船放进河流","轻轻唱 hello again 在无人的街角","我听见远方的海潮一遍一遍拍打着沉默的礁石和那座早已熄灭多年的灯塔"];
const lines:any[] = Array.from({length:40},(_,i)=>{const s=i*3,t=T[i%10],ch=Array.from(t),st=2.8/ch.length;return {words:ch.map((c,o)=>({text:c,startTime:s+o*st,endTime:s+(o+1)*st})),startTime:s,endTime:s+2.9,fullText:t,translation:`translation ${i}`,isChorus:i%4===0};});
const f=(x:number)=>Number.isInteger(x)?String(x):x.toFixed(6).replace(/0+$/,'').replace(/\.$/,'');
const out:string[]=[];
const frameBand=(width:number,height:number)=>{const padX=Math.max(30,width*0.065),padY=Math.max(30,height*0.085);const left=padX/height+0.04;return {left,right:width/height-left,top:padY/height+0.035,bottom:Math.max(padY/height+0.035+0.2,1-0.2)};};
T.forEach((t,i)=>out.push(`W ${i} ${segmentLyricWords({fullText:t} as any).map(p=>p.segment+(p.isWordLike?'':'~')).join('|')}`));
const p = compileLumiereProgram(lines, 'probe-0', {}, { seamless: false });
const g = p.paragraphs[6]!; const seed=`probe-0:${g.id}`;
const shots = g.shots.map(s=>({profile:profileOf(s.kind),startTime:s.startTime,endTime:s.endTime,lines:s.lineIndices}));
const UC:any=[0.62,0.68,0.8]; const glowOf=(rgb:any,fb:any)=>{const pk=Math.max(...rgb);return pk<0.04?fb:scaleRgb(rgb,1/pk);};
const resolveLumierePalette=(theme:any,themeMix=0)=>{const mix=Math.min(1,Math.max(0,themeMix));const accent=rgbOf(theme.accentColor,CHAMPAGNE);let light=mixRgb(mixRgb(CHAMPAGNE,accent,0.18),glowOf(accent,CHAMPAGNE),mix);const peak=Math.max(...light,1e-3);light=scaleRgb(light,1/peak);const warmLit=mixRgb(light,WHITE,0.2);const primary=glowOf(rgbOf(theme.primaryColor,warmLit),warmLit);const secondary=theme.secondaryColor?glowOf(rgbOf(theme.secondaryColor,primary),primary):primary;return {light,lit:mixRgb(warmLit,mixRgb(primary,WHITE,0.2),mix),unlit:mixRgb(mixRgb(light,UC,0.55),mixRgb(secondary,UC,0.35),mix)};};
const lead = resolveLumiereLeadShot(shots).profile;
out.push(`L ${lead.kind}`);
const typo = lumiereTypographyOfLine(shots);
out.push(`Y ${g.lines.map((_,i)=>typo(i)).join(',')}`);
for (const i of [0,1]) {
  const ln=g.lines[i]; const ws = segmentWords(ln); const sd=`${seed}:${i}:${ln.fullText}`;
  const sc = wordScales(ws, sd); out.push(`C ${i} ${sc.map(f).join(',')} ${wordJags(ws, sc, sd).map(f).join(',')}`);
}
const ra=createRngAt(`${seed}:window`, 37); out.push(`Q ${[0,1,2].map(()=>f(ra())).join(' ')}`);
for (const [w,h] of [[800,360],[360,800]]) {
  const cam = createLumiereCamera({ width:w, height:h, camera: lead.camera, startTime:g.startTime, endTime:g.endTime, sections:g.sections });
  for (const t of [3,37.5,44]) { const c=cam(t); out.push(`CAM ${w} ${t} ${[c.x,c.y,c.scale,c.rotation].map(f).join(' ')}`); }
  const b=frameBand(w,h); out.push(`FB ${w} ${[b.left,b.right,b.top,b.bottom].map(f).join(' ')}`);
}
const region={cx:lead.region.cx*2.2222,cy:lead.region.cy,w:lead.region.w*2.2222,h:lead.region.h};
for (const typ of ['horizontal','vertical'] as const) for (const rel of [-2,-1,1,2]) { const s=fixedSlot(typ,rel,region,lead.heroSize,0.95,(o,sc)=>lead.heroSize*sc*(1+0.1*o)); out.push(`FS ${typ} ${rel} ${[s.dx,s.dy,s.scale,s.alpha,s.orient].map(f).join(' ')}`); }
const fp=freePlacements(createRng(`${seed}:placements:1`), region, 0.95); for (const k of [-2,-1,1,2]) { const s=fp.get(k)!; out.push(`FP ${k} ${[s.dx,s.dy,s.scale,s.alpha,s.orient,s.rotation].map(f).join(' ')}`); }
const aspect=800/360; const idx=0; const prof=profileOf(g.shots[idx]!.kind);
const rig=prof.light({aspect, random:createRng(`${seed}:${idx}:${prof.kind}:light`)});
const pal = resolveLumierePalette({accentColor:'#ffffff', primaryColor:'#ffffff', secondaryColor:'#9ca3af'} as any, 0.3);
out.push(`PAL ${[...pal.light,...pal.lit,...pal.unlit].map(f).join(' ')}`);
const beams=resolveBeams(rig, 37.5, aspect, {intensity:1,bass:0,color:pal.light,local:1.5});
out.push(`RB ${prof.kind} ${beams.length} ${beams.map(b=>[b.ox,b.oy,b.dx,b.dy,b.reach].map(f).join(',')).join(' ')}`);
for (const [x,y] of [[0.2498,0.5824],[0.8955,0.2667],[0.25,0.59],[1.2,0.5]]) out.push(`LA ${x} ${y} ${f(lightAt(beams,x,y))}`);
for (const k of ['zenith-shaft','lattice-blinds','prism-wheel','caustic-pool']) { try{ const r2=profileOf(k).light({aspect, random:createRng('x')}); const b2=resolveBeams(r2, 10, aspect, {intensity:1,bass:0,color:[1,1,1],local:5}); out.push(`LB ${k} ${f(lightAt(b2,1.1,0.5))} ${f(lightAt(b2,0.6,0.3))}`);}catch(e){} }
appendFileSync('/workspace/work/lumiere/lumiere_golden.txt', out.join('\n')+'\n');
// 校验：每个光位在各宽高比下光束数一致
let bad=0; for (const pr of LUMIERE_PROFILES){ const n=[0.45,2.2222].map(a=>pr.light({aspect:a,random:createRng('x')}).beams.length); if(n[0]!==n[1]){bad++;console.log('mismatch',pr.kind,n);} } console.log('mismatch total',bad);
console.log(LUMIERE_PROFILES.map(p=>p.kind).filter(k=>['zenith-shaft','lattice-blinds','prism-wheel','caustic-pool'].includes(k)));
