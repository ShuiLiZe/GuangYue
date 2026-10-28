import * as cheerioNs from 'cheerio';
import CryptoJS from 'crypto-js';
import dayjsLib from 'dayjs';
import qsLib from 'qs';
import * as heNs from 'he';
import bigIntLib from 'big-integer';

// ---------------------------------------------------------------------------
// QuickJS / bare-VM bootstrap (runs AFTER the imports are evaluated).
// htmlparser2 -> entities decodes a module-level base64 entity table during
// module init with
//   `typeof atob=="function" ? atob(e) : typeof Buffer.from=="function" ? ...`
// QuickJS has neither a global `atob` nor `Buffer`, so that initializer throws
// ("Buffer is not defined") before we ever get here. Two things are needed,
// both applied below in this order:
//   1. a dependency-free base64 decoder, exposed as globalThis.atob;
//   2. a lazy `Buffer` shim, so code that already ran the `typeof Buffer`
//      branch (or any later plugin call) still decodes correctly.
// Nothing is deleted or feature-detected in a way that requires host APIs.
// ---------------------------------------------------------------------------
const G0 = globalThis;
const B64_ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';

function decodeBase64(input) {
  let str = String(input).replace(/[\t\n\f\r ]+/g, '').replace(/=+$/, '');
  if (str.length % 4 === 1) throw new Error('atob: invalid base64');
  let out = '';
  let buffer = 0;
  let bits = 0;
  for (let i = 0; i < str.length; i++) {
    const idx = B64_ALPHABET.indexOf(str.charAt(i));
    if (idx < 0) throw new Error('atob: invalid base64 character');
    buffer = (buffer << 6) | idx;
    bits += 6;
    if (bits >= 8) {
      bits -= 8;
      out += String.fromCharCode((buffer >> bits) & 0xff);
    }
  }
  return out;
}

function encodeBase64(input) {
  const str = String(input);
  let out = '';
  for (let i = 0; i < str.length; i += 3) {
    const c1 = str.charCodeAt(i);
    const c2 = i + 1 < str.length ? str.charCodeAt(i + 1) : 0;
    const c3 = i + 2 < str.length ? str.charCodeAt(i + 2) : 0;
    out += B64_ALPHABET.charAt(c1 >> 2);
    out += B64_ALPHABET.charAt(((c1 & 3) << 4) | (c2 >> 4));
    out += i + 1 < str.length ? B64_ALPHABET.charAt(((c2 & 15) << 2) | (c3 >> 6)) : '=';
    out += i + 2 < str.length ? B64_ALPHABET.charAt(c3 & 63) : '=';
  }
  return out;
}

if (typeof G0.atob !== 'function') G0.atob = decodeBase64;
if (typeof G0.btoa !== 'function') G0.btoa = encodeBase64;

