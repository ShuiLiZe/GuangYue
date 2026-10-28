// Build the GuangYue plugin-runtime prelude bundle.
//   node build.mjs            -> one-off build
//   node build.mjs --watch    -> rebuild on change
import { build, context } from 'esbuild';
import { mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { applyBootstrap } from './bootstrap.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const entry = resolve(here, 'src/index.js');
const outDir = resolve(here, '..', 'src', 'main', 'assets');
const outfile = resolve(outDir, 'prelude.js');
const metaFile = resolve(outDir, 'prelude.meta.json');

const LIBS = ['cheerio', 'crypto-js', 'dayjs', 'qs', 'he', 'big-integer'];

function installedVersions() {
  const libs = {};
  for (const name of LIBS) {
    let v = 'unknown';
    try {
      v = JSON.parse(readFileSync(resolve(here, 'node_modules', name, 'package.json'), 'utf8')).version;
    } catch {
      try {
        v = JSON.parse(readFileSync(resolve(here, 'package.json'), 'utf8')).dependencies?.[name] ?? 'unknown';
      } catch {
        /* keep unknown */
      }
    }
    libs[name] = v;
  }
  return libs;
}

function writeMeta() {
  const bytes = statSync(outfile).size;
  const meta = {
    generatedAt: new Date().toISOString(),
    bytes,
    libs: installedVersions(),
  };
  writeFileSync(metaFile, JSON.stringify(meta, null, 2) + '\n', 'utf8');
  return meta;
}

mkdirSync(outDir, { recursive: true });

const options = {
  entryPoints: [entry],
  outfile,
  bundle: true,
  format: 'iife',
  platform: 'browser',
  target: ['es2020'],
  minify: true,
  legalComments: 'none',
  charset: 'utf8',
  logLevel: 'info',
  // QuickJS has no atob/Buffer: prepend a tiny runtime shim so the load-time
  // base64 entity table in htmlparser2 -> entities can decode.
  plugins: [
    {
      name: 'gy-runtime-bootstrap',
      setup(b) {
        b.onEnd((result) => {
          if (result.errors.length) return;
          const patched = applyBootstrap(readFileSync(outfile, 'utf8'));
          writeFileSync(outfile, patched, 'utf8');
        });
      },
    },
  ],
};

if (process.argv.includes('--watch')) {
  const ctx = await context(options);
  await ctx.watch();
  console.log('[build] watching', entry);
} else {
  await build(options);
  const meta = writeMeta();
  console.log(`[build] ${outfile}`);
  console.log(`[build] bytes=${meta.bytes}`);
  console.log(`[build] libs=${JSON.stringify(meta.libs)}`);
  console.log(`[build] meta=${metaFile}`);
}
