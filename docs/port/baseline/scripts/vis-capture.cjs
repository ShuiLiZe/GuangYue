// B0 Folia 基准截图：用 Folia 自带的 visualizerMemory 探针，把某个样式定格在同一首合成歌（cjk=1）的同一时刻。
// 先在 D:\workspaces\_folia-src 跑 `npx vite --port 3000`，再：
//   node vis-capture.cjs [样式,样式…] [秒=37.5] [输出目录]
// 输出 folia-<样式>-t<秒>-<视口>.png；视口 p800 = 手机横屏 800x360dp（2x → 1600x720，与 gy34 模拟器横屏同像素），
// d1600 = 桌面宽窗 1600x720（1x）。GuangYue 侧同一时刻：
//   am start -n com.guangyue.music/.V0cStageActivity --es v0c_fixture probe --el v0c_ms <毫秒> --es v0c_style <样式>
const path = require('path');
const fs = require('fs');
const { chromium } = require('D:/workspaces/_folia-src/node_modules/playwright');

const styles = (process.argv[2] || 'monet,pendolo,nebbia,sonnet,hanabi,tempera,lumiere').split(',');
const t = Number(process.argv[3] || '37.5');
const out = process.argv[4] || path.join(__dirname, '..', 'screenshots', 'visualizer');
const settleMs = Number(process.env.SETTLE_MS || '7000');
const viewports = [
  { key: 'p800', width: 800, height: 360, dsf: 2 },
  { key: 'd1600', width: 1600, height: 720, dsf: 1 },
];
const extra = process.env.EXTRA || '';

(async () => {
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({
    // 本机没装 Chrome；BROWSER 指定任一 Chromium 内核可执行文件，缺省用 playwright 自带的 chromium。
    ...(process.env.BROWSER ? { executablePath: process.env.BROWSER } : {}),
    headless: true,
    args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader', '--ignore-gpu-blocklist'],
  });
  for (const vp of viewports) {
    const ctx = await browser.newContext({ viewport: { width: vp.width, height: vp.height }, deviceScaleFactor: vp.dsf, colorScheme: 'dark' });
    for (const style of styles) {
      const page = await ctx.newPage();
      const errors = [];
      page.on('pageerror', e => errors.push(String(e.message || e)));
      const url = `http://localhost:3000/dev-probe.html?probe=visualizerMemory&vis=${style}&cjk=1&speed=0&start=${t}${extra}`;
      await page.goto(url, { waitUntil: 'load', timeout: 90000 });
      await page.waitForTimeout(settleMs);
      const file = path.join(out, `folia-${style}-t${t}-${vp.key}.png`);
      await page.screenshot({ path: file });
      console.log(`${style} ${vp.key} -> ${path.basename(file)}${errors.length ? ' ERR ' + errors.slice(0, 2).join(' | ') : ''}`);
      await page.close();
    }
    await ctx.close();
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
