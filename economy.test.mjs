import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtemp, readdir, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { CATALOG, draw, applyEvent, openPack, view, isGold, LAUNCH_GIFT, REGIFT, fuse, spare, levelOf } from './economy.mjs';
import { createRelay } from './server.mjs';

const DAY = 86_400_000;
const fresh = () => ({ eclats: 0, bonusPacks: 0, opened: 0, owned: {}, claimed: {}, gifts: [] });
const rarity = key => CATALOG.find(c => c.id === key.split(':')[0]);

test('catalogue aligné sur l’app : 4 × 36 fonds puis 36 duos', () => {
  assert.equal(CATALOG.length, 180);
  assert.equal(CATALOG[0].id, 'N001'); assert.equal(CATALOG.at(-1).id, 'N180');
  for (let e = 0; e < 4; e++) assert.equal(CATALOG.filter(c => c.elements.length === 1 && c.elements[0] === e).length, 36);
  const duos = CATALOG.filter(c => c.elements.length === 2);
  assert.equal(duos.length, 36);
  for (const r of ['COMMUNE', 'PEU_COMMUNE', 'RARE', 'EPIQUE']) assert.ok(duos.some(c => c.rarity === r), 'duo ' + r);
});

test('taux bas sans garantie, pack doré tous les dix qui triple les chances', () => {
  const n = 200000;
  const run = gold => { let rare = 0, epic = 0, duo = 0, chroma = 0;
    for (let i = 0; i < n; i++) {
      const r = draw(i % 4, gold);
      assert.ok(r.pulls.length >= 3 && r.pulls.length <= 4);
      for (const k of r.pulls) if (rarity(k).elements.length === 2) assert.ok(k.endsWith(':NORMALE'), 'duo sans effet ' + k);
      const cards = r.pulls.map(rarity);
      if (cards.some(c => c.rarity === 'RARE' || c.rarity === 'EPIQUE')) rare++;
      if (cards.some(c => c.rarity === 'EPIQUE')) epic++;
      if (cards.some(c => c.elements.length === 2)) duo++;
      if (r.pulls.some(k => k.endsWith(':CHROMATIQUE'))) chroma++;
    }
    return { rare: rare / n, epic: epic / n, duo: duo / n, chroma: chroma / n };
  };
  const normal = run(false), gold = run(true);
  assert.ok(normal.rare > 0.05 && normal.rare < 0.063, 'rare ' + normal.rare);
  assert.ok(normal.epic > 0.009 && normal.epic < 0.016, 'épique ' + normal.epic);
  assert.ok(normal.duo > 0.012 && normal.duo < 0.021, 'duo ' + normal.duo);
  assert.ok(normal.chroma > 0.008 && normal.chroma < 0.017, 'chromatique ' + normal.chroma);
  assert.ok(gold.chroma > 2.4 * normal.chroma, 'chromatique dorée ' + gold.chroma);
  assert.ok(gold.rare > 2.4 * normal.rare && gold.epic > 2.4 * normal.epic, 'doré ' + JSON.stringify(gold));
  assert.deepEqual([...Array(20).keys()].filter(isGold), [9, 19]);
});

