import { createHash, randomInt, randomUUID, timingSafeEqual } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, unlink, readdir } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Économie des cartes Athlète, tenue par le serveur pour qu'on ne puisse pas tricher : le portefeuille (éclats, packs
 * offerts), la collection, les garanties et le tirage des packs vivent ici. L'app ne déclare que des événements
 * (séance terminée, quête accomplie) ; le serveur fixe les montants, vérifie les fenêtres de temps, refuse les doublons
 * et plafonne les gains de la semaine. Aucune donnée de santé n'est envoyée : seulement des identifiants opaques et des dates.
 */
export const ECONOMY_VERSION = 1;
export const PACK_PRICE = 100;
/** Cadeau de lancement de la série Nature : deux packs, une fois par compte. */
export const LAUNCH_GIFT = { id: 'nature-4.0', packs: 2 };

// ---- Catalogue (doit rester aligné sur Catalog.kt : 4 biomes × 10 fonds, puis 12 épiques) ------------------
export const ELEMENTS = ['FEU', 'GLACE', 'FOUDRE', 'TERRE'];
const LAYOUT = ['COMMUNE', 'COMMUNE', 'COMMUNE', 'COMMUNE', 'PEU_COMMUNE', 'PEU_COMMUNE', 'PEU_COMMUNE', 'PEU_COMMUNE', 'RARE', 'RARE'];
const DUOS = [[0, 1], [0, 1], [0, 2], [0, 2], [0, 3], [0, 3], [1, 2], [1, 2], [1, 3], [1, 3], [2, 3], [2, 3]];
export const CATALOG = (() => {
  const cards = []; let n = 1;
  const id = () => 'N' + String(n++).padStart(3, '0');
  ELEMENTS.forEach((e, ei) => LAYOUT.forEach(rarity => cards.push({ id: id(), rarity, elements: [ei] })));
  DUOS.forEach(pair => cards.push({ id: id(), rarity: 'EPIQUE', elements: pair }));
  return cards;
})();
const RANK = { COMMUNE: 0, PEU_COMMUNE: 1, RARE: 2, EPIQUE: 3 };
const byId = new Map(CATALOG.map(c => [c.id, c]));

// ---- Tirage (aligné sur PackEngine.kt) ----------------------------------------------------------------------
export const RATES = {
  bonusOdds: 0.20, prismOdds: 1 / 1000, rarePity: 50, epicPity: 200,
  slot1: [['COMMUNE', 85], ['PEU_COMMUNE', 15]],
  slot2: [['COMMUNE', 40], ['PEU_COMMUNE', 58.05], ['RARE', 1.6], ['EPIQUE', 0.35]],
  slot3: [['PEU_COMMUNE', 94.45], ['RARE', 4.8], ['EPIQUE', 0.75]],
};
const random = () => randomInt(0, 2 ** 47) / 2 ** 47;
const pick = table => { let x = random() * table.reduce((s, [, w]) => s + w, 0); for (const [r, w] of table) { x -= w; if (x < 0) return r; } return table.at(-1)[0]; };
const finish = rank => { const x = random() * 100; return rank >= 2 && x < 1.5 ? 'ANIMEE' : rank >= 2 && x < 9.5 ? 'BRILLANTE' : rank < 2 && x < 3 ? 'BRILLANTE' : 'NORMALE'; };

export function draw(booster, sinceRare, sinceEpic) {
  const prism = random() < RATES.prismOdds;
  const rarities = prism ? ['RARE', 'RARE', 'EPIQUE'] : [pick(RATES.slot1), pick(RATES.slot2)];
  if (!prism && random() < RATES.bonusOdds) rarities.push(pick(RATES.slot3));
  const last = rarities.length - 1;
  if (sinceEpic + 1 >= RATES.epicPity && !rarities.includes('EPIQUE')) rarities[last] = 'EPIQUE';
  else if (sinceRare + 1 >= RATES.rarePity && !rarities.some(r => RANK[r] >= 2)) rarities[last] = 'RARE';
  rarities.sort((a, b) => RANK[a] - RANK[b]);
  const content = CATALOG.filter(c => c.elements.includes(booster));
  const pulls = rarities.map(r => { const pool = content.filter(c => c.rarity === r); const card = pool[randomInt(0, pool.length)]; return `${card.id}:${finish(RANK[r])}`; });
  return { pulls, prism, sinceRare: rarities.some(r => RANK[r] >= 2) ? 0 : sinceRare + 1, sinceEpic: rarities.includes('EPIQUE') ? 0 : sinceEpic + 1 };
}

