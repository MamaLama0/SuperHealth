// Version low-poly (triangles à plat) d'une image : points denses sur les contours, fond en grands triangles.
// usage: node tools/lowpoly_assets.mjs <mode> <in> <out> [<in> <out>]...
//   mode ex     : photo d'exercice 360×640, recadrée sur la personne et le matériel
//   mode flat   : image opaque (illustrations), même cadre
//   mode alpha  : image détourée (muscles, repères, logos), transparence conservée
//   mode eq     : équipement détouré, plus de points pour garder les barres fines
import { execFileSync } from "node:child_process";
import Delaunator from "delaunator"; // npm install delaunator@5.0.1

const [mode, ...pairs] = process.argv.slice(2);

function info(path) {
  const [w, h] = execFileSync("magick", ["identify", "-format", "%w %h", path + "[0]"]).toString().split(" ").map(Number);
  return { w, h };
}
function decode(path, args, w, h) {
  const d = execFileSync("magick", [path + "[0]", ...args, "-alpha", "on", "-depth", "8", "rgba:-"], { maxBuffer: 1 << 28 });
  return { w, h, d };
}

// graine fixe par fichier : même entrée, même sortie
function rng(seed) {
  let s = seed >>> 0 || 1;
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296);
}
const hash = (str) => [...str].reduce((a, c) => (a * 31 + c.charCodeAt(0)) >>> 0, 7);

const lum = (d, i) => 0.299 * d[i] + 0.587 * d[i + 1] + 0.114 * d[i + 2];

/** Cadre 9:16 autour de la personne et du matériel (tout ce qui se détache du fond studio). */
function subjectCrop(img) {
  const { w, h, d } = img;
  // fond : luminance de chaque ligne mesurée sur les bords gauche et droit
  const bg = new Float32Array(h);
  for (let y = 0; y < h; y++) {
    const v = [];
    for (let x = 0; x < 16; x++) v.push(lum(d, (y * w + x) * 4), lum(d, (y * w + w - 1 - x) * 4));
    v.sort((a, b) => a - b); bg[y] = v[v.length >> 1];
  }
  const cols = new Int32Array(w), rows = new Int32Array(h);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4;
    const sat = Math.max(d[i], d[i + 1], d[i + 2]) - Math.min(d[i], d[i + 1], d[i + 2]);
    if (Math.abs(lum(d, i) - bg[y]) > 26 || sat > 40) { cols[x]++; rows[y]++; }
  }
  const span = (a, min) => { let lo = 0, hi = a.length - 1; while (lo < hi && a[lo] < min) lo++; while (hi > lo && a[hi] < min) hi--; return [lo, hi]; };
  let [x0, x1] = span(cols, 3), [y0, y1] = span(rows, 3);
  if (x1 - x0 < 20 || y1 - y0 < 20) return { x: 0, y: 0, cw: w, ch: h };
  const mx = (x1 - x0) * 0.1 + 8, my = (y1 - y0) * 0.08 + 8;
  x0 -= mx; x1 += mx; y0 -= my; y1 += my;
  let cw = Math.max(x1 - x0, ((y1 - y0) * 9) / 16, w / 1.7);
  cw = Math.min(cw, w, (h * 9) / 16);
  const ch = (cw * 16) / 9;
  let x = (x0 + x1) / 2 - cw / 2, y = (y0 + y1) / 2 - ch / 2;
  x = Math.max(0, Math.min(w - cw, x)); y = Math.max(0, Math.min(h - ch, y));
  return { x: Math.round(x), y: Math.round(y), cw: Math.round(cw), ch: Math.round(ch) };
}

/** Intensité des contours (Sobel sur la couleur et l'alpha), légèrement lissée. */
function edges(img) {
  const { w, h, d } = img;
  const g = new Float32Array(w * h);
  const ch = (x, y, c) => d[(Math.min(h - 1, Math.max(0, y)) * w + Math.min(w - 1, Math.max(0, x))) * 4 + c];
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    let m = 0;
    for (let c = 0; c < 4; c++) {
      const gx = ch(x + 1, y - 1, c) + 2 * ch(x + 1, y, c) + ch(x + 1, y + 1, c) - ch(x - 1, y - 1, c) - 2 * ch(x - 1, y, c) - ch(x - 1, y + 1, c);
      const gy = ch(x - 1, y + 1, c) + 2 * ch(x, y + 1, c) + ch(x + 1, y + 1, c) - ch(x - 1, y - 1, c) - 2 * ch(x, y - 1, c) - ch(x + 1, y - 1, c);
      m = Math.max(m, Math.hypot(gx, gy));
    }
    g[y * w + x] = m;
  }
  return g;
}

function points(img, count, seed, minDist) {
  const { w, h } = img;
  const r = rng(seed);
  const pts = [];
  // bords et coins
  const nb = 6;
  for (let k = 0; k <= nb; k++) {
    const t = k / nb;
    pts.push([t * (w - 1), 0], [t * (w - 1), h - 1], [0, t * (h - 1)], [w - 1, t * (h - 1)]);
  }
  // fond : grille grossière et décalée
  const cell = Math.max(w, h) / 5;
  for (let y = cell / 2; y < h; y += cell) for (let x = cell / 2; x < w; x += cell)
    pts.push([x + (r() - 0.5) * cell * 0.6, y + (r() - 0.5) * cell * 0.6]);
  // contours : tirage pondéré, avec une distance minimale entre points
  const g = edges(img);
  const cand = [];
  for (let i = 0; i < w * h; i++) if (g[i] > 40) cand.push([i, g[i] * (0.4 + r())]);
  cand.sort((a, b) => b[1] - a[1]);
  const grid = new Map();
  const gk = (x, y) => ((x / minDist) | 0) + "," + ((y / minDist) | 0);
  const ok = (x, y) => {
    const cx = (x / minDist) | 0, cy = (y / minDist) | 0;
    for (let dy = -1; dy <= 1; dy++) for (let dx = -1; dx <= 1; dx++)
      for (const [px, py] of grid.get(cx + dx + "," + (cy + dy)) || []) if ((px - x) ** 2 + (py - y) ** 2 < minDist * minDist) return false;
    return true;
  };
  let added = 0;
  for (const [i] of cand) {
    if (added >= count) break;
    const x = i % w, y = (i / w) | 0;
    if (!ok(x, y)) continue;
    const k = gk(x, y);
    if (!grid.has(k)) grid.set(k, []);
    grid.get(k).push([x, y]);
    pts.push([x, y]);
    added++;
  }
  return pts;
}

