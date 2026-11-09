import { compileLumiereProgram } from './src/components/visualizer/lumiere/lumiereProgram';
import { LUMIERE_PROFILES } from './src/components/visualizer/lumiere/catalog';
import { createRng } from './src/components/visualizer/lumiere/lumiereRandom';
import { buildStructureLines, resolveParagraphGapThreshold, draftParagraphs, classifyParagraph } from './src/components/visualizer/paragraphStructure';
import { writeFileSync } from 'fs';
const T = ["迷失在无边际这幽深的森林","风吹过","我们慢慢走回去，星河与风一起落在很远很远的地方","光落在晨雾里","你说夜色会把所有的名字都轻轻藏起来","晚安","在城市尽头等一场迟到的雨","把回忆折成纸船放进河流","轻轻唱 hello again 在无人的街角","我听见远方的海潮一遍一遍拍打着沉默的礁石和那座早已熄灭多年的灯塔"];
const lines:any[] = Array.from({length:40},(_,i)=>{const s=i*3,t=T[i%10],ch=Array.from(t),st=2.8/ch.length;return {words:ch.map((c,o)=>({text:c,startTime:s+o*st,endTime:s+(o+1)*st})),startTime:s,endTime:s+2.9,fullText:t,translation:`translation ${i}`,isChorus:i%4===0};});
const f=(x:number)=>Number.isInteger(x)?String(x):x.toFixed(6).replace(/0+$/,'').replace(/\.$/,'');
const out:string[]=[];
for (const seed of ['probe-0','lumiere','abc']) for (const seamless of [false,true]) {
  const p = compileLumiereProgram(lines, seed, {}, { seamless });
  out.push(`P ${seed} ${seamless?1:0} ${p.paragraphs.length} ${f(p.duration)} ${f(p.lyricEndTime!)} ${f(p.paragraphGapThreshold)}`);
  for (const g of p.paragraphs) {
    out.push(`G ${g.id} ${g.kind} ${g.boundary} ${f(g.startTime)} ${f(g.endTime)} ${f(g.lyricEndTime)} ${g.opening?1:0} ${g.transitionOut?`${g.transitionOut.kind}@${f(g.transitionOut.startTime)}`:'-'} ${g.lineIndices.join(',')||'-'} ${(g.sections??[]).map(s=>`${f(s.startTime)}:${s.kind}`).join(',')||'-'}`);
    for (const s of g.shots) out.push(`S ${s.id} ${s.kind} ${f(s.startTime)} ${f(s.endTime)} ${f(s.lyricEndTime)} ${s.isBridge?1:0} ${s.lineIndices.join(',')||'-'}`);
  }
}
const r=createRng('probe-0'); out.push(`R ${[0,1,2,3,4].map(()=>f(r())).join(' ')}`);
writeFileSync('/workspace/work/lumiere/lumiere_golden.txt', out.join('\n')+'\n');
// 光位目录
const prof:string[]=[];
const ASPECTS=[0.45,0.5625,1,1.7778,2.2222];
const beamKeys=['x','y','angle','spread','width','length','softness','intensity','streaks','streakFreq','streakSpeed','core'];
for (const p of LUMIERE_PROFILES) {
  const m=p.motes, fr=p.front, sf=p.starfall, b=p.burst;
  prof.push(`K ${p.kind} ${p.family} ${p.mood} ${p.typography} ${f(p.region.cx)} ${f(p.region.cy)} ${f(p.region.w)} ${f(p.region.h)} ${f(p.heroSize)} ${f(p.decay.strength)} ${f(p.decay.delay)} ${f(p.camera.push)} ${f(p.camera.driftX)} ${f(p.camera.driftY)} ${f(p.echo?.size??-1)} ${f(p.artGain??1)} ${p.label}`);
  const ms=(x:any)=>x?[x.count,x.sizeMin,x.sizeMax,x.driftX,x.driftY,x.swirl,x.swirlPeriod,x.twinkle,x.ambient,x.gain].map(f).join(' '):'-';
  prof.push(`M ${ms(m)} | ${ms(fr)} | ${sf?[sf.stars,sf.opening,sf.rain,sf.rainSpeed,sf.rainSpread,sf.brightness].map(f).join(' '):'-'} | ${b?[b.mode,b.density,b.every,b.offset,b.size,...b.tint].map(x=>typeof x==='number'?f(x):x).join(' '):'-'}`);
  for (const a of ASPECTS) {
    const rig=p.light({aspect:a, random:createRng(`fixed:${p.kind}:light`)});
    const fg=rig.fog, gl=rig.glare;
    prof.push(`A ${f(a)} ${[fg.density,fg.tyndallBase,fg.scale,fg.driftX,fg.driftY,fg.warp,fg.ambient].map(f).join(' ')} | ${gl?[gl.x,gl.y,gl.radius,gl.intensity,gl.streak].map(f).join(' '):'-'} | ${rig.caustic?[rig.caustic.scale,rig.caustic.speed,rig.caustic.inBeam,...(rig.caustic.floor?[rig.caustic.floor.cx,rig.caustic.floor.cy,rig.caustic.floor.rx,rig.caustic.floor.ry,rig.caustic.floor.strength]:[])].map(f).join(' '):'-'} | ${rig.wave?[rig.wave.mode,rig.wave.cx,rig.wave.cy,rig.wave.radius,rig.wave.frequency,rig.wave.speed,rig.wave.strength,rig.wave.separation??0,rig.wave.shield??0.45].map(x=>typeof x==='number'?f(x):x).join(' '):'-'}`);
    for (const bm of rig.beams) {
      const o=(x:any)=>x?`${f(x.amplitude)},${f(x.period)},${f(x.phase)}`:'-';
      const t=bm.tint?bm.tint.map(f).join(','):'-';
      const gb=bm.gobo?`${bm.gobo.pattern},${f(bm.gobo.frequency)},${f(bm.gobo.duty)},${f(bm.gobo.drift)}`:'-';
      prof.push(`B ${beamKeys.map(k=>f((bm as any)[k]??(k==='width'?0:k==='core'?0.5:0))).join(' ')} ${o(bm.sway)} ${o(bm.pulse)} ${t} ${f(bm.reveal??0)} ${gb} ${f(bm.spectrum??0)} ${f(bm.reach??0)}`);
    }
  }
}
writeFileSync('/workspace/work/lumiere/lumiere_rigs.txt', prof.join('\n')+'\n');