// ---- Niveaux, remboursement et fabrication ---------------------------------------------------------------------
const LEVEL_COPIES = [1, 2, 4, 7, 11];
export const level = copies => Math.max(1, LEVEL_COPIES.filter(c => copies >= c).length);
/** Doublon d'un fond déjà au niveau 5 : rendu en éclats. */
export const REFUND = { COMMUNE: 5, PEU_COMMUNE: 10, RARE: 40, EPIQUE: 200 };
/** Prix d'un fond précis, en éclats : bien plus cher qu'un pack, pour viser une carte qui manque. */
export const CRAFT = { COMMUNE: 150, PEU_COMMUNE: 300, RARE: 1200, EPIQUE: 4000 };

// ---- Gains : l'algorithme qui vise 2 à 3 packs par semaine ----------------------------------------------------
/**
 * Un utilisateur impliqué (objectif de séances atteint, quêtes du jour et de la semaine faites) gagne environ 280 éclats
 * par semaine, soit 2,8 packs. Les séances rapportent 120 éclats répartis sur l'objectif, quel qu'il soit (deux séances
 * à 60 ou cinq à 24), plus deux séances bonus à moitié prix. Au-delà de 250 éclats, chaque gain est divisé par deux,
 * et la semaine plafonne à 330 (3,3 packs). Peu d'implication : une séance et deux quêtes ≈ 0,6 pack.
 */
export const REWARDS = {
  sessionPool: 120, extraSessions: 2,
  daily: { session: 6, rir: 6, sets: 6 },
  weekly: { target: 30, legs: 20, pr: 30 },
  league: 15, leaguePerWeek: 3,
  softCap: 250, hardCap: 330,
};
const DAY = 86_400_000;
const epochDay = ms => Math.floor(ms / DAY);
/** Lundi (epochDay) de la semaine ISO de [ms], en UTC ; une marge d'un jour couvre les fuseaux horaires. */
const monday = ms => { const d = epochDay(ms); return d - ((d + 3) % 7); };

function credit(account, amount, now) {
  const week = monday(now);
  if (account.week?.id !== week) account.week = { id: week, earned: 0, sessions: 0, league: 0 };
  const before = account.week.earned;
  let gained = 0;
  for (let i = 0; i < amount; i++) {
    const at = before + gained;
    if (at >= REWARDS.hardCap) break;
    gained += at >= REWARDS.softCap ? 0.5 : 1;
  }
  gained = Math.floor(gained);
  account.week.earned += gained; account.eclats += gained;
  return gained;
}

function remember(account, key, now) {
  account.claimed[key] = now;
  // Les clés de plus de 30 jours ne peuvent plus être rejouées (fenêtres de temps), on les oublie.
  for (const [k, t] of Object.entries(account.claimed)) if (t < now - 30 * DAY) delete account.claimed[k];
}

