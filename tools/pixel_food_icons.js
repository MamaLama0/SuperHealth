// Pixel-art 16x16 dans le style du pack, à partir des icônes plates MacroFactor.
// usage: node tools/pixel_food_icons.js <IconSheet 16x16 en rgba brut, via magick ... rgba:-> <outdir> <id:path.webp>...
const fs = require("fs");
const { execFileSync } = require("child_process");

const [sheetPath, outDir, ...jobs] = process.argv.slice(2);
const N = 16, INNER = 14;

// ---------- palette du pack (couleurs utilisées au moins 12 fois) ----------
const sheet = fs.readFileSync(sheetPath);
const freq = new Map();
for (let i = 0; i < sheet.length; i += 4) {
  if (sheet[i + 3] < 128) continue;
  const k = (sheet[i] << 16) | (sheet[i + 1] << 8) | sheet[i + 2];
  freq.set(k, (freq.get(k) || 0) + 1);
}
const pal = [...freq].filter(([, n]) => n >= 12).map(([k]) => [(k >> 16) & 255, (k >> 8) & 255, k & 255]);

// ---------- couleur : Lab ----------
function lab([r, g, b]) {
  const f = (c) => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
  const R = f(r), G = f(g), B = f(b);
  let X = (R * 0.4124 + G * 0.3576 + B * 0.1805) / 0.95047, Y = R * 0.2126 + G * 0.7152 + B * 0.0722, Z = (R * 0.0193 + G * 0.1192 + B * 0.9505) / 1.08883;
  const h = (t) => (t > 0.008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116);
  X = h(X); Y = h(Y); Z = h(Z);
  return [116 * Y - 16, 500 * (X - Y), 200 * (Y - Z)];
}
const palLab = pal.map(lab);
const cache = new Map();
function nearest(c, filter) {
  const key = c.join(",") + (filter ? "f" + filter.name : "");
  if (cache.has(key)) return cache.get(key);
  const L = lab(c);
  let best = 0, bd = 1e9;
  for (let i = 0; i < pal.length; i++) {
    if (filter && !filter(i)) continue;
    const p = palLab[i];
    // la saturation compte double : les icônes du pack sont franches
    const d = (L[0] - p[0]) ** 2 + 1.6 * ((L[1] - p[1]) ** 2 + (L[2] - p[2]) ** 2);
    if (d < bd) { bd = d; best = i; }
  }
  cache.set(key, best);
  return best;
}
const dark = (i) => palLab[i][0] < 30;

// ---------- teintes : ombre vers le violet, lumière vers le jaune ----------
function shift(c, k) {
  // k<0 ombre, k>0 lumière (mélange avec une teinte froide/chaude)
  const t = k < 0 ? [40, 30, 70] : [255, 245, 200];
  const a = Math.abs(k);
  return c.map((v, j) => Math.round(v * (1 - a) + t[j] * a));
}

// bols et assiettes turquoise de MacroFactor -> vaisselle blanche du pack
const near = (c, t, r) => (c[0] - t[0]) ** 2 + (c[1] - t[1]) ** 2 + (c[2] - t[2]) ** 2 < r * r;
function plate(c) {
  if (near(c, [0, 138, 146], 42)) return [238, 242, 248];
  if (near(c, [1, 99, 111], 36)) return [192, 203, 220];
  return c;
}

function decode(path) {
  const raw = execFileSync("magick", [path, "-background", "none", "-trim", "+repage", "-depth", "8", "rgba:-"]);
  const info = execFileSync("magick", [path, "-trim", "-format", "%w %h", "info:"]).toString().split(" ").map(Number);
  return { w: info[0], h: info[1], d: raw };
}

function gen(id, path) {
  const { w, h, d } = decode(path);
  const side = Math.max(w, h);
  const ox = (side - w) / 2, oy = (side - h) / 2;
  // couleur de palette de chaque pixel source opaque
  const q = new Int16Array(w * h).fill(-1);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4;
    if (d[i + 3] >= 160) q[y * w + x] = nearest(plate([d[i], d[i + 1], d[i + 2]]));
  }
  // réduction : couverture + couleur dominante par cellule
  const cell = side / INNER;
  const fill = new Int16Array(N * N).fill(-1);
  for (let cy = 0; cy < INNER; cy++) for (let cx = 0; cx < INNER; cx++) {
    const x0 = cx * cell - ox, y0 = cy * cell - oy;
    const votes = new Map(); let tot = 0, cov = 0;
    for (let y = Math.floor(y0); y < Math.ceil(y0 + cell); y++) for (let x = Math.floor(x0); x < Math.ceil(x0 + cell); x++) {
      tot++;
      if (x < 0 || y < 0 || x >= w || y >= h) continue;
      const v = q[y * w + x];
      if (v < 0) continue;
      cov++;
      votes.set(v, (votes.get(v) || 0) + 1);
    }
    if (tot === 0 || cov / tot < 0.4) continue;
    let best = -1, bn = 0;
    for (const [v, n] of votes) if (n > bn) { bn = n; best = v; }
    fill[(cy + 1) * N + cx + 1] = best;
  }
  // ombre (bas/droite près du bord) et lumière (haut/gauche près du bord)
  const at = (x, y) => (x < 0 || y < 0 || x >= N || y >= N ? -1 : fill[y * N + x]);
  const out = new Int16Array(N * N).fill(-1);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    const v = at(x, y); if (v < 0) continue;
    const c = pal[v];
    let k = 0;
    if (at(x + 1, y) < 0 || at(x, y + 1) < 0 || at(x + 1, y + 1) < 0) k = -0.28;
    else if ((at(x - 1, y) < 0 || at(x, y - 1) < 0) && palLab[v][0] < 80) k = 0.15;
    out[y * N + x] = k ? nearest(shift(c, k)) : v;
  }
  // contour 1 px (voisinage en croix), teinté d'après le pixel voisin le plus sombre
  const line = new Int16Array(N * N).fill(-1);
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) {
    if (out[y * N + x] >= 0) continue;
    const nb = [[1, 0], [-1, 0], [0, 1], [0, -1]].map(([dx, dy]) => at(x + dx, y + dy)).filter((v) => v >= 0);
    if (!nb.length) continue;
    const v0 = nb.reduce((a, b) => (palLab[b][0] < palLab[a][0] ? b : a));
    line[y * N + x] = nearest(pal[v0].map((v) => Math.round(v * 0.28)), dark);
  }
  const buf = Buffer.alloc(N * N * 4);
  for (let i = 0; i < N * N; i++) {
    const v = out[i] >= 0 ? out[i] : line[i];
    if (v < 0) continue;
    const c = pal[v];
    buf[i * 4] = c[0]; buf[i * 4 + 1] = c[1]; buf[i * 4 + 2] = c[2]; buf[i * 4 + 3] = 255;
  }
  execFileSync("magick", ["-size", `${N}x${N}`, "-depth", "8", "rgba:-", `${outDir}/${id}.png`], { input: buf });
}

for (const j of jobs) {
  const k = j.indexOf(":");
  gen(j.slice(0, k), j.slice(k + 1));
}
