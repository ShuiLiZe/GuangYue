import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const here = dirname(fileURLToPath(import.meta.url));
const preludePath = resolve(here, '..', 'src', 'main', 'assets', 'prelude.js');
const src = readFileSync(preludePath, 'utf8');

// Node builtins deliberately NOT provided: the sandbox mimics QuickJS, which has
// no require/process/module/Buffer/atob/setImmediate. Only `console` is exposed;
// `Buffer` is expected to be supplied by the prelude itself (bootstrap shim).
const sandbox = { console };
const ctx = vm.createContext(sandbox, {
  name: 'gy-prelude-verify',
  codeGeneration: { strings: true, wasm: false },
});

const failures = [];
function check(label, fn) {
  try {
    const ok = fn();
    if (ok === true) {
      console.log(`  ok   ${label}`);
    } else {
      failures.push(`${label} -> ${ok === false ? 'assertion false' : 'value=' + String(ok)}`);
      console.log(`  FAIL ${label} -> ${ok}`);
    }
  } catch (e) {
    failures.push(`${label} -> ${e && e.stack ? e.stack : e}`);
    console.log(`  FAIL ${label} -> ${e}`);
  }
}

// ---- load bundle -----------------------------------------------------------
new vm.Script(src, { filename: 'prelude.js' }).runInContext(ctx, { timeout: 60000 });

const G = sandbox;

console.log('globals:');
for (const name of ['cheerio', 'CryptoJS', 'dayjs', 'Qs', 'he', 'BigInteger']) {
  check(`globalThis.${name} present`, () => typeof G[name] !== 'undefined');
}

// ---- cheerio ---------------------------------------------------------------
check('cheerio.load text', () => {
  const $ = G.cheerio.load('<div id="a">hi</div>');
  return $('#a').text() === 'hi';
});
check('cheerio.load element html', () => {
  const $ = G.cheerio.load('<div id="a">hi</div>');
  return $('div').html() === 'hi';
});
check('cheerio entities decode (&alpha;)', () => {
  const $ = G.cheerio.load('<p>&alpha;&amp;&#x1F600;</p>');
  return $('p').text() === '\u03b1&\u{1F600}';
});

// ---- CryptoJS --------------------------------------------------------------
check('CryptoJS.MD5("abc")', () => G.CryptoJS.MD5('abc').toString() === '900150983cd24fb0d6963f7d28e17f72');
check('CryptoJS.SHA256("abc")', () =>
  G.CryptoJS.SHA256('abc').toString() === 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad');

// ---- dayjs -----------------------------------------------------------------
check('dayjs function form', () => typeof G.dayjs === 'function');
check('dayjs("2024-01-02").format', () => G.dayjs('2024-01-02').format('YYYY/MM/DD') === '2024/01/02');
check('dayjs().add', () => G.dayjs('2024-01-02').add(1, 'day').format('YYYY-MM-DD') === '2024-01-03');

// ---- Qs --------------------------------------------------------------------
check('Qs.stringify', () => {
  const s = G.Qs.stringify({ a: 1, b: 'x y' });
  return typeof s === 'string' && s.includes('b=x+y') && s.includes('a=1');
});
check('Qs.parse', () => G.Qs.parse('a=1&b=x%20y').b === 'x y');

// ---- he --------------------------------------------------------------------
check('he.decode("&amp;")', () => G.he.decode('&amp;') === '&');
check('he.encode', () => G.he.encode('<a>&', { useNamedReferences: true }) === '&lt;a&gt;&amp;');

// ---- BigInteger ------------------------------------------------------------
check('BigInteger(x) call form', () => G.BigInteger('123456789012345678901234567890').multiply(2).toString() === '246913578024691357802469135780');
check('new BigInteger(x) form', () => new G.BigInteger('123456789012345678901234567890').multiply(2).toString() === '246913578024691357802469135780');
check('BigInteger add/divide/pow', () => {
  const a = G.BigInteger('100000000000000000000');
  return a.add(1).toString() === '100000000000000000001'
    && G.BigInteger(255).divide(16).toString() === '15'
    && G.BigInteger(2).pow(100).toString() === '1267650600228229401496703205376';
});
check('BigInteger.mod / compare / isEven', () =>
  G.BigInteger('123456789').mod(1000).toString() === '789'
  && G.BigInteger(5).compare(3) === 1
  && G.BigInteger(4).isEven() === true);
check('BigInteger static one/zero', () =>
  String(G.BigInteger.one) === '1' && String(G.BigInteger.zero) === '0');

// ---- hygiene ---------------------------------------------------------------
// NOTE: `Buffer`/`atob` ARE defined on purpose - the prelude installs small
// polyfills so htmlparser2 -> entities can decode its base64 tree on QuickJS.
// Everything that is genuinely Node-only must stay absent.
check('no node-only globals leaked', () =>
  typeof G.require === 'undefined'
  && typeof G.process === 'undefined'
  && typeof G.module === 'undefined'
  && typeof G.exports === 'undefined'
  && typeof G.setImmediate === 'undefined');
check('prelude-installed polyfills present', () =>
  typeof G.atob === 'function' && typeof G.Buffer === 'function' && typeof G.Buffer.from === 'function');

const bytes = Buffer.byteLength(src, 'utf8');
console.log(`prelude.js bytes: ${bytes}`);

if (failures.length) {
  console.error(`\nVERIFY FAILED (${failures.length})`);
  for (const f of failures) console.error(' - ' + f);
  process.exit(1);
}
console.log('VERIFY OK');