/** Applique un événement déclaré par l'app ; renvoie les éclats gagnés, ou lève une erreur 400/409. */
export function applyEvent(account, event, now) {
  const bad = () => { const e = new Error('event'); e.status = 400; throw e; };
  const seen = () => { const e = new Error('seen'); e.status = 409; throw e; };
  if (!event || typeof event !== 'object') bad();
  if (event.type === 'session') {
    if (typeof event.id !== 'string' || !/^[\w-]{1,64}$/.test(event.id) || !Number.isSafeInteger(event.start)) bad();
    if (event.start > now + 300_000 || event.start < now - 4 * DAY) bad();
    const key = 's:' + event.id; if (account.claimed[key]) seen();
    const target = Math.min(6, Math.max(2, Number.isSafeInteger(event.target) ? event.target : 3));
    credit(account, 0, now); remember(account, key, now);
    const n = account.week.sessions++;
    const full = Math.round(REWARDS.sessionPool / target);
    const amount = n < target ? full : n < target + REWARDS.extraSessions ? Math.round(full / 2) : 0;
    return credit(account, amount, now);
  }
  if (event.type === 'quest') {
    const m = typeof event.id === 'string' && /^(d|w):(session|rir|sets|target|legs|pr):(\d{4,6})$/.exec(event.id);
    if (!m) bad();
    const [, scope, kind, dayText] = m; const day = Number(dayText);
    const amount = scope === 'd' ? REWARDS.daily[kind] : REWARDS.weekly[kind];
    if (amount == null) bad();
    // Une quête du jour vaut pour aujourd'hui (±1 jour de fuseau), une quête de la semaine pour la semaine en cours.
    if (scope === 'd' && Math.abs(day - epochDay(now)) > 1) bad();
    if (scope === 'w' && Math.abs(day - monday(now)) > 1) bad();
    const key = 'q:' + event.id; if (account.claimed[key]) seen();
    remember(account, key, now);
    return credit(account, amount, now);
  }
  if (event.type === 'league') {
    if (typeof event.id !== 'string' || !/^[\w:-]{1,80}$/.test(event.id)) bad();
    const key = 'l:' + event.id; if (account.claimed[key]) seen();
    credit(account, 0, now);
    if (account.week.league >= REWARDS.leaguePerWeek) { remember(account, key, now); return 0; }
    account.week.league++; remember(account, key, now);
    return credit(account, REWARDS.league, now);
  }
  bad();
}

export function openPack(account, booster) {
  if (!Number.isSafeInteger(booster) || booster < 0 || booster >= ELEMENTS.length) { const e = new Error('booster'); e.status = 400; throw e; }
  if (account.bonusPacks > 0) account.bonusPacks--;
  else if (account.eclats >= PACK_PRICE) account.eclats -= PACK_PRICE;
  else { const e = new Error('funds'); e.status = 402; throw e; }
  const result = draw(booster, account.sinceRare, account.sinceEpic);
  account.sinceRare = result.sinceRare; account.sinceEpic = result.sinceEpic; account.opened++;
  const gains = result.pulls.map(key => {
    const card = byId.get(key.split(':')[0]);
    const before = Object.entries(account.owned).reduce((s, [k, v]) => k.startsWith(card.id + ':') ? s + v : s, 0);
    if (level(before) >= 5 && account.owned[key]) { account.eclats += REFUND[card.rarity]; return { key, copiesBefore: before, copiesAfter: before, refund: REFUND[card.rarity] }; }
    account.owned[key] = (account.owned[key] || 0) + 1;
    return { key, copiesBefore: before, copiesAfter: before + 1, refund: 0 };
  });
  return { pulls: gains, prism: result.prism };
}

export function craft(account, id) {
  const card = byId.get(id);
  if (!card) { const e = new Error('card'); e.status = 400; throw e; }
  if (account.eclats < CRAFT[card.rarity]) { const e = new Error('funds'); e.status = 402; throw e; }
  account.eclats -= CRAFT[card.rarity];
  account.owned[id + ':NORMALE'] = (account.owned[id + ':NORMALE'] || 0) + 1;
}

const view = a => ({ v: ECONOMY_VERSION, eclats: a.eclats, bonusPacks: a.bonusPacks, sinceRare: a.sinceRare, sinceEpic: a.sinceEpic, opened: a.opened,
  owned: a.owned, claimed: Object.keys(a.claimed), week: a.week ? { earned: a.week.earned, cap: REWARDS.hardCap } : { earned: 0, cap: REWARDS.hardCap },
  price: PACK_PRICE, gifts: a.gifts });

// ---- Stockage et routes : /v2/economy/{compte}, jeton d'écriture dans X-Write-Token ----------------------------
const HEX = /^[a-f0-9]{64}$/;
const digest = v => createHash('sha256').update(v).digest('hex');
const equal = (a, b) => Buffer.byteLength(a) === Buffer.byteLength(b) && timingSafeEqual(Buffer.from(a), Buffer.from(b));

