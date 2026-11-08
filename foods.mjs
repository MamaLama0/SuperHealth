// Recherche d'aliments (NCC, MND, USDA) servie depuis data/foods.sqlite, construit par scripts/build-foods.mjs.
// Lecture seule, sans compte ni journal : la requête et le code-barres ne sont pas conservés.
//   GET /v2/foods/search?q=banane&limit=30
//   GET /v2/foods/barcode/3017620422003
// Sans fichier foods.sqlite, la fonction est désactivée (404) et /health annonce foods: 0.
import { DatabaseSync } from 'node:sqlite';
import { existsSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

export const MAX_QUERY = 80;
export const MAX_LIMIT = 50;
const CANDIDATES = 250;

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

/** Minuscules sans accents (« Pêche » -> « peche »). */
export const plain = value => String(value).normalize('NFKD').replace(/\p{M}/gu, '').toLowerCase();
/** Singulier simple d'un mot français ou anglais (« pommes » -> « pomme », « noix » reste « noix »). */
export const singular = word => word.length > 3 && /[^s]s$/.test(word) ? word.slice(0, -1) : word;
const BASIC = /^\s*(fresh|raw|whole|plain|white|cooked|boiled|cru|crue|nature|regular|unsweetened)\b/;
const forms = term => [term, term + 's', term + 'x', term + 'es'];

/** Précision du nom [name] pour [term] (tous deux passés par plain) : 0 exact, 1 mot + autres mots, 2 début de mot, 3 ailleurs. */
export function matchClass(name, term) {
  const t = singular(term);
  if (forms(t).some(f => name === f || name.startsWith(f + ',') || name.startsWith(f + ' ('))) return 0;
  if (forms(t).some(f => name.startsWith(f + ' '))) return 1;
  if (name.startsWith(t)) return 2;
  return 3;
}

/**
 * Aliments courants en français -> noms anglais des bases NCC et USDA, pour que « pomme » trouve « Apple, Fresh ».
 * Clés sans accents ni pluriel.
 */
export const SYNONYMS = {
  pomme: ['apple'], poire: ['pear'], banane: ['banana'], orange: ['orange'], clementine: ['clementine'], mandarine: ['mandarin'],
  fraise: ['strawberry'], framboise: ['raspberry'], myrtille: ['blueberry'], mure: ['blackberry'], raisin: ['grape'], cerise: ['cherry'],
  peche: ['peach'], nectarine: ['nectarine'], abricot: ['apricot'], prune: ['plum'], pruneau: ['prune'], ananas: ['pineapple'], mangue: ['mango'],
  kiwi: ['kiwi'], citron: ['lemon'], 'citron vert': ['lime'], pasteque: ['watermelon'], melon: ['cantaloupe', 'melon'], figue: ['fig'],
  datte: ['date'], grenade: ['pomegranate'], pamplemousse: ['grapefruit'], avocat: ['avocado'], 'noix de coco': ['coconut'],
  tomate: ['tomato'], carotte: ['carrot'], 'pomme de terre': ['potato'], patate: ['potato'], 'patate douce': ['sweet potato'],
  courgette: ['zucchini'], aubergine: ['eggplant'], poivron: ['bell pepper'], oignon: ['onion'], ail: ['garlic'], echalote: ['shallot'],
  salade: ['lettuce'], laitue: ['lettuce'], epinard: ['spinach'], brocoli: ['broccoli'], 'chou fleur': ['cauliflower'], chou: ['cabbage'],
  'chou de bruxelle': ['brussels sprouts'], haricot: ['beans'], 'haricot vert': ['green beans'], 'petit poi': ['peas'], poi: ['peas'],
  lentille: ['lentils'], 'pois chiche': ['chickpeas'], mais: ['corn'], champignon: ['mushroom'], concombre: ['cucumber'], radis: ['radish'],
  betterave: ['beet'], celeri: ['celery'], poireau: ['leek'], asperge: ['asparagus'], artichaut: ['artichoke'], fenouil: ['fennel'],
  courge: ['squash'], potiron: ['pumpkin'], citrouille: ['pumpkin'], navet: ['turnip'], 'soja': ['soybeans'], tofu: ['tofu'],
  riz: ['rice'], 'riz complet': ['brown rice'], pate: ['pasta'], spaghetti: ['spaghetti'], pain: ['bread'], 'pain complet': ['whole wheat bread'],
  baguette: ['french bread'], farine: ['flour'], avoine: ['oats', 'oatmeal'], 'flocon d avoine': ['oatmeal', 'oats'], semoule: ['couscous'],
  quinoa: ['quinoa'], boulgour: ['bulgur'], cereale: ['cereal'], biscotte: ['toast'], croissant: ['croissant'],
  poulet: ['chicken'], 'blanc de poulet': ['chicken breast'], dinde: ['turkey'], boeuf: ['beef'], 'steak hache': ['ground beef'], porc: ['pork'],
  jambon: ['ham'], veau: ['veal'], agneau: ['lamb'], canard: ['duck'], lapin: ['rabbit'], saucisse: ['sausage'], lardon: ['bacon'], bacon: ['bacon'],
  saumon: ['salmon'], thon: ['tuna'], cabillaud: ['cod'], colin: ['pollock'], sardine: ['sardines'], maquereau: ['mackerel'], truite: ['trout'],
  crevette: ['shrimp'], moule: ['mussels'], oeuf: ['egg'], 'blanc d oeuf': ['egg white'], lait: ['milk'], 'lait d amande': ['almond milk'],
  yaourt: ['yogurt'], 'yaourt grec': ['greek yogurt'], 'fromage blanc': ['cottage cheese', 'quark'], fromage: ['cheese'], beurre: ['butter'],
  creme: ['cream'], 'creme fraiche': ['sour cream'], mozzarella: ['mozzarella'], emmental: ['swiss cheese'], huile: ['oil'],
  "huile d olive": ['olive oil'], sucre: ['sugar'], miel: ['honey'], confiture: ['jam'], chocolat: ['chocolate'], 'chocolat noir': ['dark chocolate'],
  amande: ['almonds'], noix: ['walnuts'], noisette: ['hazelnuts'], cacahuete: ['peanuts'], 'beurre de cacahuete': ['peanut butter'],
  'noix de cajou': ['cashews'], pistache: ['pistachios'], graine: ['seeds'], cafe: ['coffee'], the: ['tea'], 'jus d orange': ['orange juice'],
  eau: ['water'], biere: ['beer'], vin: ['wine'], frite: ['french fries'], pizza: ['pizza'], hamburger: ['hamburger'], soupe: ['soup'],
  glace: ['ice cream'], gateau: ['cake'], biscuit: ['cookie'], compote: ['applesauce'], 'proteine whey': ['whey protein'], whey: ['whey protein'],
};

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
  // Noms qui commencent par le terme (premier mot du nom), meilleures sources d'abord : un mot courant (« apple »)
  // a trop de fiches pour que les génériques sortent des seuls candidats plein texte.
  const leading = db.prepare(`SELECT f.*, 0 AS score FROM food_fts JOIN food f ON f.id = food_fts.rowid
    WHERE food_fts MATCH ? ORDER BY f.rank LIMIT ${CANDIDATES}`);
  const leadingQuery = term => {
    const one = form => { const w = ftsQuery(form).split(' ').filter(Boolean).map(x => x.replace(/\*$/, '')); return w.length ? `(name : ^ ${w.join(' + ')})` : ''; };
    const t = singular(term);
    return [...new Set([one(t), one(t + 's')])].filter(Boolean).join(' OR ');
  };
  const cache = new Map();

  /**
   * Classement par précision du nom, pour chaque terme cherché (la requête et sa traduction anglaise éventuelle) :
   * 0 nom exact (« Pomme », « Apple, Fresh », « Banana (raw) »), 1 le mot suivi d'autres mots (« Pomme Mure »),
   * 2 le mot en début d'un mot plus long (« Applesauce »), 3 ailleurs. Le pluriel compte comme le mot. À précision égale :
   * la source prioritaire (NCC, MND, USDA base, USDA marques), les noms de moins de mots, la pertinence plein texte.
   * Les fiches identiques (même nom, marque et énergie) ne sont montrées qu'une fois.
   */
  function find(text, limit) {
    const key = plain(String(text)).replace(/\s+/g, ' ').trim() + '|' + limit;
    if (cache.has(key)) return cache.get(key);
    const result = rank(text, limit);
    if (cache.size >= 500) cache.delete(cache.keys().next().value);
    cache.set(key, result);
    return result;
  }

  function rank(text, limit) {
    const head = plain(String(text)).replace(/\s+/g, ' ').trim();
    const terms = [...new Set([head, ...(SYNONYMS[head] || SYNONYMS[head.split(' ').map(singular).join(' ')] || [])])];
    const rows = new Map();
    for (const term of terms) {
      const match = ftsQuery(term);
      if (!match) continue;
      for (const row of search.all(match)) if (!rows.has(row.id)) rows.set(row.id, row);
      const lead = leadingQuery(term);
      if (lead) for (const row of leading.all(lead)) if (!rows.has(row.id)) rows.set(row.id, row);
    }
    const seen = new Set();
    return [...rows.values()]
      .map(row => {
        const name = plain(row.name);
        const cls = Math.min(...terms.map(t => matchClass(name, t)));
        // forme de base (« Apple, Fresh », « Milk, Whole », « Rice, White, Cooked ») avant les préparations
        const basic = BASIC.test(name.split(',').slice(1).join(',')) ? 0 : 1;
        const key = `${name}|${row.brand ? plain(row.brand) : ''}|${Math.round(row.kcal)}`;
        return { row, cls, basic, key, tier: Math.floor(row.rank), words: name.split(/[^\p{L}\p{N}]+/u).filter(Boolean).length };
      })
      .sort((a, b) => a.cls - b.cls || a.tier - b.tier || a.basic - b.basic || a.words - b.words || a.row.score - b.row.score || a.row.rank - b.row.rank)
      .filter(x => !seen.has(x.key) && seen.add(x.key))
      .slice(0, limit).map(x => toJson(x.row));
  }

  function barcode(code) {
    for (const variant of barcodeVariants(code)) { const row = byUpc.get(variant); if (row) return toJson(row); }
    return null;
  }

  /** Réponse JSON, compressée quand le client l'accepte (≈ 5 fois plus petite pour 20 aliments). */
  function json(req, res, send, value, maxAge) {
    const body = JSON.stringify(value);
    const headers = { 'Cache-Control': `public, max-age=${maxAge}`, Vary: 'Accept-Encoding' };
    if (body.length > 1024 && /\bgzip\b/.test(req.headers['accept-encoding'] || ''))
      return send(res, 200, gzipSync(body, { level: 6 }), { ...headers, 'Content-Encoding': 'gzip' });
    return send(res, 200, body, headers);
  }

  function handle(req, res, send, url) {
    req.resume();
    if (req.method !== 'GET') return send(res, 405, '', { Allow: 'GET' });
    const path = url.pathname;
    if (path === '/v2/foods/search') {
      const q = url.searchParams.get('q') || '';
      if (q.length < 2 || q.length > MAX_QUERY) return send(res, 400);
      const limit = Math.min(MAX_LIMIT, Math.max(1, Number.parseInt(url.searchParams.get('limit') || '30', 10) || 30));
      return json(req, res, send, { foods: find(q, limit) }, 3600);
    }
    const code = /^\/v2\/foods\/barcode\/(\d{8,14})$/.exec(path);
    if (code) {
      const food = barcode(code[1]);
      return food ? json(req, res, send, { food }, 86400) : send(res, 404, '', { 'Cache-Control': 'public, max-age=3600' });
    }
    return send(res, 404);
  }

  return { enabled: true, count, find, barcode, handle, close: () => db.close() };
}
