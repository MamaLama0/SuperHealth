import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { CATALOG, draw, applyEvent, openPack, RATES, REWARDS, LAUNCH_GIFT } from './economy.mjs';
import { createRelay } from './server.mjs';

const DAY = 86_400_000;
const fresh = () => ({ eclats: 0, bonusPacks: 0, sinceRare: 0, sinceEpic: 0, opened: 0, owned: {}, claimed: {}, gifts: [] });

test('catalogue aligné sur l’app : 52 fonds, 12 épiques à deux biomes', () => {
  assert.equal(CATALOG.length, 52);
  assert.equal(CATALOG[0].id, 'N001'); assert.equal(CATALOG.at(-1).id, 'N052');
  assert.equal(CATALOG.filter(c => c.rarity === 'EPIQUE' && c.elements.length === 2).length, 12);
});

test('taux bas et garanties tenues', () => {
  let rare = 0, epic = 0, sr = 0, se = 0, gapR = 0, worstR = 0;
  const n = 200000;
  for (let i = 0; i < n; i++) {
    const r = draw(i % 4, sr, se);
    assert.ok(r.pulls.length >= 2 && r.pulls.length <= 3);
    const ranks = r.pulls.map(k => CATALOG.find(c => c.id === k.split(':')[0]).rarity);
    if (ranks.some(x => x === 'RARE' || x === 'EPIQUE')) { rare++; worstR = Math.max(worstR, gapR + 1); gapR = 0; } else gapR++;
    if (ranks.includes('EPIQUE')) epic++;
    sr = r.sinceRare; se = r.sinceEpic;
  }
  assert.ok(rare / n > 0.03 && rare / n < 0.045, 'rare ' + rare / n);
  assert.ok(epic / n > 0.001 && epic / n < 0.01, 'epic ' + epic / n);
  assert.ok(worstR <= RATES.rarePity);
});

test('une semaine impliquée rapporte 2 à 3 packs, une semaine creuse bien moins, et rien ne se rejoue', () => {
  const now = Date.UTC(2026, 10, 6, 12); // jeudi
  const a = fresh();
  const day = Math.floor(now / DAY); const monday = day - ((day + 3) % 7);
  for (let s = 0; s < 3; s++) applyEvent(a, { type: 'session', id: 'w' + s, start: now - s * DAY, target: 3 }, now);
  for (const d of [day, day - 1]) for (const k of ["session", "rir", "sets"]) applyEvent(a, { type: "quest", id: `d:${k}:${d}` }, now);
  ['target', 'legs', 'pr'].forEach(k => applyEvent(a, { type: 'quest', id: `w:${k}:${monday}` }, now));
  for (let l = 0; l < 3; l++) applyEvent(a, { type: 'league', id: 'lq' + l }, now);
  assert.ok(a.eclats >= 200 && a.eclats <= 300, 'impliqué ' + a.eclats);
  assert.throws(() => applyEvent(a, { type: 'session', id: 'w0', start: now, target: 3 }, now), e => e.status === 409);
  assert.throws(() => applyEvent(a, { type: 'session', id: 'vieux', start: now - 10 * DAY }, now), e => e.status === 400);
  const lazy = fresh(); applyEvent(lazy, { type: 'session', id: 'x', start: now, target: 3 }, now); applyEvent(lazy, { type: 'quest', id: `d:session:${day}` }, now);
  assert.ok(lazy.eclats < 60, 'creux ' + lazy.eclats);
  const grind = fresh();
  for (let s = 0; s < 30; s++) applyEvent(grind, { type: 'session', id: 'g' + s, start: now, target: 2 }, now);
  assert.ok(grind.eclats <= REWARDS.hardCap);
});

test('ouvrir coûte un pack offert puis 100 éclats, et refuse sans fonds', () => {
  const a = fresh(); a.bonusPacks = 1; a.eclats = 150;
  openPack(a, 0); assert.equal(a.bonusPacks, 0); assert.equal(a.eclats, 150);
  openPack(a, 1); assert.ok(a.eclats >= 50);
  a.eclats = 0; assert.throws(() => openPack(a, 2), e => e.status === 402);
});

test('route HTTP : compte créé au premier appel, cadeau de 2 packs une fois, jeton vérifié', async () => {
  const server = await createRelay({ dataDir: await mkdtemp(join(tmpdir(), 'eco-')) });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}/v2/economy/` + randomBytes(32).toString('hex');
  const token = randomBytes(32).toString('hex');
  const call = (body, t = token) => fetch(base, { method: 'POST', headers: { 'X-Write-Token': t, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  let r = await call({ op: 'state' }); assert.equal(r.status, 200); let s = await r.json();
  assert.equal(s.bonusPacks, LAUNCH_GIFT.packs);
  r = await call({ op: 'open', booster: 0 }); s = await r.json(); assert.equal(r.status, 200); assert.ok(s.pulls.length >= 2);
  r = await call({ op: 'state' }); s = await r.json(); assert.equal(s.bonusPacks, 1, 'le cadeau ne revient pas');
  r = await call({ op: 'state' }, randomBytes(32).toString('hex')); assert.equal(r.status, 403);
  server.close();
});
