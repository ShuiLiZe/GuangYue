const THREE=require('/workspace/work/folia-src/node_modules/three/build/three.cjs');
// 输入用一组固定 hex（任意调色板，三套颜色），对每个 (daylight, overcast, snowCover) 推出 5 色 hex
const base={skyTop:'#0e1524',skyBottom:'#26324a',hemiSky:'#3a4a6e',hemiGround:'#1a1820',moon:'#9cb3dc',
skyTopDusk:'#2c2f5c',skyBottomDusk:'#e58a62',hemiSkyDusk:'#8b79a6',hemiGroundDusk:'#30262a',moonDusk:'#ffae78',
skyTopDay:'#4d8bd4',skyBottomDay:'#cde4f2',hemiSkyDay:'#bcd6ee',hemiGroundDay:'#5f5b52',moonDay:'#fff0da'};
const dusk=0.45;const out=[];
for(const daylight of [0,0.2,0.45,0.7,1]) for(const overcast of [0,0.5,1]) for(const snowCover of [0,1]){
 const res={};
 for(const key of ['skyTop','skyBottom','hemiSky','hemiGround','moon']){
  const c=new THREE.Color(), o=new THREE.Color();
  if(daylight<=dusk) c.copy(new THREE.Color(base[key])).lerp(new THREE.Color(base[key+'Dusk']),daylight/dusk);
  else c.copy(new THREE.Color(base[key+'Dusk'])).lerp(new THREE.Color(base[key+'Day']),(daylight-dusk)/(1-dusk));
  const l=c.r*0.2126+c.g*0.7152+c.b*0.0722;
  o.setRGB(l,l,l).multiplyScalar(key==='moon'?1:0.9+0.35*daylight);
  c.lerp(o,overcast*(key==='moon'?0.45:0.7));
  if(key==='hemiGround') c.lerp(o.setRGB(0.55,0.6,0.7).multiplyScalar(0.35+0.65*daylight),snowCover*0.5);
  res[key]=[c.r,c.g,c.b];
 }
 out.push(JSON.stringify({daylight,overcast,snowCover,res}));
}
console.log(JSON.stringify(base)); out.forEach(l=>console.log(l));