if (typeof G0.Buffer === 'undefined') {
  const BufferShim = function Buffer(value, encoding) {
    // Only the two shapes cheerio/htmlparser2/entities use are supported:
    // Buffer.from(str, 'base64'|'utf8'|'binary'|'hex') and new Buffer(str, enc).
    if (!(this instanceof BufferShim)) return BufferShim.from(value, encoding);
    if (typeof value === 'number') {
      this.length = value;
      for (let i = 0; i < value; i++) this[i] = 0;
      return this;
    }
    const bytes = BufferShim.from(value, encoding);
    Object.assign(this, bytes);
    this.length = bytes.length;
    return this;
  };
  BufferShim.from = function from(value, encoding) {
    const bytes = [];
    if (typeof value === 'string') {
      const enc = (encoding || 'utf8').toLowerCase();
      if (enc === 'base64') {
        const decoded = decodeBase64(value);
        for (let i = 0; i < decoded.length; i++) bytes.push(decoded.charCodeAt(i) & 0xff);
      } else if (enc === 'hex') {
        for (let i = 0; i < value.length; i += 2) bytes.push(parseInt(value.slice(i, i + 2), 16) || 0);
      } else {
        // utf8 / latin1 / binary (cheerio only ever needs latin1 semantics here)
        for (let i = 0; i < value.length; i++) {
          const code = value.charCodeAt(i);
          if (code < 0x80) {
            bytes.push(code);
          } else if (code < 0x800) {
            bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f));
          } else {
            bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f));
          }
        }
      }
    } else if (value && typeof value.length === 'number') {
      for (let i = 0; i < value.length; i++) bytes.push(value[i] & 0xff);
    }
    bytes.toString = function toString(enc) {
      const e = (enc || 'utf8').toLowerCase();
      if (e === 'binary' || e === 'latin1') {
        let s = '';
        for (let i = 0; i < this.length; i++) s += String.fromCharCode(this[i]);
        return s;
      }
      if (e === 'hex') {
        let s = '';
        for (let i = 0; i < this.length; i++) s += (this[i] < 16 ? '0' : '') + this[i].toString(16);
        return s;
      }
      if (e === 'base64') {
        let s = '';
        for (let i = 0; i < this.length; i++) s += String.fromCharCode(this[i]);
        return encodeBase64(s);
      }
      // utf8
      let s = '';
      for (let i = 0; i < this.length; i++) {
        const b = this[i];
        if (b < 0x80) s += String.fromCharCode(b);
        else if (b >= 0xc0 && b < 0xe0) {
          s += String.fromCharCode(((b & 0x1f) << 6) | (this[++i] & 0x3f));
        } else if (b >= 0xe0 && b < 0xf0) {
          s += String.fromCharCode(((b & 0x0f) << 12) | ((this[++i] & 0x3f) << 6) | (this[++i] & 0x3f));
        } else s += String.fromCharCode(0xfffd);
      }
      return s;
    };
    return bytes;
  };
  BufferShim.isBuffer = function isBuffer(v) {
    return !!(v && typeof v === 'object' && typeof v.toString === 'function' && v.constructor === BufferShim);
  };
  BufferShim.alloc = function alloc(n) {
    return BufferShim.from(new Array(n).fill(0));
  };
  G0.Buffer = BufferShim;
}

// ---------------------------------------------------------------------------
// cheerio: ESM-only namespace -> plain object with the usual entry points
// ---------------------------------------------------------------------------
const cheerio = cheerioNs;

// ---------------------------------------------------------------------------
// dayjs: keep the callable function form + copy static helpers (.extend etc.)
// ---------------------------------------------------------------------------
const dayjs = typeof dayjsLib === 'function' ? dayjsLib : dayjsLib && dayjsLib.default;

// ---------------------------------------------------------------------------
// Qs / he: interop wrapper (ESM namespace or CJS object both fine)
// ---------------------------------------------------------------------------
const qsSrc0 = qsLib && qsLib.stringify ? qsLib : (qsLib && qsLib.default) || qsLib;
// qs 6.x keeps its defaults in `formats` and does NOT emit `+` for spaces unless
// the caller passes { format: 'RFC1738' }. QuickJS plugins expect the classic
// jQuery/Axios-style output, so make `+` the default while keeping the original
// object reachable for explicit opt-out.
function withRfc1738Default(qs) {
  if (!qs || !qs.formats || qs.formats.default === 'RFC1738' || qs.__gyDefault) return qs;
  const qsOrigin = qs;
  const qsWrapped = Object.create(qsOrigin);
  qsWrapped.formats = Object.assign({}, qsOrigin.formats, { default: 'RFC1738' });
  qsWrapped.stringify = function stringify(value, options) {
    const opts = options && typeof options === 'object' ? Object.assign({}, options) : options;
    if (opts && typeof opts === 'object' && opts.format === undefined) opts.format = 'RFC1738';
    return qsOrigin.stringify(value, opts || { format: 'RFC1738' });
  };
  qsWrapped.parse = function parse(input, options) {
    return qsOrigin.parse(input, options);
  };
  Object.defineProperty(qsWrapped, '__gyDefault', { value: true, enumerable: false });
  Object.defineProperty(qsWrapped, 'original', { value: qsOrigin, enumerable: false });
  return qsWrapped;
}
const qsSrc = withRfc1738Default(qsSrc0);
const heSrc = heNs;

// ---------------------------------------------------------------------------
// BigInteger: default export is a *builder function* returning a bigInt object.
// Plugins call either BigInteger(x) or new BigInteger(x); make both work and
// forward every property/method of the produced object.
// ---------------------------------------------------------------------------
const bigIntFactory = typeof bigIntLib === 'function' ? bigIntLib : bigIntLib && bigIntLib.default;

