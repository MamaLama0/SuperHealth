// Icônes dessinées à la main (formes simples), dans le style du pack : contour sombre, ombre en bas à droite.
// usage: node hand.js <outdir> [<pickles.png> pour ajouter les points des cornichons]
const { execFileSync } = require("child_process");
const [outDir, pickles] = process.argv.slice(2);
const N = 16;
const hex = (h) => [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];

function write(name, px) {
  const buf = Buffer.alloc(N * N * 4);
  px.forEach((c, i) => { if (c) { buf[i * 4] = c[0]; buf[i * 4 + 1] = c[1]; buf[i * 4 + 2] = c[2]; buf[i * 4 + 3] = 255; } });
  execFileSync("magick", ["-size", `${N}x${N}`, "-depth", "8", "rgba:-", `${outDir}/${name}.png`], { input: buf });
}

/** Remplit d'après une fonction (x, y) -> couleur|null, puis contour 1 px à l'intérieur de la forme. */
function draw(fill, outline) {
  const px = [];
  for (let y = 0; y < N; y++) for (let x = 0; x < N; x++) px.push(fill(x + 0.5, y + 0.5));
  const empty = (x, y) => x < 0 || y < 0 || x >= N || y >= N || !px[y * N + x];
  return px.map((c, i) => {
    if (!c) return null;
    const x = i % N, y = (i / N) | 0;
    return empty(x + 1, y) || empty(x - 1, y) || empty(x, y + 1) || empty(x, y - 1) ? outline(c) : c;
  });
}
const ink = (c) => c.map((v, j) => Math.round(v * 0.3 * 0.65 + [24, 20, 37][j] * 0.35));

// Oreo vu de dessus : biscuit rond très sombre, anneau en relief et motif central.
{
  const base = hex("3b2a33"), relief = hex("5e4654"), hi = hex("7d6372"), lo = hex("2a1e26");
  write("i571", draw((x, y) => {
    const dx = x - 8, dy = y - 8, r = Math.hypot(dx, dy);
    if (r > 7.6) return null;
    if (r > 4.8 && r < 5.7) return dx + dy < 0 ? hi : relief;
    if (r < 2.4) return (Math.floor(x) + Math.floor(y)) % 2 ? relief : base;
    if (r > 5.7 && dx + dy > 3) return lo;
    return base;
  }, () => hex("181425")));
}

// Tournesol : huit pétales jaunes arrondis, cœur brun piqué de graines.
{
  const pet = hex("feae34"), petLo = hex("f77622"), petHi = hex("fee761"), core = hex("743f39"), seed = hex("3f2832");
  write("i416", draw((x, y) => {
    const dx = x - 8, dy = y - 8, r = Math.hypot(dx, dy), a = Math.atan2(dy, dx);
    if (r < 3.3) return (Math.floor(x) + 2 * Math.floor(y)) % 3 === 0 ? seed : core;
    const petal = 5.5 + 2.3 * Math.pow(Math.abs(Math.cos(a * 4)), 0.6);
    if (r > petal) return null;
    if (dx + dy > 4) return petLo;
    if (dx + dy < -5) return petHi;
    return pet;
  }, (c) => (c === pet || c === petLo || c === petHi ? hex("743f39") : hex("3f2832"))));
}

// Éclair en diagonale : pâte à choux dorée, glaçage chocolat sur le dessus avec un reflet.
{
  const pastry = hex("f0b541"), pastryLo = hex("cf752b"), choc = hex("4e2c28"), chocHi = hex("7d4a3a"), chocLo = hex("2e1c22");
  const ax = 3, ay = 12.5, bx = 13, by = 3.5;
  write("i1025", draw((x, y) => {
    const vx = bx - ax, vy = by - ay, t = Math.max(0, Math.min(1, ((x - ax) * vx + (y - ay) * vy) / (vx * vx + vy * vy)));
    const px = ax + t * vx, py = ay + t * vy, d = Math.hypot(x - px, y - py);
    if (d > 3.2) return null;
    // côté du glaçage : au-dessus de l'axe (vers le haut à gauche)
    const side = (x - px) * -vy + (y - py) * vx; // < 0 : dessus
    if (side < 0.3 * Math.hypot(vx, vy)) {
      if (side < -1.6 * Math.hypot(vx, vy)) return chocHi;
      return d > 2.3 && side < 0 ? chocLo : choc;
    }
    return d > 2.2 ? pastryLo : pastry;
  }, ink));
}

// Bocal de cornichons : points clairs sur les cornichons (version générée passée en argument).
if (pickles) {
  const raw = execFileSync("magick", [pickles, "-depth", "8", "rgba:-"]);
  const px = [];
  for (let i = 0; i < N * N; i++) px.push(raw[i * 4 + 3] ? [raw[i * 4], raw[i * 4 + 1], raw[i * 4 + 2]] : null);
  const green = (c) => c && c[1] > c[0] + 25 && c[1] > c[2] + 25 && c[1] > 80;
  for (let y = 1; y < N - 1; y++) for (let x = 1; x < N - 1; x++) {
    const i = y * N + x;
    if (green(px[i]) && green(px[i - 1]) && green(px[i + 1]) && (x * 3 + y * 2) % 7 === 0) px[i] = hex("dff6a0");
  }
  write("i1038", px);
}