test('quêtes : 2 à 3 par jour du serveur, la quête de la semaine du Groupe ; 4 à 6 packs pour une semaine impliquée', () => {
  const now = Date.UTC(2026, 10, 6, 20); // jeudi soir
  const day = Math.floor(now / DAY);
  const engaged = fresh();
  for (let s = 0; s < 3; s++) applyEvent(engaged, { type: 'session', id: 'w' + s, start: now - s * DAY, target: 3 }, now, 'a');
  for (const d of [day, day - 1, day - 2, day - 3]) {
    const quests = view(engaged, now, d, 'a').quests;
    assert.ok(quests.length >= 2 && quests.length <= 3, 'quêtes du jour ' + quests.length);
    assert.ok(quests.every(q => q.scope === 'd'));
  }
  for (const d of [day, day - 1]) for (const q of view(engaged, now, d, 'a').quests) applyEvent(engaged, { type: 'quest', id: q.id }, now, 'a');
  // Quête de la semaine du Groupe : principale puis secondaire, une seule fois chacune.
  assert.equal(applyEvent(engaged, { type: 'league', id: `lq:${day}:STRENGTH` }, now, 'a'), 90);
  assert.equal(applyEvent(engaged, { type: 'league', id: `lq:${day}:FOOD:s` }, now, 'a'), 55);
  assert.equal(applyEvent(engaged, { type: 'league', id: `lq:${day}:RUN` }, now, 'a'), 0);
  assert.ok(engaged.eclats >= 320 && engaged.eclats <= 450, 'impliqué ' + engaged.eclats);
  assert.throws(() => applyEvent(engaged, { type: 'session', id: 'w0', start: now, target: 3 }, now), e => e.status === 409);
  assert.throws(() => applyEvent(engaged, { type: 'league', id: `lq:${day}:STRENGTH` }, now, 'a'), e => e.status === 409);
  assert.throws(() => applyEvent(engaged, { type: 'quest', id: `d:${day}:inconnue` }, now, 'a'), e => e.status === 400);
  assert.throws(() => applyEvent(engaged, { type: 'session', id: 'vieux', start: now - 10 * DAY }, now), e => e.status === 400);
  const lazy = fresh();
  applyEvent(lazy, { type: 'session', id: 'x', start: now, target: 3 }, now, 'b'); applyEvent(lazy, { type: 'quest', id: `d:${day}:session` }, now, 'b');
  assert.ok(lazy.eclats < 100, 'creux ' + lazy.eclats);
  const grind = fresh();
  for (let s = 0; s < 30; s++) applyEvent(grind, { type: 'session', id: 'g' + s, start: now, target: 2 }, now, 'c');
  assert.ok(grind.eclats <= 230, 'séances ' + grind.eclats);
});

test('la difficulté des quêtes du jour suit les semaines : monte quand elles sont faites, baisse sinon', () => {
  const start = Date.UTC(2026, 10, 2, 12); // lundi
  const good = fresh(), bad = fresh();
  for (let w = 0; w < 3; w++) for (let d = 0; d < 5; d++) {
    const now = start + (w * 7 + d) * DAY; const day = Math.floor(now / DAY);
    for (const a of [good, bad]) view(a, now, day, 'x');
    applyEvent(good, { type: 'session', id: `g${w}${d}`, start: now, target: 4 }, now, 'x');
    applyEvent(bad, { type: 'session', id: `b${w}${d}`, start: now, target: 4 }, now, 'x');
    for (const q of view(good, now, day, 'x').quests) applyEvent(good, { type: 'quest', id: q.id }, now, 'x');
  }
  const later = start + 21 * DAY; view(good, later, Math.floor(later / DAY), 'x'); view(bad, later, Math.floor(later / DAY), 'x');
  assert.ok(good.adapt.d > bad.adapt.d, `palier ${good.adapt.d} vs ${bad.adapt.d}`);
});

test('ouvrir coûte un pack offert puis 100 éclats, et refuse sans fonds', () => {
  const a = fresh(); a.bonusPacks = 1; a.eclats = 150;
  openPack(a, 0); assert.equal(a.bonusPacks, 0); assert.equal(a.eclats, 150);
  openPack(a, 1); assert.ok(a.eclats >= 50);
  a.eclats = 0; assert.throws(() => openPack(a, 2), e => e.status === 402);
});

test('fusion 2/4/16/256 : les doublons se consomment, un exemplaire de chaque finition reste, et quelques éclats', () => {
  const a = fresh();
  assert.throws(() => { a.owned = { 'N001:NORMALE': 3 }; fuse(a, 'N001'); }, e => e.status === 402, 'sans éclats, pas de fusion');
  a.eclats = 140;
  a.owned = { 'N001:NORMALE': 3, 'N001:BRILLANTE': 1 };
  assert.equal(spare(a, 'N001'), 2); assert.equal(levelOf(a, 'N001'), 1);
  fuse(a, 'N001'); assert.equal(levelOf(a, 'N001'), 2); assert.equal(a.owned['N001:NORMALE'], 2); assert.equal(a.owned['N001:BRILLANTE'], 1);
  assert.throws(() => fuse(a, 'N001'), e => e.status === 402, 'il faut 2 doublons pour le niveau 3');
  a.owned['N001:NORMALE'] = 3; fuse(a, 'N001'); assert.equal(levelOf(a, 'N001'), 3); assert.equal(a.owned['N001:NORMALE'], 1);
  a.owned['N001:ANIMEE'] = 13; fuse(a, 'N001'); assert.equal(levelOf(a, 'N001'), 4); assert.equal(a.owned['N001:ANIMEE'], 1);
  assert.equal(a.eclats, 0, '20 + 40 + 80 éclats');
});

