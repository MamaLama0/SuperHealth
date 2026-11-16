// 3e passe : icônes dessinées à la main (pieuvre, calamar, cerf, tournesol, assiette et couverts, cacahuète, nuggets).
// usage: node hand2.js <outdir>
const { execFileSync } = require("child_process");
const [outDir] = process.argv.slice(2);
const N = 16;
const hex = (h) => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];

function write(name, px) {
  const buf = Buffer.alloc(N * N * 4);
  px.forEach((c, i) => { if (c) { buf[i * 4] = c[0]; buf[i * 4 + 1] = c[1]; buf[i * 4 + 2] = c[2]; buf[i * 4 + 3] = 255; } });
  execFileSync("magick", ["-size", `${N}x${N}`, "-depth", "8", "rgba:-", `${outDir}/${name}.png`], { input: buf });
}
/** Remplissage par fonction puis contour 1 px dans la forme ; [keep] = couleurs jamais remplacées par le contour. */
function draw(fill, outline, keep = []) {
  const px = [];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) px.push(fill(x + 0.5, y + 0.5));
  const empty = (x, y) => x < 0 || y < 0 || x >= N || y >= N || !px[y * N + x];
  return px.map((c, i) => {
    if (!c || keep.includes(c)) return c;
    const x = i % N, y = (i / N) | 0;
    return empty(x + 1, y) || empty(x - 1, y) || empty(x, y + 1) || empty(x, y - 1) ? outline(c) : c;
  });
}
/** Dessin en caractères : une ligne de 16 caractères par rangée, '.' = transparent. */
function ascii(rows, pal) {
  return rows.join("").split("").map((ch) => (ch === "." ? null : hex(pal[ch])));
}
const ink = (c) => c.map((v, j) => Math.round(v * 0.3 * 0.65 + [24, 20, 37][j] * 0.35));
const segDist = (x, y, ax, ay, bx, by) => {
  const vx = bx - ax, vy = by - ay, t = Math.max(0, Math.min(1, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)));
  return Math.hypot(x - (ax + t * vx), y - (ay + t * vy));
};
/** Distance à une polyligne. */
const polyDist = (x, y, pts) => { let d = 1e9; for (let i = 1; i < pts.length; i++) d = Math.min(d, segDist(x, y, ...pts[i - 1], ...pts[i])); return d; };

// Pieuvre : tête ronde, yeux, six tentacules ondulés qui s'écartent.
{
  const o = hex("f77622"), oHi = hex("feae34"), oLo = hex("c64524"), eye = hex("181425"), white = hex("ffffff");
  const legs = [-2.5, -1.5, -0.5, 0.5, 1.5, 2.5].map((k) => {
    const pts = [];
    for (let t = 0; t <= 1.0001; t += 0.1) pts.push([8 + k * 1.15 + k * 0.9 * t + Math.sin(t * 5 + k) * 0.6, 8.5 + t * 6.2]);
    return pts;
  });
  write("i216", draw((x, y) => {
    const head = ((x - 8) / 4.8) ** 2 + ((y - 5.6) / 4.6) ** 2;
    if (head <= 1) {
      if ((Math.abs(x - 6) < 0.6 || Math.abs(x - 10) < 0.6) && Math.abs(y - 6.5) < 0.6) return eye;
      if ((Math.abs(x - 5.9) < 0.6 || Math.abs(x - 9.9) < 0.6) && Math.abs(y - 5.5) < 0.6) return white;
      if (x + y < 9.5) return oHi;
      return x - 8 + (y - 5.6) > 3 ? oLo : o;
    }
    if (legs.some((p) => polyDist(x, y, p) < 0.95)) return y > 12 ? oLo : o;
    return null;
  }, () => hex("6e2727"), [eye, white]));
}