export async function createEconomy(dataDir, { now = Date.now, maxAccounts = 5000, maxNewPerHour = 12, opsPerMinute = 30, secret = null } = {}) {
  const dir = join(dataDir, 'economy'); await mkdir(dir, { recursive: true, mode: 0o700 });
  let count = (await readdir(dir)).filter(n => /^[a-f0-9]{64}\.json$/.test(n)).length;
  let queue = Promise.resolve();
  const serialized = task => { const r = queue.then(task); queue = r.catch(() => {}); return r; };
  const ops = new Map(); const creations = new Map();
  const file = id => join(dir, id + '.json');
  const load = async id => { try { return JSON.parse(await readFile(file(id), 'utf8')); } catch (e) { if (e.code === 'ENOENT') return null; throw e; } };
  const save = async (id, account) => {
    const tmp = file(id) + '.' + randomUUID() + '.tmp';
    try { await writeFile(tmp, JSON.stringify(account), { mode: 0o600, flag: 'wx' }); await rename(tmp, file(id)); }
    finally { await unlink(tmp).catch(() => {}); }
  };
  const send = (res, status, body) => { res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' }); res.end(body ? JSON.stringify(body) : ''); };

  return async function handle(req, res, account, readBody) {
    if (req.method !== 'POST') { req.resume(); return send(res, 405); }
    if (secret && !equal(req.headers.authorization || '', secret)) { req.resume(); return send(res, 401); }
    const token = req.headers['x-write-token'];
    if (typeof token !== 'string' || !HEX.test(token)) { req.resume(); return send(res, 401); }
    const id = digest(account);
    const minute = Math.floor(now() / 60000); const prev = ops.get(id);
    const n = prev?.minute === minute ? prev.n + 1 : 1;
    if (!ops.has(id) && ops.size >= 4096) ops.clear();
    ops.set(id, { minute, n });
    if (n > opsPerMinute) { req.resume(); return send(res, 429); }
    let body;
    try { const raw = await readBody(req); if (Buffer.byteLength(raw) > 4096) return send(res, 413); body = JSON.parse(raw || '{}'); } catch { return send(res, 400); }
    return serialized(async () => {
      let acc = await load(id);
      if (acc && !equal(acc.writeHash, digest(token))) return send(res, 403);
      if (!acc) {
        if (count >= maxAccounts) return send(res, 507);
        const ip = digest(req.socket.remoteAddress || '?'); const hour = Math.floor(now() / 3600000);
        const c = creations.get(ip); if (c && c.hour === hour && c.n >= maxNewPerHour) return send(res, 429);
        if (!creations.has(ip) && creations.size >= 1024) creations.clear();
        creations.set(ip, { hour, n: c?.hour === hour ? c.n + 1 : 1 });
        acc = { writeHash: digest(token), eclats: 0, bonusPacks: 0, sinceRare: 0, sinceEpic: 0, opened: 0, owned: {}, claimed: {}, gifts: [], createdAt: now() };
        count++;
      }
      // Le cadeau de lancement arrive au premier contact après la mise à jour, une seule fois par compte.
      if (!acc.gifts.includes(LAUNCH_GIFT.id)) { acc.gifts.push(LAUNCH_GIFT.id); acc.bonusPacks += LAUNCH_GIFT.packs; }
      let extra = {};
      try {
        if (body.op === 'state') {}
        else if (body.op === 'events') {
          if (!Array.isArray(body.events) || body.events.length > 40) return send(res, 400);
          const results = body.events.map(ev => { try { return { gained: applyEvent(acc, ev, now()) }; } catch (e) { return { error: e.status || 400 }; } });
          extra = { results };
        } else if (body.op === 'open') extra = openPack(acc, body.booster);
        else if (body.op === 'craft') craft(acc, body.card);
        else return send(res, 400);
      } catch (e) { await save(id, acc); return send(res, e.status || 400, view(acc)); }
      await save(id, acc);
      send(res, 200, { ...view(acc), ...extra });
    });
  };
}