/** Triangles colorés par la moyenne des pixels qu'ils couvrent ; rendu ×2 puis réduit (bords lissés). */
let FACET = 0.05;
const seedTri = (k) => { let x = Math.sin(k * 12.9898) * 43758.5453; return x - Math.floor(x); };
function render(img, pts, outW, outH, keepAlpha) {
  const { w, h, d } = img;
  const del = Delaunator.from(pts);
  const t = del.triangles;
  const S = 2, W = outW * S, H = outH * S, sx = W / w, sy = H / h;
  const out = Buffer.alloc(W * H * 4);
  const tri = (ax, ay, bx, by, cx, cy, fn) => {
    const minX = Math.max(0, Math.floor(Math.min(ax, bx, cx))), maxX = Math.min(Math.ceil(Math.max(ax, bx, cx)), Infinity);
    const minY = Math.max(0, Math.floor(Math.min(ay, by, cy))), maxY = Math.ceil(Math.max(ay, by, cy));
    const area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax);
    if (area === 0) return;
    for (let y = minY; y <= maxY; y++) for (let x = minX; x <= maxX; x++) {
      const px = x + 0.5, py = y + 0.5;
      const w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area;
      const w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area;
      const w2 = 1 - w0 - w1;
      if (w0 >= -1e-6 && w1 >= -1e-6 && w2 >= -1e-6) fn(x, y);
    }
  };
  for (let k = 0; k < t.length; k += 3) {
    const [a, b, c] = [pts[t[k]], pts[t[k + 1]], pts[t[k + 2]]];
    let n = 0, R = 0, G = 0, B = 0, A = 0;
    tri(a[0], a[1], b[0], b[1], c[0], c[1], (x, y) => {
      if (x >= w || y >= h) return;
      const i = (y * w + x) * 4, al = d[i + 3];
      R += d[i] * al; G += d[i + 1] * al; B += d[i + 2] * al; A += al; n++;
    });
    if (!n) { const i = ((Math.round((a[1] + b[1] + c[1]) / 3) * w) + Math.round((a[0] + b[0] + c[0]) / 3)) * 4; R = d[i] * 255; G = d[i + 1] * 255; B = d[i + 2] * 255; A = 255; n = 1; }
    const alpha = A / n;
    if (keepAlpha && alpha < 128) continue;
    // facettes : chaque triangle un peu plus clair ou plus sombre selon son orientation (lumière en haut à gauche)
    const nx = (b[1] - a[1]) * (c[0] - a[0]) - (b[0] - a[0]) * (c[1] - a[1]);
    const shade = 1 + FACET * Math.sin(seedTri(k) * 6.283 + nx * 1e-9) ;
    const col = A ? [R / A, G / A, B / A].map((v) => Math.max(0, Math.min(255, v * shade))) : [0, 0, 0];
    tri(a[0] * sx, a[1] * sy, b[0] * sx, b[1] * sy, c[0] * sx, c[1] * sy, (x, y) => {
      if (x >= W || y >= H) return;
      const i = (y * W + x) * 4;
      out[i] = col[0]; out[i + 1] = col[1]; out[i + 2] = col[2]; out[i + 3] = keepAlpha ? 255 : 255;
    });
  }
  return { W, H, out };
}

function run(inp, outp) {
  const { w, h } = info(inp);
  const seed = hash(inp.split("/").pop());
  let img, outW = w, outH = h, count, minDist;
  if (mode === "ex") {
    const full = decode(inp, [], w, h);
    const c = subjectCrop(full);
    const aw = 540, ah = 960;
    img = decode(inp, ["-crop", `${c.cw}x${c.ch}+${c.x}+${c.y}`, "+repage", "-resize", `${aw}x${ah}!`], aw, ah);
    count = 650; minDist = 10;
  } else {
    const scale = Math.min(1, 720 / Math.max(w, h)) * (Math.max(w, h) < 300 ? 2 : 1);
    const aw = Math.round(w * scale), ah = Math.round(h * scale);
    img = decode(inp, ["-resize", `${aw}x${ah}!`], aw, ah);
    const big = Math.max(aw, ah);
    count = mode === "flat" ? 420 : mode === "eq" ? 1100 : 300; minDist = big / (mode === "eq" ? 110 : 45);
  }
  const pts = points(img, count, seed, minDist);
  const { W, H, out } = render(img, pts, outW, outH, mode !== "flat");
  const fmt = outp.endsWith(".png") ? [] : ["-quality", "88"];
  execFileSync("magick", ["-size", `${W}x${H}`, "-depth", "8", "rgba:-", "-filter", "box", "-resize", `${outW}x${outH}!`, ...fmt, outp], { input: out });
}

for (let i = 0; i < pairs.length; i += 2) {
  try { run(pairs[i], pairs[i + 1]); } catch (e) { console.error("FAIL", pairs[i], e.message.split("\n")[0]); }
}