// Calamar : manteau pointu avec nageoires en haut, deux yeux, tentacules fins dont deux longs.
{
  const m = hex("f6757a"), mHi = hex("fbb8b8"), mLo = hex("c84858"), eye = hex("181425");
  write("i228", draw((x, y) => {
    // nageoires (losange en haut)
    const fin = Math.abs(x - 8) / 5 + Math.abs(y - 2.6) / 2.3 <= 1;
    // manteau : fuseau du sommet (y=0.5) jusqu'à y=9
    const half = y < 0.5 || y > 9.5 ? -1 : Math.min(2.8, 0.6 + (y - 0.5) * 0.7) - Math.max(0, y - 8) * 0.6;
    const body = Math.abs(x - 8) <= half;
    if (fin || body) {
      if (Math.abs(y - 8.3) < 0.6 && (Math.abs(x - 6.6) < 0.6 || Math.abs(x - 9.4) < 0.6)) return eye;
      if (x < 7.2 && !fin) return mHi;
      return x > 9 ? mLo : m;
    }
    const tent = [-2.2, -1.1, 0, 1.1, 2.2].some((k) => segDist(x, y, 8 + k * 0.9, 9.5, 8 + k * 1.5, 13.2) < 0.55);
    const longs = [-1, 1].some((s) => polyDist(x, y, [[8 + s * 0.6, 9.5], [8 + s * 1.8, 13], [8 + s * 2.4, 15.2]]) < 0.6);
    if (tent || longs) return mLo;
    return null;
  }, () => hex("6b2438"), [eye]));
}

// Cerf de profil, tourné vers la gauche : bois, oreille, œil, queue blanche.
write("i209", ascii([
  ".k.k.k..........",
  ".kckck..........",
  "..kcck..........",
  "..kBBkk.........",
  ".kBBBBk.........",
  "kBkBBBk.........",
  "kBBBBBBkkkkkkk..",
  ".kkkBBBBBBBBBBkw",
  "...kBBBBBBBBBBkk",
  "...kBbbbbbbbBBk.",
  "...kBkkkkkkkBBk.",
  "...kBk.....kBkk.",
  "..kBBk.....kBkBk",
  "..kBk......kBkBk",
  "..kDk......kDkDk",
  "..kk.......kk.kk",
], { k: "3f2832", B: "b86f50", b: "e4a672", D: "743f39", c: "ead4aa", w: "ffffff" }));

// Tournesol : huit pétales séparés autour d'un gros cœur brun, tige et deux feuilles.
{
  const pet = hex("feae34"), petHi = hex("fee761"), petLo = hex("f77622"), core = hex("743f39"), seed = hex("3f2832"),
    stem = hex("3e8948"), leaf = hex("63c74d");
  const cx = 8, cy = 6.2;
  write("i416", draw((x, y) => {
    const dx = x - cx, dy = y - cy, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    if (r < 2.6) return (Math.floor(x) + Math.floor(y)) % 2 ? seed : core;
    const k = Math.round(a / (Math.PI / 4)), da = Math.abs(a - k * (Math.PI / 4));
    if (r < 6 && r >= 2.6 && da * r < 1.25) return dx + dy > 2 ? petLo : dx + dy < -2 ? petHi : pet;
    if (Math.abs(x - 8) < 0.7 && y > 8.5) return stem;
    if (((x - 5.2) / 2.2) ** 2 + ((y - 12.3) / 1.0) ** 2 <= 1 && x < 8) return leaf;
    if (((x - 10.8) / 2.2) ** 2 + ((y - 13.4) / 1.0) ** 2 <= 1 && x > 8) return leaf;
    return null;
  }, (c) => (c === leaf || c === stem ? hex("265c42") : c === core || c === seed ? hex("3f2832") : hex("9a4a1c")), [seed]));
}

