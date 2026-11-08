// Construit data/foods.sqlite (base d'aliments servie par foods.mjs) à partir de la base complète foods.db.
//   node scripts/build-foods.mjs <foods.db> [sortie=data/foods.sqlite]
// Garde NCC (aliments génériques, très complets), MND (produits de marque) et l'USDA (base générique et produits de
// marque) ; Open Food Facts reste interrogé directement par l'app. Priorité d'affichage, NCC et MND d'abord puis par
// qualité mesurée (fiches incohérentes / sans calcium) : NCC (1,4 % / 2 %), MND (5,7 % / 62 %), USDA base (0,2 % / 4 %),
// USDA marques (3,3 % / 47 %). Nettoyage :
//  - fiches sans énergie, aux valeurs négatives, aux macros > 105 g pour 100 g ou dont l'énergie s'écarte de plus de
//    20 % (+ 20 kcal) de 4/4/9/7 kcal par gramme : écartées (≈ 6 % de MND, ≈ 1 % de NCC) ;
//  - un code-barres en double : la fiche de la source prioritaire, puis la plus récente ;
//  - micronutriments à 0 : la base source remplace les valeurs inconnues par 0, on les traite comme absents.
// Le fichier produit n'est jamais commité ni copié dans l'image : il se place dans le volume de données du serveur.
import { DatabaseSync } from 'node:sqlite';
import { existsSync, renameSync, rmSync, mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';

const [source, target = 'data/foods.sqlite'] = process.argv.slice(2);
if (!source || !existsSync(source)) { console.error('usage: node scripts/build-foods.mjs <foods.db> [data/foods.sqlite]'); process.exit(1); }
const out = resolve(target); const tmp = out + '.building';
mkdirSync(dirname(out), { recursive: true }); rmSync(tmp, { force: true });

// colonne de la base source -> code de micronutriment de l'app (Micros.kt), valeurs pour 100 g dans la même unité.
// Plusieurs colonnes de la base source sont mal nommées ; correspondances vérifiées sur des aliments de référence USDA
// (fève crue 16052, banane 09040, blanc de poulet, yaourt entier) : « manganese_mg » contient le cuivre, « iodine_ug » le
// manganèse, « chromium_ug » le sélénium, « vitamin_e_mg » le bêta-carotène, « gamma_tocopherol_mg » la vitamine E,
// « vitamin_d3_ug » la vitamine A (ER), « vitamin_d_iu » la vitamine D en µg, « fat_trans_mono/poly_g » les acides
// gras mono- et polyinsaturés, « omega6_g » l'ALA. Les colonnes au contenu incertain (« vitamin_d_ug », « fat_mono_g »,
// « fat_poly_g », « iodine »…) sont ignorées.
export const MICROS = {
  sugars_g: 'sugar', starch_g: 'starch', fiber_g: 'fiber', fat_saturated_g: 'sat', fat_trans_mono_g: 'mufa', fat_trans_poly_g: 'pufa',
  omega6_g: 'ala', cholesterol_mg: 'chol', alcohol_g: 'alcohol', water_g: 'water', thiamin_mg: 'b1', riboflavin_mg: 'b2',
  niacin_mg: 'b3', pantothenic_acid_mg: 'b5', vitamin_b6_mg: 'b6', folate_ug: 'folate', vitamin_b12_ug: 'b12', vitamin_c_mg: 'vitC',
  vitamin_d_iu: 'vitD', gamma_tocopherol_mg: 'vitE', vitamin_d3_ug: 'vitA', vitamin_e_mg: 'betaCarotene',
  calcium_mg: 'calcium', iron_mg: 'iron', magnesium_mg: 'magnesium', phosphorus_mg: 'phosphorus', potassium_mg: 'potassium',
  sodium_mg: 'sodium', zinc_mg: 'zinc', chromium_ug: 'selenium', sugars_added_g: 'addedSugar', fat_trans_g: 'trans',
  vitamin_k_ug: 'vitK', manganese_mg: 'copper', iodine_ug: 'manganese', caffeine_mg: 'caffeine',
};
// Produits de marque USDA : la vitamine D de l'étiquette n'a pas d'unité sûre dans la source, on l'écarte.
const SKIP = { USDA: new Set(['vitD']) };

const src = new DatabaseSync(source, { readOnly: true });
const db = new DatabaseSync(tmp);
db.exec(`PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA page_size=4096;
  CREATE TABLE food (id INTEGER PRIMARY KEY, src TEXT NOT NULL, name TEXT NOT NULL, brand TEXT, upc TEXT,
    kcal REAL NOT NULL, protein REAL NOT NULL, fat REAL NOT NULL, carbs REAL NOT NULL, micros TEXT, servings TEXT, rank REAL NOT NULL);
  CREATE VIRTUAL TABLE food_fts USING fts5(name, brand, content='food', content_rowid='id', tokenize='unicode61 remove_diacritics 2');`);

const cols = Object.keys(MICROS).join(', ');
const rows = src.prepare(`SELECT id, food_id, collection, source, common, food_name, brand_name, main_upc, last_updated,
  energy_kcal, protein_g, fat_g, carbs_g, alcohol_g, default_serving_desc, default_serving_g, ${cols}
  FROM foods WHERE source IN ('NCC', 'MND', 'USDAC', 'USDA')
  ORDER BY CASE source WHEN 'NCC' THEN 0 WHEN 'MND' THEN 1 WHEN 'USDAC' THEN 2 ELSE 3 END, last_updated DESC`);
const PRIORITY = { NCC: 0, MND: 1, USDAC: 2, USDA: 3 };
const CODE = { NCC: 'ncc', MND: 'mnd', USDAC: 'usda', USDA: 'usdab' };
const servingsOf = src.prepare('SELECT description, amount, grams FROM servings WHERE food_id = ? AND collection = ? ORDER BY sort_order, idx LIMIT 8');
const insert = db.prepare('INSERT INTO food (src, name, brand, upc, kcal, protein, fat, carbs, micros, servings, rank) VALUES (?,?,?,?,?,?,?,?,?,?,?)');

const r1 = v => Math.round(v * 10) / 10;
const r3 = v => Math.round(v * 1000) / 1000;
const clean = s => (s ?? '').replace(/\s+/g, ' ').trim();
const title = s => s && s === s.toLowerCase() ? s.replace(/(^|[\s(/-])(\p{L})/gu, (m, a, b) => a + b.toUpperCase()) : s;
const seen = new Set();
let kept = 0, dropped = 0, dupes = 0;
db.exec('BEGIN');
for (const f of rows.iterate()) {
  const { energy_kcal: k, protein_g: p, fat_g: l, carbs_g: c } = f;
  const name = title(clean(f.food_name));
  if (!name || k == null || p == null || l == null || c == null || k < 0 || p < 0 || l < 0 || c < 0 || k > 950 || p + l + c > 105 ||
      Math.abs(k - (4 * p + 4 * c + 9 * l + 7 * (f.alcohol_g || 0))) > 0.2 * k + 20) { dropped++; continue; }
  const upc = /^\d{8,14}$/.test(f.main_upc || '') ? f.main_upc.replace(/^0+(?=\d{12,})/, '') : null;
  if (upc) { if (seen.has(upc)) { dupes++; continue; } seen.add(upc); }
  const micros = {};
  for (const [col, code] of Object.entries(MICROS)) if (f[col] > 0 && !SKIP[f.source]?.has(code)) micros[code] = r3(f[col]);
  const servings = servingsOf.all(f.food_id, f.collection)
    .filter(s => s.grams > 0 && s.description)
    .map(s => [clean(`${s.amount && s.amount !== 1 ? r3(s.amount) + ' ' : ''}${s.description}`), r1(s.grams)]);
  if (!servings.length && f.default_serving_g > 0 && f.default_serving_desc) servings.push([clean(f.default_serving_desc), r1(f.default_serving_g)]);
  // classement : source prioritaire d'abord, puis noms courts (« Banana » avant « Banana bread with walnuts »)
  const rank = PRIORITY[f.source] + Math.min(name.length, 80) / 200;
  insert.run(CODE[f.source], name, title(clean(f.brand_name)) || null, upc, r1(k), r1(p), r1(l), r1(c),
    Object.keys(micros).length ? JSON.stringify(micros) : null, servings.length ? JSON.stringify(servings) : null, rank);
  if (++kept % 100000 === 0) console.log(`${kept} aliments…`);
}
db.exec('COMMIT');
console.log('index…');
db.exec(`INSERT INTO food_fts(rowid, name, brand) SELECT id, name, coalesce(brand, '') FROM food;
  INSERT INTO food_fts(food_fts) VALUES('optimize');
  CREATE INDEX food_upc ON food(upc) WHERE upc IS NOT NULL;
  CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);`);
db.prepare('INSERT INTO meta VALUES (?, ?)').run('built', new Date().toISOString());
db.prepare('INSERT INTO meta VALUES (?, ?)').run('count', String(kept));
db.exec('ANALYZE; VACUUM;');
db.close(); src.close();
renameSync(tmp, out);
console.log(`${out} : ${kept} aliments gardés, ${dropped} écartés (incohérents), ${dupes} doublons de code-barres.`);
