// Post-build patch: make the bundle self-bootstrapping on bare ES2020 runtimes
// (QuickJS: no atob / Buffer). Appends a tiny bootstrap at the very TOP of the
// IIFE body: it defines globalThis.atob and a minimal Buffer before any module
// factory runs, so the load-time base64 entity table in htmlparser2/entities
// decodes instead of throwing "Buffer is not defined".
//
// Kept as a separate step (not an esbuild plugin) so build.mjs stays readable.
export const BOOTSTRAP = String.raw`var __gy_b64m="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
function __gy_atob(u){var s=String(u).replace(/[\t\n\f\r ]+/g,"").replace(/=+$/,"");if(s.length%4===1)throw new Error("atob: invalid base64");var o="",b=0,n=0;for(var i=0;i<s.length;i++){var k=__gy_b64m.indexOf(s.charAt(i));if(k<0)throw new Error("atob: invalid base64 character");b=b<<6|k;n+=6;if(n>=8){n-=8;o+=String.fromCharCode(b>>n&255)}}return o}
function __gy_btoa(u){var s=String(u),o="";for(var i=0;i<s.length;i+=3){var c1=s.charCodeAt(i),c2=i+1<s.length?s.charCodeAt(i+1):0,c3=i+2<s.length?s.charCodeAt(i+2):0;o+=__gy_b64m.charAt(c1>>2);o+=__gy_b64m.charAt((c1&3)<<4|(c2>>4));o+=i+1<s.length?__gy_b64m.charAt((c2&15)<<2|(c3>>6)):"=";o+=i+2<s.length?__gy_b64m.charAt(c3&63):"="}return o}
if(typeof globalThis.atob!=="function")globalThis.atob=__gy_atob;
if(typeof globalThis.btoa!=="function")globalThis.btoa=__gy_btoa;
if(typeof globalThis.Buffer==="undefined"){(function(){function B(v,e){if(!(this instanceof B))return B.from(v,e);var a=B.from(v,e);this.length=a.length;for(var i=0;i<a.length;i++)this[i]=a[i];return this}
B.from=function(v,e){var out=[];if(typeof v==="string"){var enc=String(e||"utf8").toLowerCase();if(enc==="base64"){var d=__gy_atob(v);for(var i=0;i<d.length;i++)out.push(d.charCodeAt(i)&255)}else if(enc==="hex"){for(var i=0;i<v.length;i+=2)out.push(parseInt(v.slice(i,i+2),16)||0)}else{for(var i=0;i<v.length;i++){var c=v.charCodeAt(i);if(c<128)out.push(c);else if(c<2048)out.push(192|c>>6,128|c&63);else out.push(224|c>>12,128|c>>6&63,128|c&63)}}}else if(v&&typeof v.length==="number"){for(var i=0;i<v.length;i++)out.push(v[i]&255)}
out.toString=function(enc){var en=String(enc||"utf8").toLowerCase(),s="",i;if(en==="binary"||en==="latin1"){for(i=0;i<this.length;i++)s+=String.fromCharCode(this[i]);return s}if(en==="hex"){for(i=0;i<this.length;i++)s+=(this[i]<16?"0":"")+this[i].toString(16);return s}if(en==="base64"){for(i=0;i<this.length;i++)s+=String.fromCharCode(this[i]);return __gy_btoa(s)}for(i=0;i<this.length;i++){var b=this[i];if(b<128)s+=String.fromCharCode(b);else if(b>=192&&b<224)s+=String.fromCharCode((b&31)<<6|this[++i]&63);else if(b>=224&&b<240)s+=String.fromCharCode((b&15)<<12|(this[++i]&63)<<6|this[++i]&63);else s+=String.fromCharCode(65533)}return s};return out};
B.isBuffer=function(v){return !!(v&&typeof v==="object"&&v.constructor===B)};
B.alloc=function(n){var a=new Array(n);for(var i=0;i<n;i++)a[i]=0;return B.from(a)};
globalThis.Buffer=B})()}
`;

export function applyBootstrap(code) {
  const marker = '(()=>{';
  const at = code.indexOf(marker);
  if (at !== 0) {
    throw new Error('unexpected iife prologue: ' + JSON.stringify(code.slice(0, 16)));
  }
  return marker + BOOTSTRAP + code.slice(marker.length);
}