// Assiette turquoise entre une fourchette et un couteau, bien séparés.
{
  const rim = hex("1f9ea3"), center = hex("5fd0cf"), metal = hex("e8edf5"), metalLo = hex("a9b4c7");
  write("i13", draw((x, y) => {
    const r = Math.hypot(x - 8, y - 8);
    if (r <= 4.9) return r < 3.2 ? center : rim;
    // fourchette : manche x 1.5, dents en haut
    if (x > 0.8 && x < 2.6 && y > 2 && y < 14.5) {
      if (y < 5.5) return Math.abs(x - 1.7) < 0.35 && y < 4.6 ? null : metal;
      return Math.abs(x - 1.7) < 0.55 ? metal : null;
    }
    // couteau : lame large en haut, manche fin
    if (x > 13.2 && x < 15.2 && y > 1.6 && y < 14.5) return y < 8.5 ? (x > 14.4 ? metalLo : metal) : Math.abs(x - 14.2) < 0.55 ? metalLo : null;
    return null;
  }, (c) => (c === rim || c === center ? hex("13575a") : hex("3a4466"))));
}

// Cacahuète : coque en huit, quadrillage de la coque marqué par des points.
{
  const shell = hex("e4a672"), hi = hex("ead4aa"), lo = hex("b86f50"), dot = hex("9a5a3c");
  write("i412", draw((x, y) => {
    const a = Math.hypot(x - 5.4, y - 10.4) <= 4.2, b = Math.hypot(x - 10.4, y - 5.4) <= 4.4;
    const waist = segDist(x, y, 5.4, 10.4, 10.4, 5.4) <= 2.9;
    if (!(a || b || waist)) return null;
    if ((Math.floor(x) * 2 + Math.floor(y)) % 4 === 0 && (Math.floor(x) + Math.floor(y)) % 2 === 0) return dot;
    if (x + y < 12.5 && (x - y) * (x - y) < 30) return hi;
    return x + y > 19 ? lo : shell;
  }, () => hex("743f39"), [dot]));
}

// Nuggets : trois morceaux dorés irréguliers qui se chevauchent, panure piquetée.
{
  const gold = hex("f0b541"), hi = hex("fee761"), lo = hex("cf752b"), speck = hex("a65a1e");
  const blobs = [[5.2, 6.0, 4.0, 3.2], [10.6, 7.6, 4.2, 3.4], [7.0, 11.6, 4.6, 3.2]];
  write("i923", draw((x, y) => {
    let hit = -1;
    blobs.forEach(([bx, by, rx, ry], i) => {
      const wob = 1 + 0.12 * Math.sin(Math.atan2(y - by, x - bx) * 3 + i * 2);
      if (((x - bx) / rx) ** 2 + ((y - by) / ry) ** 2 <= wob) hit = i;
    });
    if (hit < 0) return null;
    const [bx, by] = blobs[hit];
    if ((Math.floor(x) * 3 + Math.floor(y) * 5) % 7 === 0) return speck;
    if (x - bx + y - by < -2) return hi;
    return x - bx + y - by > 2 ? lo : gold;
  }, () => hex("743f39"), [speck]));
}

// --- versions pixel par pixel (remplacent les tracés ci-dessus pour ces icônes) ---
write("i216", ascii([
  "....kkkkkkkk....",
  "...kOOOOOOOOk...",
  "..kOooOOOOOOLk..",
  "..kOoOOOOOOOLk..",
  "..kOOOOOOOOOLk..",
  "..kOWEOOOWEOLk..",
  "..kOEEOOOEEOLk..",
  "..kOOOOOOOOOLk..",
  "...kOOOOOOOOk...",
  "..kOOkOOOOkOOk..",
  ".kOOk.kOOk.kOOk.",
  "kOOk.kOOOOk.kOOk",
  "kOk.kOk..kOk.kOk",
  "kOk.kOk..kOk.kOk",
  ".kOkkOk..kOkkOk.",
  "..kk.kk..kk.kk..",
], { k: "6e2727", O: "f77622", o: "feae34", L: "c64524", W: "ffffff", E: "181425" }));

write("i228", ascii([
  ".......kk.......",
  "......kPPk......",
  ".....kPPPPk.....",
  "...kkPpPPPLkk...",
  "..kPPPpPPPLLLk..",
  ".kPPPPpPPPLLLLk.",
  "..kkkPpPPPLkkk..",
  "....kPpPPPLk....",
  "....kPpPPPLk....",
  "....kPEPPELk....",
  "....kPPPPPLk....",
  "...kLkLkkLkLk...",
  "..kLk.kLLk.kLk..",
  "..kLk..kLk..kLk.",
  ".kLk...kLk...kLk",
  ".kk....kLk....kk",
], { k: "6b2438", P: "f6757a", p: "fbb8b8", L: "c84858", E: "181425" }));