function BigInteger(value, base) {
  if (!(this instanceof BigInteger)) {
    return BigInteger.apply(Object.create(BigInteger.prototype), arguments);
  }
  const inner = arguments.length > 1 ? bigIntFactory(value, base) : bigIntFactory(value);
  Object.defineProperty(this, '__inner', { value: inner, enumerable: false });
  // mirror the live state onto `this` so String(this) / property reads work
  for (const k in inner) {
    if (Object.prototype.hasOwnProperty.call(inner, k)) {
      try {
        Object.defineProperty(this, k, {
          get: () => this.__inner[k],
          enumerable: true,
          configurable: true,
        });
      } catch (e) {
        /* non-gettable property: skip */
      }
    }
  }
  this.s = inner.s;
  this.value = inner.value;
  this.digits = inner.digits;
  return this;
}

BigInteger.prototype = Object.create(bigIntFactory.prototype || Object.prototype, {
  constructor: { value: BigInteger, writable: true, configurable: true },
});

// big-integer keeps the real method table on the objects returned by the
// factory, not on `factory.prototype`, so discover it from one sample value.
const innerProto = (function () {
  try {
    const sample = bigIntFactory(0);
    return Object.getPrototypeOf(sample) || bigIntFactory.prototype || {};
  } catch (e) {
    return bigIntFactory.prototype || {};
  }
})();
const protoMethodNames = Object.getOwnPropertyNames(innerProto || {}).filter(
  (k) => k !== 'constructor' && typeof innerProto[k] === 'function'
);

for (const name of protoMethodNames) {
  if (name === 'constructor' || BigInteger.prototype[name]) continue;
  const fn = innerProto[name];
  if (typeof fn !== 'function') continue;
  Object.defineProperty(BigInteger.prototype, name, {
    value: function () {
      const r = this.__inner[name].apply(this.__inner, arguments);
      return r && typeof r === 'object' && r.s !== undefined && !(r instanceof BigInteger)
        ? fromInner(r)
        : r;
    },
    writable: true,
    configurable: true,
  });
}

function fromInner(inner) {
  const inst = Object.create(BigInteger.prototype);
  Object.defineProperty(inst, '__inner', { value: inner, enumerable: false });
  for (const k in inner) {
    if (Object.prototype.hasOwnProperty.call(inner, k)) {
      try {
        Object.defineProperty(inst, k, {
          get: () => inst.__inner[k],
          enumerable: true,
          configurable: true,
        });
      } catch (e) {
        /* ignore */
      }
    }
  }
  inst.s = inner.s;
  inst.value = inner.value;
  inst.digits = inner.digits;
  return inst;
}

// static helpers copied from the factory (one, zero, max, min, fromArray ...)
for (const k of Object.keys(bigIntFactory)) {
  if (!(k in BigInteger)) BigInteger[k] = bigIntFactory[k];
}
for (const k of ['prototype', 'isInstance', 'fromArray', 'fromString', 'fromNumber', 'fromValue', 'parseValue', 'fromBigInt', 'fromBits', 'fromBase']) {
  if (typeof bigIntFactory[k] === 'function' && k !== 'prototype' && !(k in BigInteger)) {
    BigInteger[k] = bigIntFactory[k];
  }
}
BigInteger.one = bigIntFactory.one;
BigInteger.zero = bigIntFactory.zero;
BigInteger.minusOne = bigIntFactory.minusOne;
BigInteger.max = bigIntFactory.max;
BigInteger.min = bigIntFactory.min;
BigInteger['from'] = function (v) {
  return BigInteger(v);
};
BigInteger.isInstance = function (v) {
  return v instanceof BigInteger || (v && v.__inner && v.__inner.constructor === bigIntFactory);
};
BigInteger.toInner = function (v) {
  return v instanceof BigInteger ? v.__inner : v;
};

const g = globalThis;
g.cheerio = cheerio;
g.CryptoJS = CryptoJS;
g.dayjs = dayjs;
g.Qs = qsSrc;
g.he = heSrc;
g.BigInteger = BigInteger;

// expose for build-time sanity checks (harmless in QuickJS)
if (typeof globalThis.__GY_PRELUDE__ === 'undefined') {
  Object.defineProperty(globalThis, '__GY_PRELUDE__', {
    value: { version: '1', libs: ['cheerio', 'CryptoJS', 'dayjs', 'Qs', 'he', 'BigInteger'] },
    enumerable: false,
    configurable: true,
    writable: true,
  });
}
