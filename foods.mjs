// Recherche d'aliments (NCC, MND, USDA) servie depuis data/foods.sqlite, construit par scripts/build-foods.mjs.
// Lecture seule, sans compte ni journal : la requête et le code-barres ne sont pas conservés.
//   GET /v2/foods/search?q=banane&limit=30
//   GET /v2/foods/barcode/3017620422003
// Sans fichier foods.sqlite, la fonction est désactivée (404) et /health annonce foods: 0.
import { DatabaseSync } from 'node:sqlite';
import { existsSync } from 'node:fs';
import { join } from 'node:path';

export const MAX_QUERY = 80;
export const MAX_LIMIT = 50;
const CANDIDATES = 400;

/** Mots de la requête -> expression FTS5 sûre : chaque mot en préfixe, guillemets neutralisés. */
export function ftsQuery(text) {
  const words = String(text).normalize('NFKC').toLowerCase().match(/[\p{L}\p{N}]+/gu) || [];
  return words.slice(0, 8).map(w => `"${w}"*`).join(' ');
}

/** Codes-barres équivalents : EAN-13 et UPC-A (zéro de tête), avec ou sans zéros devant. */
export function barcodeVariants(code) {
  const bare = code.replace(/^0+/, '');
  return [...new Set([code, bare, bare.padStart(12, '0'), bare.padStart(13, '0'), bare.padStart(14, '0')])];
}

const toJson = row => ({
  id: row.id, src: row.src, name: row.name, brand: row.brand || undefined, upc: row.upc || undefined,
  kcal: row.kcal, protein: row.protein, fat: row.fat, carbs: row.carbs,
  micros: row.micros ? JSON.parse(row.micros) : undefined,
  servings: row.servings ? JSON.parse(row.servings) : undefined,
});

export function createFoods(dataDir, { file = process.env.FOODS_DB || join(dataDir, 'foods.sqlite') } = {}) {
  if (!existsSync(file)) return { enabled: false, handle: (req, res, send) => { req.resume(); send(res, 404); } };
  const db = new DatabaseSync(file, { readOnly: true });
  db.exec('PRAGMA cache_size=-16000; PRAGMA mmap_size=0; PRAGMA query_only=1;');
  const count = Number(db.prepare("SELECT value FROM meta WHERE key = 'count'").get()?.value || 0);
  const search = db.prepare(`SELECT f.*, bm25(food_fts, 1.0, 0.4) AS score FROM food_fts
    JOIN food f ON f.id = food_fts.rowid WHERE food_fts MATCH ? ORDER BY score LIMIT ${CANDIDATES}`);
  const byUpc = db.prepare('SELECT * FROM food WHERE upc = ? ORDER BY rank LIMIT 1');

  /**
   * Classement : les noms qui commencent par la requête d'abord ; dans chaque groupe, la source prioritaire (NCC, MND,
   * USDA base, USDA marques : partie entière de rank), puis le nom exact, la pertinence plein texte et les noms courts.
   * Les fiches identiques (même nom, marque et énergie) ne sont montrées qu'une fois.
   */
  function find(text, limit) {
    const match = ftsQuery(text);
    if (!match) return [];
    const head = String(text).normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase().trim();
    const plain = s => s.normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase();
    return search.all(match)
      .map(row => {
        const name = plain(row.name);
        // 0 : le nom est la requête (« Banana, Fresh » pour banana), 1 : il commence par elle, 2 : ailleurs
        const starts = name === head || name.startsWith(head + ',') || name.startsWith(head + ' (') ? 0 : name.startsWith(head) ? 1 : 2;
        return { row, tier: Math.floor(row.rank), starts };
      })
      .sort((a, b) => (a.starts > 1) - (b.starts > 1) || a.tier - b.tier || a.starts - b.starts || a.row.score - b.row.score || a.row.rank - b.row.rank)
      .filter((x, i, all) => {
        const key = `${plain(x.row.name)}|${plain(x.row.brand || '')}|${Math.round(x.row.kcal)}`;
        return all.findIndex(y => `${plain(y.row.name)}|${plain(y.row.brand || '')}|${Math.round(y.row.kcal)}` === key) === i;
      })
      .slice(0, limit).map(x => toJson(x.row));
  }

  function barcode(code) {
    for (const variant of barcodeVariants(code)) { const row = byUpc.get(variant); if (row) return toJson(row); }
    return null;
  }

  function handle(req, res, send, url) {
    req.resume();
    if (req.method !== 'GET') return send(res, 405, '', { Allow: 'GET' });
    const path = url.pathname;
    if (path === '/v2/foods/search') {
      const q = url.searchParams.get('q') || '';
      if (q.length < 2 || q.length > MAX_QUERY) return send(res, 400);
      const limit = Math.min(MAX_LIMIT, Math.max(1, Number.parseInt(url.searchParams.get('limit') || '30', 10) || 30));
      return send(res, 200, JSON.stringify({ foods: find(q, limit) }), { 'Cache-Control': 'public, max-age=3600' });
    }
    const code = /^\/v2\/foods\/barcode\/(\d{8,14})$/.exec(path);
    if (code) {
      const food = barcode(code[1]);
      return food ? send(res, 200, JSON.stringify({ food }), { 'Cache-Control': 'public, max-age=86400' }) : send(res, 404, '', { 'Cache-Control': 'public, max-age=3600' });
    }
    return send(res, 404);
  }

  return { enabled: true, count, find, barcode, handle, close: () => db.close() };
}