write("i416", ascii([
  "......kkkk......",
  "..kk.kYYYYk.kk..",
  ".kYYkkyYYYkkYYk.",
  ".kYYkkkkkkkkYYk.",
  "kYYkDDSDDSDDkYYk",
  "kyYkDSDDSDDSkYYk",
  "kYYkDDSDDSDDkYYk",
  ".kYYkkkkkkkkYYk.",
  ".kYYkkYYYYkkYYk.",
  "..kk.kYYYYk.kk..",
  "......kkkk......",
  ".......kGk......",
  "..kkk..kGk.kkk..",
  ".kGGGkkkGkkGGGk.",
  "...kk..kGk..kk..",
  ".......kkk......",
], { k: "3f2832", Y: "feae34", y: "fee761", D: "a0603c", S: "5d2e22", G: "63c74d" }));

write("i923", ascii([
  "................",
  "..kkkk..........",
  ".kyYYYkk........",
  "kyYsYYYYk..kkk..",
  "kYYYYsYYk.kyYYk.",
  "kYsYYYOOkkyYYsYk",
  ".kOYYOOkkYYsYYYk",
  "..kkOOk.kYYYYOOk",
  "....kkkkkOYsOOk.",
  "...kyYYYYkkOOk..",
  "..kyYsYYYYOkk...",
  "..kYYYYsYYOOk...",
  "..kYsYYYYOOk....",
  "...kOOYYOOk.....",
  "....kkkkkk......",
  "................",
], { k: "743f39", Y: "f0b541", y: "fee761", O: "cf752b", s: "a65a1e" }));

// Assiette et couverts posés pixel par pixel, avec un vide entre chaque objet.
{
  const px = new Array(N * N).fill(null);
  const set = (x, y, c) => { if (x >= 0 && y >= 0 && x < N && y < N) px[y * N + x] = c; };
  const rim = hex("1f9ea3"), center = hex("5fd0cf"), plateInk = hex("13575a"), metal = hex("e8edf5"), metalLo = hex("a9b4c7"), metalInk = hex("3a4466");
  const plate = [];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) if (Math.hypot(x + 0.5 - 8, y + 0.5 - 8) <= 3.9) plate.push([x, y]);
  const inPlate = new Set(plate.map(([x, y]) => x + "," + y));
  plate.forEach(([x, y]) => {
    const edge = [[1, 0], [-1, 0], [0, 1], [0, -1]].some(([dx, dy]) => !inPlate.has(x + dx + "," + (y + dy)));
    set(x, y, edge ? plateInk : Math.hypot(x + 0.5 - 8, y + 0.5 - 8) < 2.4 ? center : rim);
  });
  const fork = [[0, 1], [0, 2], [0, 3], [2, 1], [2, 2], [2, 3], [0, 4], [1, 4], [2, 4], [1, 5], ...[6, 7, 8, 9, 10, 11, 12, 13].map((y) => [1, y])];
  const knife = [...[1, 2, 3, 4, 5, 6, 7].map((y) => [14, y]), ...[2, 3, 4, 5, 6, 7].map((y) => [15, y]), ...[8, 9, 10, 11, 12, 13].map((y) => [14, y])];
  const metalSet = new Set([...fork, ...knife].map(([x, y]) => x + "," + y));
  [...fork, ...knife].forEach(([x, y]) => set(x, y, y >= 8 ? metalLo : metal));
  // contour extérieur des couverts
  for (const [x, y] of [...fork, ...knife]) for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
    const k = x + dx + "," + (y + dy);
    if (!metalSet.has(k) && !inPlate.has(k) && x + dx >= 0 && x + dx < N) set(x + dx, y + dy, metalInk);
  }
  write("i13", px);
}
