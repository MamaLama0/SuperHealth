import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { request } from 'node:http';
import { createRelay } from './server.mjs';
import { ftsQuery, barcodeVariants } from './foods.mjs';

/** Petite base au format de scripts/build-foods.mjs. */
function makeDb(file) {
  const db = new DatabaseSync(file);
  db.exec(`CREATE TABLE food (id INTEGER PRIMARY KEY, src TEXT NOT NULL, name TEXT NOT NULL, brand TEXT, upc TEXT,
      kcal REAL NOT NULL, protein REAL NOT NULL, fat REAL NOT NULL, carbs REAL NOT NULL, micros TEXT, servings TEXT, rank REAL NOT NULL);
    CREATE VIRTUAL TABLE food_fts USING fts5(name, brand, content='food', content_rowid='id', tokenize='unicode61 remove_diacritics 2');
    CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT);`);
  const add = db.prepare('INSERT INTO food (src, name, brand, upc, kcal, protein, fat, carbs, micros, servings, rank) VALUES (?,?,?,?,?,?,?,?,?,?,?)');
  add.run('usdab', 'Banana Chips', 'Acme', '012345678905', 520, 2, 34, 58, null, null, 3.06);
  add.run('mnd', 'Banana Smoothie', 'Fruity', null, 60, 1, 0.3, 14, null, null, 1.08);
  add.run('ncc', 'Banana, Fresh', null, null, 88, 0.7, 0.3, 23, '{"potassium":326}', '[["medium",118]]', 0.065);
  add.run('ncc', 'Crème brûlée', null, null, 280, 4, 18, 26, null, null, 0.06);
  add.run('mnd', 'Pommes Frites', 'Coop', null, 121, 2, 4, 19, null, null, 1.07);
  add.run('ncc', 'Apple, Chips', null, null, 383, 1, 20, 50, null, null, 0.06);
  add.run('ncc', 'Apple, Fresh, With Skin', null, null, 52, 0.3, 0.2, 14, null, null, 0.12);
  db.exec("INSERT INTO food_fts(rowid, name, brand) SELECT id, name, coalesce(brand, '') FROM food; INSERT INTO meta VALUES ('count', '7');");
  db.close();
}

async function start(withFoods = true) {
  const dataDir = await mkdtemp(join(tmpdir(), 'sh-foods-'));
  if (withFoods) makeDb(join(dataDir, 'foods.sqlite'));
  const server = await createRelay({ dataDir, rewardsUrl: null, rateLimitPerMinute: 8 });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  return { base, close: async () => { await new Promise(r => server.close(r)); await rm(dataDir, { recursive: true, force: true }); } };
}

test('precision classes and French synonyms', async () => {
  const { matchClass, SYNONYMS, singular } = await import('./foods.mjs');
  assert.equal(matchClass('pomme', 'pomme'), 0);
  assert.equal(matchClass('apple, fresh, with skin', 'apple'), 0);
  assert.equal(matchClass('pommes frites', 'pomme'), 1);
  assert.equal(matchClass('applesauce', 'apple'), 2);
  assert.equal(matchClass('compote de pomme', 'pomme'), 3);
  assert.deepEqual(SYNONYMS['pommes de terre'.split(' ').map(singular).join(' ')], ['potato']);
});

test('query and barcode helpers stay safe', () => {
  assert.equal(ftsQuery('Crème "brûlée" OR *'), '"crème"* "brûlée"* "or"*');
  assert.equal(ftsQuery('  ;; '), '');
  assert.deepEqual(barcodeVariants('0012345678905').sort(), ['00012345678905', '0012345678905', '012345678905', '12345678905'].sort());
});

test('search ranks NCC first, generic name first, then other sources; barcode finds UPC-A from EAN-13', async () => {
  const { base, close } = await start();
  try {
    assert.equal((await (await fetch(base + '/health')).json()).foods, 1);
    const { foods } = await (await fetch(base + '/v2/foods/search?q=banana')).json();
    assert.deepEqual(foods.map(f => f.name), ['Banana, Fresh', 'Banana Smoothie', 'Banana Chips']);
    assert.deepEqual(foods[0].micros, { potassium: 326 });
    assert.deepEqual(foods[0].servings, [['medium', 118]]);
    const zipped = await new Promise((resolve, reject) => request(base + '/v2/foods/search?q=banana', { headers: { 'Accept-Encoding': 'gzip' } }, r => { r.resume(); resolve(r.headers['content-encoding']); }).on('error', reject).end());
    assert.equal(zipped, undefined, 'petites réponses non compressées');
    const pomme = await (await fetch(base + '/v2/foods/search?q=pomme')).json();
    assert.deepEqual(pomme.foods.map(f => f.name), ['Apple, Fresh, With Skin', 'Apple, Chips', 'Pommes Frites']);
    const accent = await (await fetch(base + '/v2/foods/search?q=creme%20brul')).json();
    assert.equal(accent.foods[0].name, 'Crème brûlée');
    const hit = await fetch(base + '/v2/foods/barcode/0012345678905');
    assert.equal(hit.status, 200);
    assert.equal((await hit.json()).food.name, 'Banana Chips');
    assert.equal((await fetch(base + '/v2/foods/barcode/3017620422003')).status, 404);
    assert.equal((await fetch(base + '/v2/foods/search?q=a')).status, 400);
    assert.equal((await fetch(base + '/v2/foods/search?q=banana', { method: 'POST' })).status, 405);
    // limite par minute partagée par la recherche
    assert.equal((await fetch(base + '/v2/foods/search?q=banana')).status, 429);
  } finally { await close(); }
});

test('without foods.sqlite the feature is off', async () => {
  const { base, close } = await start(false);
  try {
    assert.equal((await (await fetch(base + '/health')).json()).foods, 0);
    assert.equal((await fetch(base + '/v2/foods/search?q=banana')).status, 404);
  } finally { await close(); }
});
