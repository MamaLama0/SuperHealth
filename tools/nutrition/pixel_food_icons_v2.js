// Pixel-art 16×16 dans le style du pack, plus fidèle à l'icône d'origine (v2).
// usage: node gen2.js <sheet16.rgba> <outdir> <options json> <id:path.webp>...
// options : inner (14 = contour autour, 16 = contour à l'intérieur de la forme), thr (couverture minimale d'une case),
//           snap (écart de couleur sous lequel on prend la couleur du pack), sep (traits sombres entre zones de couleurs
//           différentes), teal (vaisselle turquoise -> blanche), zoom (<1 = icône plus petite dans la case)
const fs = require("fs");
const { execFileSync } = require("child_process");

const [sheetPath, outDir, optJson, ...jobs] = process.argv.slice(2);
const O = { inner: 14, thr: 0.3, snap: 14, sep: 0, teal: false, zoom: 1, ...JSON.parse(optJson) };
const N = 16;

const sheet = fs.readFileSync(sheetPath);
const freq = new Map();
for (let i = 0; i < sheet.length; i += 4) {
  if (sheet[i + 3] < 128) continue;
  const k = (sheet[i] << 16) | (sheet[i + 1] << 8) | sheet[i + 2];
  freq.set(k, (freq.get(k) || 0) + 1);
}
const pal = [...freq].filter(([, n]) => n >= 12).map(([k]) => [(k >> 16) & 255, (k >> 8) & 255, k & 255]);

function lab([r, g, b]) {
  const f = (c) => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
  const R = f(r), G = f(g), B = f(b);
  let X = (R * 0.4124 + G * 0.3576 + B * 0.1805) / 0.95047, Y = R * 0.2126 + G * 0.7152 + B * 0.0722, Z = (R * 0.0193 + G * 0.1192 + B * 0.9505) / 1.08883;
  const h = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  X = h(X); Y = h(Y); Z = h(Z);
  return [116 * Y - 16, 500 * (X - Y), 200 * (Y - Z)];
}
const palLab = pal.map(lab);
const dE = (a, b) => Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
/** Couleur du pack la plus proche si elle est assez proche, sinon la couleur d'origine. */
function settle(c) {
  const L = lab(c);
  let best = -1, bd = 1e9;
  for (let i = 0; i < pal.length; i++) { const d = dE(L, palLab[i]); if (d < bd) { bd = d; best = i; } }
  return bd <= O.snap ? pal[best] : c.map(Math.round);
}
const mix = (c, t, a) => c.map((v, j) => Math.round(v * (1 - a) + t[j] * a));
const shadow = (c, a) => mix(c, [40, 30, 70], a);
const light = (c, a) => mix(c, [255, 245, 200], a);
const ink = (c) => mix(c.map((v) => v * 0.3), [24, 20, 37], 0.35);

const near = (c, t, r) => (c[0] - t[0]) ** 2 + (c[1] - t[1]) ** 2 + (c[2] - t[2]) ** 2 < r * r;
function plate(c) {
  if (!O.teal) return c;
  if (near(c, [0, 138, 146], 42)) return [238, 242, 248];
  if (near(c, [1, 99, 111], 36)) return [192, 203, 220];
  return c;
}

function decode(path) {
  // pre : réduction de qualité (Lanczos) à la taille finale puis peu de couleurs, au lieu de la couleur dominante par case
  const pre = O.pre ? (() => {
    const [w, h] = execFileSync("magick", [path, "-trim", "-format", "%w %h", "info:"]).toString().split(" ").map(Number);
    const s = Math.round(O.inner * O.zoom), k = s / Math.max(w, h);
    return ["-filter", "Lanczos", "-resize", `${Math.max(1, Math.round(w * k))}x${Math.max(1, Math.round(h * k))}!`, "-channel", "A", "-threshold", "45%", "+channel", "+dither", "-colors", String(O.pre)];
  })() : [];
  const args = [path, "-background", "none", "-trim", "+repage", ...pre];
  const raw = execFileSync("magick", [...args, "-depth", "8", "rgba:-"]);
  const info = execFileSync("magick", [...args, "-format", "%w %h", "info:"]).toString().split(" ").map(Number);
  return { w: info[0], h: info[1], d: raw };
}

