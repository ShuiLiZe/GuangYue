// 用一个假的 module/exports 载入 docs/demo-plugin.js，模拟宿主的 CommonJS 包装。
const fs = require('fs');
const src = fs.readFileSync(process.argv[2] || 'docs/demo-plugin.js', 'utf8');
const mod = { exports: {} };
const fn = new Function('module', 'exports', 'require', src.replace(/^/, ''));
fn(mod, mod.exports, require);
const p = mod.exports;
console.log('platform =', p.platform, '| version =', p.version);
console.log('fns =', Object.keys(p).filter((k) => typeof p[k] === 'function').join(','));
console.log('search 夜 →', JSON.stringify(p.search('夜', 1, 'music')).slice(0, 160));
console.log('search empty →', p.search('', 1, 'music').data.length, 'items');
console.log('getMediaSource →', p.getMediaSource({ id: 'demo-2' }, 'high').url);
console.log('getLyric head →', JSON.stringify(p.getLyric({ id: 'demo-1' }).rawLrc.split('\n')[0]));
console.log('getTopLists →', JSON.stringify(p.getTopLists()).slice(0, 100));
console.log('getRecommendSheetDetail →', p.getRecommendSheetDetail({ id: 'x' }, 1).data.length, 'items');
