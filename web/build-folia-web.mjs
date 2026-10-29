/**
 * 广乐 Folia 覆盖层（folia-overlay）的构建脚本。
 *
 * 职责：把 folia-major 的源码与广乐自己的补丁合成一份可构建的 Web 工程，然后交给 Vite。
 *
 * 设计要点：
 * - **不把 Folia 源码提交进广乐仓库**（1300+ 组件、22MB、每次 CI 都白拉一遍没有意义）。
 *   构建时按固定 commit 拉取，补丁全部放在 `overlay/` 下，可审计、可重复。
 * - 补丁分三类：`replace/` 整文件替换、`patch/` 文本替换（带指纹校验，上游一改就报错而不是
 *   悄悄失效）、`extra/` 新增文件。每一类都写明它替换的是上游哪个文件。
 * - 产物只保留移动端要用的一份 `index.html`，其余入口（stage-client / mod-export）不构建。
 *
 * 用法：
 *   node build-folia-web.mjs --prepare   # 合成工作副本到 .work/folia
 *   node build-folia-web.mjs --build     # 合成 + vite build + 拷到 assets
 *   node build-folia-web.mjs --dev       # 合成后跑 vite dev
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const ROOT = here;                                     // web/
const OVERLAY = path.join(ROOT, 'folia-overlay');
const WORK = path.join(ROOT, '.work');
const SRC = path.join(WORK, 'folia');
const DIST = path.join(WORK, 'dist');

const cfg = JSON.parse(fs.readFileSync(path.join(OVERLAY, 'manifest.json'), 'utf8'));

const log = (...a) => console.log('[folia-web]', ...a);

/** 给 shell 用的最小转义：含空白或 shell 元字符的参数包一层双引号。 */
function quoteArg(value) {
  const text = String(value);
  if (text === '') return '""';
  return /[\s"&|<>^]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function run(cmd, args, cwd, env = {}) {
  log(`$ ${cmd} ${args.join(' ')}`);
  const isWin = process.platform === 'win32';
  // Node 20+ 在 Windows 上禁止直接 spawn .cmd/.bat，必须经由 shell。参数里有空格与引号，
  // 所以整条命令拼成一个字符串，而不是把数组交给 shell:true（后者在 Node 里有注入告警）。
  const command = [cmd, ...args].map(quoteArg).join(' ');
  const result = spawnSync(command, {
    cwd,
    stdio: 'inherit',
    env: { ...process.env, ...env },
    shell: isWin ? (process.env.ComSpec || 'cmd.exe') : '/bin/sh',
  });
  if (result.status !== 0) {
    throw new Error(`${cmd} ${args.join(' ')} 失败（退出码 ${result.status}）`);
  }
}

/** 1. 取得上游源码：有缓存就复用，没有就拉固定 commit。 */
async function ensureSource() {
  const stampFile = path.join(SRC, '.folia-revision');
  const cached = path.join(WORK, 'cache');
  const hasCache = fs.existsSync(path.join(cached, 'package.json'));
  const stampOk = fs.existsSync(stampFile) && fs.readFileSync(stampFile, 'utf8').trim() === cfg.folia.commit;

  // 1. 已经合成了这个 commit 的源码：直接复用。
  if (stampOk && !hasCache) {
    log(`复用已缓存的上游源码 @${cfg.folia.commit}`);
    return;
  }

  // 2. 本地缓存（CI 由 actions/cache 预置，本地由 tools/seed-folia-cache 填充）：
  //    它只是"上游源码的一份拷贝"，没有 .git，所以这里补一个空仓库，后续 reset/clean 是空操作。
  fs.mkdirSync(SRC, { recursive: true });
  if (hasCache) {
    log(`使用本地缓存 ${cached}`);
    for (const entry of fs.readdirSync(cached)) {
      if (entry === 'node_modules') continue;
      fs.rmSync(path.join(SRC, entry), { recursive: true, force: true });
      fs.cpSync(path.join(cached, entry), path.join(SRC, entry), { recursive: true });
    }
    fs.writeFileSync(stampFile, cfg.folia.commit);
    return;
  }

  // 3. 在线拉取：清掉源码与 .git（保留 node_modules：上游 lockfile 没变就不用重装 1170 个包）。
  for (const entry of fs.readdirSync(SRC, { withFileTypes: true })) {
    if (entry.name === 'node_modules') continue;
    fs.rmSync(path.join(SRC, entry.name), { recursive: true, force: true });
  }
  fs.mkdirSync(WORK, { recursive: true });
  log(`拉取 ${cfg.folia.repo} @ ${cfg.folia.commit}`);
  run('git', ['init', '-q'], SRC);
  run('git', ['remote', 'add', 'origin', cfg.folia.repo], SRC);

  // GitHub 对大仓浅拉会偶发断流（curl 56 / early EOF），重试三次；仍失败就把原因抛出去。
  let lastError = null;
  for (let attempt = 1; attempt <= 3; attempt += 1) {
    try {
      run('git', ['-c', 'http.version=HTTP/1.1', 'fetch', '--depth', '1', 'origin', cfg.folia.ref || 'main'], SRC);
      lastError = null;
      break;
    } catch (error) {
      lastError = error;
      log(`fetch 第 ${attempt} 次失败：${error.message}`);
      await new Promise((resolve) => setTimeout(resolve, 3000 * attempt));
    }
  }
  if (lastError) {
    throw new Error(
      `无法拉取上游源码（${lastError.message}）。离线时先运行 node tools/seed-folia-cache.mjs 填充 web/.work/cache。`,
    );
  }
  run('git', ['checkout', '-q', cfg.folia.commit], SRC);
  fs.writeFileSync(stampFile, cfg.folia.commit);
}

/** 2. 应用补丁。 */
function applyOverlay() {
  const replaceDir = path.join(OVERLAY, 'replace');
  const patchDir = path.join(OVERLAY, 'patch');
  const extraDir = path.join(OVERLAY, 'extra');

  if (fs.existsSync(replaceDir)) {
    for (const rel of fs.readdirSync(replaceDir, { recursive: true })) {
      const src = path.join(replaceDir, rel);
      if (!fs.statSync(src).isFile()) continue;
      const dst = path.join(SRC, rel);
      fs.mkdirSync(path.dirname(dst), { recursive: true });
      fs.copyFileSync(src, dst);
      log(`替换 ${rel}`);
    }
  }

  if (fs.existsSync(patchDir)) {
    for (const rel of fs.readdirSync(patchDir).filter((f) => f.endsWith('.json'))) {
      const spec = JSON.parse(fs.readFileSync(path.join(patchDir, rel), 'utf8'));
      const target = path.join(SRC, spec.file);
      if (!fs.existsSync(target)) throw new Error(`补丁目标不存在：${spec.file}`);
      let text = fs.readFileSync(target, 'utf8');
      for (const edit of spec.edits) {
        // 幂等：已经打过（或上游本来就长这样）就跳过，这样重复 --prepare 不会二次替换。
        if (text.includes(edit.replace)) {
          log(`补丁 ${rel} 已应用（${spec.file}）`);
          continue;
        }
        const idx = text.indexOf(edit.find);
        if (idx < 0) {
          throw new Error(
            `补丁 ${rel} 失效：在 ${spec.file} 里找不到\n---\n${edit.find}\n---\n` +
            `上游可能改了这段，请重新核对后更新 web/folia-overlay/patch/${rel}`,
          );
        }
        text = text.slice(0, idx) + edit.replace + text.slice(idx + edit.find.length);
      }
      if (spec.append) text += spec.append;
      fs.writeFileSync(target, text);
      log(`打补丁 ${spec.file}（${spec.edits.length} 处）`);
    }
  }

  if (fs.existsSync(extraDir)) {
    for (const rel of fs.readdirSync(extraDir, { recursive: true })) {
      const src = path.join(extraDir, rel);
      if (!fs.statSync(src).isFile()) continue;
      const dst = path.join(SRC, rel);
      fs.mkdirSync(path.dirname(dst), { recursive: true });
      fs.copyFileSync(src, dst);
      log(`新增 ${rel}`);
    }
  }
}

/** 3. 依赖。上游 lockfile 锁死版本，用 ci 保证可复现。 */
function ensureDeps() {
  const marker = path.join(SRC, 'node_modules', '.folia-install-ok');
  if (fs.existsSync(marker)) {
    log('依赖已就绪');
    return;
  }
  log('安装依赖（npm ci）…');
  run('npm', ['ci', '--no-audit', '--no-fund'], SRC);
  fs.writeFileSync(marker, new Date().toISOString());
}

async function prepare() {
  await ensureSource();
  // 每次合成前把工作副本还原到上游原样：补丁与替换都基于未修改的源码，重复执行才能幂等。
  // 用 stash 的思路不够（未跟踪文件），直接 reset --hard + clean -fd 最干净；node_modules 与
  // 上游 .gitignore 里的东西不受 -fd 影响（-x 才会连忽略文件一起删）。
  run('git', ['reset', '--hard', '-q'], SRC);
  run('git', ['clean', '-fdq'], SRC);
  applyOverlay();
  ensureDeps();
  log(`工作副本就绪：${SRC}`);
}

async function build(targetAssets) {
  await prepare();
  const mode = process.argv.includes('--dev-dist') ? 'development' : 'production';
  run('npx', ['vite', 'build', '--mode', mode], SRC, {
    CI: '1',
    // 只构建移动端入口；上游的 stage-client / mod-export 是桌面/OBS 用的，不进包。
    FOLIA_MOBILE_ENTRY: '1',
  });
  const out = path.join(SRC, 'dist');
  if (targetAssets) {
    fs.rmSync(targetAssets, { recursive: true, force: true });
    fs.mkdirSync(targetAssets, { recursive: true });
    fs.cpSync(out, targetAssets, { recursive: true });
    log(`产物 → ${targetAssets}`);
  }
}

const arg = process.argv[2];
try {
  if (arg === '--prepare') await prepare();
  else if (arg === '--build') await build(path.resolve(process.argv[3] || DIST));
  else if (arg === '--dev') {
    await prepare();
    run('npx', ['vite', '--port', '3070', '--host', '127.0.0.1'], SRC);
  } else {
    console.log('用法: node build-folia-web.mjs --prepare | --build <assetsDir> | --dev');
    process.exitCode = 1;
  }
} catch (error) {
  console.error('[folia-web] 失败：', error.message);
  process.exitCode = 1;
}