function gen(id, path) {
  const { w, h, d } = decode(path);
  const inner = O.inner;
  const pad = (N - inner) / 2;
  const side = O.pre ? inner : Math.max(w, h) / O.zoom;
  const ox = (side - w) / 2, oy = (side - h) / 2;
  const cell = side / inner;
  // case par case : couverture et couleur dominante (moyenne du groupe de couleurs le plus fréquent)
  const col = new Array(N * N).fill(null);
  for (let cy = 0; cy < inner; cy++) for (let cx = 0; cx < inner; cx++) {
    const x0 = cx * cell - ox, y0 = cy * cell - oy;
    const groups = new Map(); let tot = 0, cov = 0;
    for (let y = Math.floor(y0); y < Math.ceil(y0 + cell); y++) for (let x = Math.floor(x0); x < Math.ceil(x0 + cell); x++) {
      tot++;
      if (x < 0 || y < 0 || x >= w || y >= h) continue;
      const i = (y * w + x) * 4;
      if (d[i + 3] < 160) continue;
      cov++;
      const c = plate([d[i], d[i + 1], d[i + 2]]);
      const k = (c[0] >> 4) * 256 + (c[1] >> 4) * 16 + (c[2] >> 4);
      const g = groups.get(k) || { n: 0, s: [0, 0, 0] };
      g.n++; g.s[0] += c[0]; g.s[1] += c[1]; g.s[2] += c[2];
      groups.set(k, g);
    }
    if (!tot || cov / tot < O.thr) continue;
    let best = null;
    for (const g of groups.values()) if (!best || g.n > best.n) best = g;
    col[(cy + pad) * N + cx + pad] = best.s.map((v) => v / best.n);
  }
  const at = (x, y) => (x < 0 || y < 0 || x >= N || y >= N ? null : col[y * N + x]);
  const empty = (x, y) => at(x, y) === null;
  const out = new Array(N * N).fill(null);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const c = at(x, y);
    if (!c) {
      // contour extérieur (mode 14) : autour de la forme
      if (inner < N) {
        const nb = [[1, 0], [-1, 0], [0, 1], [0, -1]].map(([dx, dy]) => at(x + dx, y + dy)).filter(Boolean);
        if (nb.length) out[y * N + x] = ink(nb.reduce((a, b) => (lab(b)[0] < lab(a)[0] ? b : a)));
      }
      continue;
    }
    const edge = empty(x + 1, y) || empty(x - 1, y) || empty(x, y + 1) || empty(x, y - 1);
    if (inner === N && edge) { out[y * N + x] = ink(c); continue; }
    let v = c;
    const nearEdge = (dx, dy) => (inner === N ? empty(x + 2 * dx, y + 2 * dy) || empty(x + dx, y + dy) : empty(x + dx, y + dy));
    if (nearEdge(1, 0) || nearEdge(0, 1)) v = shadow(c, 0.26);
    else if ((nearEdge(-1, 0) || nearEdge(0, -1)) && lab(c)[0] < 80) v = light(c, 0.14);
    // trait sombre entre deux zones très différentes : sur la plus sombre des deux
    if (O.sep) for (const [dx, dy] of [[1, 0], [0, 1], [-1, 0], [0, -1]]) {
      const q = at(x + dx, y + dy);
      if (q && dE(lab(c), lab(q)) > O.sep && lab(c)[0] < lab(q)[0] - 4 && (dx + dy > 0)) { v = shadow(c, 0.55); break; }
    }
    out[y * N + x] = v;
  }
  const buf = Buffer.alloc(N * N * 4);
  for (let i = 0; i < N * N; i++) {
    if (!out[i]) continue;
    const c = settle(out[i]);
    buf[i * 4] = c[0]; buf[i * 4 + 1] = c[1]; buf[i * 4 + 2] = c[2]; buf[i * 4 + 3] = 255;
  }
  execFileSync("magick", ["-size", `${N}x${N}`, "-depth", "8", "rgba:-", `${outDir}/${id}.png`], { input: buf });
}

for (const j of jobs) {
  const k = j.indexOf(":");
  gen(j.slice(0, k), j.slice(k + 1));
}