test('route HTTP : compte créé au premier appel, cadeau de 10 packs une fois, jeton vérifié', async () => {
  const server = await createRelay({ dataDir: await mkdtemp(join(tmpdir(), 'eco-')) });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const base = `http://127.0.0.1:${server.address().port}/v2/economy/` + randomBytes(32).toString('hex');
  const token = randomBytes(32).toString('hex');
  const call = (body, t = token) => fetch(base, { method: 'POST', headers: { 'X-Write-Token': t, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  let r = await call({ op: 'state' }); assert.equal(r.status, 200); let s = await r.json();
  assert.equal(s.bonusPacks, LAUNCH_GIFT.packs);
  r = await call({ op: 'open', booster: 0 }); s = await r.json(); assert.equal(r.status, 200); assert.ok(s.pulls.length >= 3);
  r = await call({ op: 'state' }); s = await r.json(); assert.equal(s.bonusPacks, LAUNCH_GIFT.packs - 1, 'le cadeau ne revient pas');
  r = await call({ op: 'state' }, randomBytes(32).toString('hex')); assert.equal(r.status, 403);
  server.close();
});

test('rattrapage 3.6.2 : cinq packs pour les comptes qui avaient déjà ouvert des packs, une seule fois', async () => {
  const dataDir = await mkdtemp(join(tmpdir(), 'eco-'));
  let server = await createRelay({ dataDir });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const url = () => `http://127.0.0.1:${server.address().port}/v2/economy/`;
  const box = randomBytes(32).toString('hex'), idle = randomBytes(32).toString('hex'), token = randomBytes(32).toString('hex');
  const call = (b, body) => fetch(url() + b, { method: 'POST', headers: { 'X-Write-Token': token, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  await call(box, { op: 'state' }); await call(box, { op: 'open', booster: 0 }); await call(idle, { op: 'state' });
  server.close();
  // Comptes d'avant la mise à jour : cadeau de lancement reçu, sans le rattrapage.
  for (const f of await readdir(join(dataDir, 'economy'))) {
    const p = join(dataDir, 'economy', f); const a = JSON.parse(await readFile(p, 'utf8'));
    a.gifts = a.gifts.filter(g => g !== REGIFT.id); await writeFile(p, JSON.stringify(a));
  }
  server = await createRelay({ dataDir });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  let s = await (await call(box, { op: 'state' })).json();
  assert.equal(s.bonusPacks, LAUNCH_GIFT.packs - 1 + REGIFT.packs);
  s = await (await call(box, { op: 'state' })).json(); assert.equal(s.bonusPacks, LAUNCH_GIFT.packs - 1 + REGIFT.packs, 'une seule fois');
  s = await (await call(idle, { op: 'state' })).json(); assert.equal(s.bonusPacks, LAUNCH_GIFT.packs, 'rien sans pack ouvert');
  await call(idle, { op: 'open', booster: 0 });
  s = await (await call(idle, { op: 'state' })).json(); assert.equal(s.bonusPacks, LAUNCH_GIFT.packs - 1, 'pas après coup');
  server.close();
});

test('échange entre amis : carte mise de côté, même rareté, échange d’un coup ou rendue', async () => {
  const server = await createRelay({ dataDir: await mkdtemp(join(tmpdir(), 'eco-')) });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  const user = () => { const account = randomBytes(32).toString('hex'), token = randomBytes(32).toString('hex');
    return body => fetch(`http://127.0.0.1:${port}/v2/economy/${account}`, { method: 'POST', headers: { 'X-Write-Token': token, 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(async r => ({ status: r.status, ...(await r.json().catch(() => ({}))) })); };
  const alice = user(), bob = user();
  let a = await alice({ op: 'state' }), b = await bob({ op: 'state' });
  for (let i = 0; i < 4; i++) { a = await alice({ op: 'open', booster: 0 }); b = await bob({ op: 'open', booster: 1 }); }
  const common = s => Object.keys(s.owned).find(k => rarity(k).rarity === 'COMMUNE');
  const give = common(a), want = common(b);
  const offerCards = (g, w) => alice({ op: 'trade-offer', to: b.tradeId, give: { card: g }, want: { card: w } });
  const other = Object.keys(b.owned).find(k => rarity(k).rarity !== 'COMMUNE');
  if (other) assert.equal((await offerCards(give, other)).status, 400, 'rareté différente');
  assert.equal((await alice({ op: 'trade-offer', to: b.tradeId, give: { eclats: 10 }, want: { eclats: 5 } })).status, 400, 'au moins une carte');
  const browse = await alice({ op: 'trade-browse', to: b.tradeId }); assert.ok(browse.browse.owned[want]);
  const before = a.owned[give];
  const offer = await offerCards(give, want); assert.equal(offer.status, 200);
  assert.equal(offer.owned[give] ?? 0, before - 1, 'carte mise de côté');
  b = await bob({ op: 'state' }); assert.equal(b.trades.in.length, 1);
  const accepted = await bob({ op: 'trade-accept', id: b.trades.in[0].id }); assert.equal(accepted.traded, true);
  assert.ok(accepted.owned[give] >= 1);
  a = await alice({ op: 'state' }); assert.ok(a.owned[want] >= 1); assert.equal(a.trades.out.length, 0);
  // Une carte contre des éclats : refusée, la carte revient ; acceptée, les éclats passent.
  const card = Object.keys(a.owned)[0], count = a.owned[card];
  await alice({ op: 'trade-offer', to: b.tradeId, give: { card }, want: { eclats: 30 } });
  b = await bob({ op: 'state' }); await bob({ op: 'trade-decline', id: b.trades.in[0].id });
  a = await alice({ op: 'state' }); assert.equal(a.owned[card], count);
  await alice({ op: 'trade-offer', to: b.tradeId, give: { card }, want: { eclats: 30 } });
  b = await bob({ op: 'state' });
  assert.equal((await bob({ op: 'trade-accept', id: b.trades.in[0].id })).status, 402, 'bob n’a pas les éclats');
  server.close();
});

test('défis entre amis réciproques : course gagnée par le premier, ensemble payé quand tous ont fini', async () => {
  const { CHALLENGES, formChallenge } = await import('./economy.mjs');
  // Tirage pur : deux participants au départ, un troisième seulement s'il est ami de tous, de moins en moins souvent.
  const friends = { a: ['b', 'c', 'd'], b: ['a', 'c'], c: ['a', 'b'], d: ['a'] };
  let sizes = [0, 0, 0, 0, 0, 0];
  for (let seed = 0; seed < 4000; seed++) { const c = formChallenge('a', 1, ['b', 'c', 'd'], seed * 2654435761, m => friends[m]); if (c) { sizes[c.members.length]++; assert.ok(!c.members.includes('d') || c.members.length === 2 && c.members[1] === 'd'); } }
  assert.ok(sizes[2] > sizes[3] && sizes[3] > 0, 'tailles ' + sizes);
  const odds = CHALLENGES.odds; CHALLENGES.odds = 1;
  const server = await createRelay({ dataDir: await mkdtemp(join(tmpdir(), 'eco-')) });
  await new Promise(r => server.listen(0, '127.0.0.1', r));
  const port = server.address().port;
  try {
  const user = () => { const account = randomBytes(32).toString('hex'), token = randomBytes(32).toString('hex');
    return body => fetch(`http://127.0.0.1:${port}/v2/economy/${account}`, { method: 'POST', headers: { 'X-Write-Token': token, 'Content-Type': 'application/json' }, body: JSON.stringify(body) }).then(async r => ({ status: r.status, ...(await r.json().catch(() => ({}))) })); };
  const alice = user(), bob = user();
  const a0 = await alice({ op: 'state' }), b0 = await bob({ op: 'state' });
  await bob({ op: 'state', friends: [a0.tradeId] });
  // Bob a déjà tiré sa semaine (sans amis) : c'est Alice qui forme le défi.
  const a1 = await alice({ op: 'state', friends: [b0.tradeId] });
  assert.ok(a1.challenge, 'défi formé');
  assert.deepEqual(new Set(a1.challenge.members), new Set([a0.tradeId, b0.tradeId]));
  const b1 = await bob({ op: 'state' });
  assert.equal(b1.challenge.id, a1.challenge.id, 'le même défi pour les deux');
  const before = a1.eclats;
  const done = await alice({ op: 'challenge-done', id: a1.challenge.id });
  if (a1.challenge.type === 'race') {
    assert.equal(done.eclats, before + CHALLENGES.race);
    const late = await bob({ op: 'challenge-done', id: a1.challenge.id });
    assert.equal(late.eclats, b1.eclats, 'le second ne gagne rien');
    assert.equal(late.challenge.winner, a0.tradeId);
  } else {
    assert.equal(done.eclats, before, 'ensemble : rien tant que tous n’ont pas fini');
    const last = await bob({ op: 'challenge-done', id: a1.challenge.id });
    assert.equal(last.eclats, b1.eclats + CHALLENGES.together);
    assert.equal((await alice({ op: 'state' })).eclats, before + CHALLENGES.together);
  }
  assert.equal((await alice({ op: 'challenge-done', id: a1.challenge.id })).status, 409);
  } finally { CHALLENGES.odds = odds; server.close(); }
});

test('badges payés une fois selon leur rang, séries hebdo : +10 éclats par semaine tenue', async () => {
  const { badgeReward, BADGES } = await import('./economy.mjs');
  assert.equal(badgeReward('strength_10'), BADGES.reward[0]);
  assert.equal(badgeReward('distance_25000'), BADGES.reward[6]);
  assert.equal(badgeReward('streak_FOOD_10'), 200);
  assert.equal(badgeReward('streak_STRENGTH_30'), 250);
  assert.equal(badgeReward('strength_1'), null); assert.equal(badgeReward('hikes_1'), null); assert.equal(badgeReward('streak_HIKE_4'), null);
  const a = fresh(); const now = Date.UTC(2026, 10, 4, 12);
  assert.equal(applyEvent(a, { type: 'badge', id: 'food_100' }, now), 50);
  assert.throws(() => applyEvent(a, { type: 'badge', id: 'food_100' }, now + 40 * DAY), e => e.status === 409, 'jamais deux fois, même plus tard');
  assert.throws(() => applyEvent(a, { type: 'badge', id: 'food_31' }, now), e => e.status === 400);
  const gains = [1, 2, 3, 4].map((w, k) => applyEvent(a, { type: 'streak', track: 'FOOD', weeks: w }, now + k * 7 * DAY));
  assert.deepEqual(gains, [10, 20, 30, 40]);
  assert.throws(() => applyEvent(a, { type: 'streak', track: 'FOOD', weeks: 5 }, now + 3 * 7 * DAY + DAY), e => e.status === 409, 'une fois par semaine');
  assert.equal(applyEvent(a, { type: 'streak', track: 'FOOD', weeks: 50 }, now + 4 * 7 * DAY), 50, 'pas plus que les semaines écoulées');
  assert.equal(applyEvent(a, { type: 'streak', track: 'FOOD', weeks: 30 }, now + 30 * 7 * DAY), 100, 'plafond à 10 semaines');
  assert.equal(applyEvent(a, { type: 'streak', track: 'MIND', weeks: 1 }, now), 10);
});

test('départ de la mise à jour : badges déjà obtenus payés sans éclats, une seule fois', () => {
  const a = fresh(); const now = Date.UTC(2026, 10, 4, 12);
  assert.equal(applyEvent(a, { type: 'baseline', badges: ['food_30', 'strength_10', 'xp_100'] }, now), 0);
  assert.equal(a.eclats, 0);
  assert.throws(() => applyEvent(a, { type: 'badge', id: 'food_30' }, now), e => e.status === 409);
  assert.throws(() => applyEvent(a, { type: 'baseline', badges: [] }, now), e => e.status === 409);
  assert.equal(applyEvent(a, { type: 'badge', id: 'food_100' }, now), 50);
});
