import { createHash, randomInt, randomUUID, timingSafeEqual } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, unlink, readdir } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Économie des cartes Athlète, tenue par le serveur pour qu'on ne puisse pas tricher : le portefeuille (éclats, packs
 * offerts), la collection, la fusion des doublons, les échanges entre joueurs, le compteur du pack doré, les quêtes et le tirage des packs vivent ici. L'app ne déclare que
 * des événements (séance terminée, quête accomplie) ; le serveur fixe les montants, vérifie les fenêtres de temps et
 * refuse les doublons. Aucune donnée de santé n'est envoyée : seulement des identifiants opaques et des dates.
 */
export const ECONOMY_VERSION = 3;
export const PACK_PRICE = 100;
/** Cadeau de la mise à jour Dopamine : dix packs (donc au moins un pack doré), une fois par compte. */
export const LAUNCH_GIFT = { id: 'dopamine-3.6', packs: 10 };
/** Cadeau de la 3.6.2 : cinq packs de plus pour qui a déjà ouvert ses packs de lancement, une fois par compte. */
export const REGIFT = { id: 'regift-3.6.2', packs: 5 };

// ---- Catalogue (doit rester aligné sur Catalog.kt) ------------------------------------------------------------
// 4 biomes × 36 fonds (14 communes, 11 peu communes, 7 rares, 4 épiques), puis 6 duos par paire de biomes.
export const ELEMENTS = ['FEU', 'GLACE', 'FOUDRE', 'TERRE'];
const rep = (r, n) => Array(n).fill(r);
const LAYOUT = [...rep('COMMUNE', 14), ...rep('PEU_COMMUNE', 11), ...rep('RARE', 7), ...rep('EPIQUE', 4)];
const PAIRS = [[0, 1], [0, 2], [0, 3], [1, 2], [1, 3], [2, 3]];
const DUO_LAYOUT = [...rep('COMMUNE', 2), ...rep('PEU_COMMUNE', 2), 'RARE', 'EPIQUE'];
export const CATALOG = (() => {
  const cards = []; let n = 1;
  const id = () => 'N' + String(n++).padStart(3, '0');
  ELEMENTS.forEach((e, ei) => LAYOUT.forEach(rarity => cards.push({ id: id(), rarity, elements: [ei] })));
  PAIRS.forEach(pair => DUO_LAYOUT.forEach(rarity => cards.push({ id: id(), rarity, elements: pair })));
  return cards;
})();
const RANK = { COMMUNE: 0, PEU_COMMUNE: 1, RARE: 2, EPIQUE: 3 };
const byId = new Map(CATALOG.map(c => [c.id, c]));

// ---- Tirage (aligné sur PackEngine.kt) ----------------------------------------------------------------------
/**
 * Trois cartes, une quatrième une fois sur quatre. Chaque carte est soit un duo (1 sur 200, finition normale), soit
 * normale, brillante, animée ou chromatique : rien ne se cumule. Pas de garantie : un pack sur dix est doré et triple
 * les chances de rare, d'épique, de duo, d'animée, de chromatique et de Prisme (trois rares et une épique).
 */
export const RATES = {
  bonusOdds: 0.25, prismOdds: 1 / 1000, duoOdds: 0.005, goldEvery: 10, goldBoost: 3,
  prism: ['RARE', 'RARE', 'RARE', 'EPIQUE'],
  // Effets par rareté, en % par carte : chromatique, animée, brillante (chromatique et animée triplées en doré).
  effects: { COMMUNE: [0.3, 0.8, 3], PEU_COMMUNE: [0.5, 1.2, 5], RARE: [0.8, 2, 8], EPIQUE: [1.2, 3, 12] },
  slot1: [['COMMUNE', 82], ['PEU_COMMUNE', 18]],
  slot2: [['COMMUNE', 55], ['PEU_COMMUNE', 45]],
  slot3: [['COMMUNE', 20], ['PEU_COMMUNE', 76.7], ['RARE', 2.6], ['EPIQUE', 0.7]],
  slot4: [['PEU_COMMUNE', 90.5], ['RARE', 7.5], ['EPIQUE', 2]],
};
const random = () => randomInt(0, 2 ** 47) / 2 ** 47;
const boosted = (table, gold) => gold ? table.map(([r, w]) => [r, RANK[r] >= 2 ? w * RATES.goldBoost : w]) : table;
const pick = (table, rnd = random) => { let x = rnd() * table.reduce((s, [, w]) => s + w, 0); for (const [r, w] of table) { x -= w; if (x < 0) return r; } return table.at(-1)[0]; };
const finish = (rarity, gold) => {
  const [chroma, anim, shine] = RATES.effects[rarity]; const k = gold ? RATES.goldBoost : 1;
  let x = random() * 100;
  for (const [name, p] of [['CHROMATIQUE', chroma * k], ['ANIMEE', anim * k], ['BRILLANTE', shine]]) { x -= p; if (x < 0) return name; }
  return 'NORMALE';
};
export const isGold = opened => (opened + 1) % RATES.goldEvery === 0;

export function draw(booster, gold = false) {
  const k = gold ? RATES.goldBoost : 1;
  const prism = random() < RATES.prismOdds * k;
  const rarities = prism ? [...RATES.prism] : [RATES.slot1, RATES.slot2, RATES.slot3].map(t => pick(boosted(t, gold)));
  if (!prism && random() < RATES.bonusOdds) rarities.push(pick(boosted(RATES.slot4, gold)));
  rarities.sort((a, b) => RANK[a] - RANK[b]);
  const content = CATALOG.filter(c => c.elements.includes(booster));
  const pulls = rarities.map(r => {
    const duo = random() < RATES.duoOdds * k;
    const pool = content.filter(c => c.rarity === r && (c.elements.length === 2) === duo);
    return `${pool[randomInt(0, pool.length)].id}:${duo ? 'NORMALE' : finish(r, gold)}`;
  });
  return { pulls, prism, gold };
}

// ---- Fusion ---------------------------------------------------------------------------------------------------------
/** Exemplaires fusionnés cumulés pour chaque niveau : 2 pour +10 % d'effet, 4 pour +20 %, 16 pour +30 %, 256 pour +40 %. */
export const LEVEL_COPIES = [1, 2, 4, 16, 256];
/** Éclats d'une fusion vers le niveau 2, 3, 4 puis 5 : un petit coût, qui monte avec le niveau. */
export const FUSE_PRICE = [20, 40, 80, 160];
export const level = copies => Math.max(1, LEVEL_COPIES.filter(c => copies >= c).length);
const FINISHES = ['NORMALE', 'BRILLANTE', 'ANIMEE', 'CHROMATIQUE'];

const fail = (status, msg = 'bad') => { const e = new Error(msg); e.status = status; throw e; };
const KEY = /^(N\d{3}):(NORMALE|BRILLANTE|ANIMEE|CHROMATIQUE)$/;
const cardOf = key => { const m = typeof key === 'string' && KEY.exec(key); return m && byId.get(m[1]) ? byId.get(m[1]) : null; };
const finishesOf = (a, id) => FINISHES.filter(f => (a.owned[`${id}:${f}`] || 0) > 0);
/** Doublons d'un fond : tous ses exemplaires sauf un de chaque finition possédée. */
export const spare = (a, id) => finishesOf(a, id).reduce((s, f) => s + a.owned[`${id}:${f}`] - 1, 0);
export const levelOf = (a, id) => level(1 + ((a.merged || {})[id] || 0));
const take = (a, key, n = 1) => { a.owned[key] -= n; if (a.owned[key] <= 0) delete a.owned[key]; };
const give = (a, key, n = 1) => { a.owned[key] = (a.owned[key] || 0) + n; };

/** Fusionne assez de doublons d'un fond (les finitions les plus simples d'abord) pour le faire monter d'un niveau. */
export function fuse(a, id) {
  if (!byId.has(id)) fail(400);
  a.merged ??= {};
  const l = levelOf(a, id); if (l >= LEVEL_COPIES.length) fail(409);
  let cost = LEVEL_COPIES[l] - LEVEL_COPIES[l - 1];
  if (spare(a, id) < cost) fail(402);
  const price = FUSE_PRICE[l - 1];
  if ((a.eclats || 0) < price) fail(402);
  a.eclats -= price;
  a.merged[id] = (a.merged[id] || 0) + cost;
  for (const f of FINISHES) { const key = `${id}:${f}`; const n = Math.min(cost, (a.owned[key] || 0) - 1); if (n > 0) { take(a, key, n); cost -= n; } }
  return { level: l + 1 };
}

// ---- Échanges entre joueurs -----------------------------------------------------------------------------------------
/**
 * Pas de boutique : les cartes ne changent de main qu'entre joueurs, carte contre carte (même rareté) ou carte contre
 * éclats, dans un sens ou dans l'autre. Un échange se propose par l'identifiant d'échange de l'autre (l'identifiant de
 * stockage de son compte, qui ne permet pas d'écrire : il faut aussi le compte et le jeton). Ce qu'on offre est mis de
 * côté dès la proposition ; à l'acceptation, tout change de main d'un coup. Dix propositions au plus, sept jours pour
 * répondre, sinon la mise revient à son propriétaire.
 */
export const TRADES = { maxOut: 10, maxIn: 30, ttl: 7 * 86_400_000, maxEclats: 5000 };
export const tradesOf = a => (a.trades ??= { out: [], in: [] });
/** Une mise : une carte ([card]) ou des éclats ([eclats]), jamais les deux. */
const stake = s => s && typeof s === 'object' && (cardOf(s.card) ? !('eclats' in s) : Number.isSafeInteger(s.eclats) && s.eclats >= 1 && s.eclats <= TRADES.maxEclats && !('card' in s));
const hold = (a, s) => s.card ? take(a, s.card) : (a.eclats -= s.eclats);
const pay = (a, s) => s.card ? give(a, s.card) : (a.eclats += s.eclats);
const has = (a, s) => s.card ? (a.owned[s.card] || 0) > 0 : a.eclats >= s.eclats;
/** Retire les propositions expirées ; celles que ce compte a faites lui rendent leur mise. */
export function pruneTrades(a, now) {
  const t = tradesOf(a);
  for (const o of t.out.filter(o => o.at < now - TRADES.ttl)) pay(a, o.give);
  t.out = t.out.filter(o => o.at >= now - TRADES.ttl); t.in = t.in.filter(o => o.at >= now - TRADES.ttl);
}
/** Échange valide : au moins une carte ; deux cartes de même rareté. */
export function validTrade(giveS, wantS) {
  if (!stake(giveS) || !stake(wantS) || (!giveS.card && !wantS.card)) return false;
  return !(giveS.card && wantS.card) || cardOf(giveS.card).rarity === cardOf(wantS.card).rarity;
}

// ---- Gains et quêtes ---------------------------------------------------------------------------------------------
/**
 * Aucun plafond affiché ni appliqué : ce sont les sources de gains qui sont bornées. Les séances rapportent 150 éclats
 * répartis sur l'objectif (plus deux séances bonus à moitié prix), les deux ou trois quêtes du jour quelques éclats
 * chacune, et la quête de la semaine (celle du Groupe, adaptée en local à tous les domaines suivis) 90 éclats, plus 55
 * pour sa quête secondaire. Un utilisateur qui tient son objectif ouvre ainsi 4 à 5 packs par semaine, un très assidu
 * jusqu'à 6, et quelqu'un qui s'entraîne peu nettement moins.
 *
 * La difficulté des quêtes du jour s'adapte pour qu'elles restent faisables : chaque semaine, le palier monte un peu
 * quand presque toutes ont été faites et redescend nettement quand la plupart ont été manquées. La troisième quête du
 * jour n'apparaît que pour qui suit bien. L'app traduit le palier (0 à 2) en objectif concret à partir des habitudes de
 * l'utilisateur (séries par séance, groupes musculaires).
 */
export const REWARDS = {
  sessionPool: 150, extraSessions: 2,
  daily: { session: 10, other: 10 },
  weekly: { base: 100, span: 60 },
  league: { primary: 90, secondary: 55 },
};
/**
 * Badges : chaque palier atteint rapporte des éclats, une seule fois par compte, de plus en plus à mesure que les paliers
 * montent (seuils de Achievements.kt, volontairement hauts). Séries : toutes hebdomadaires (une semaine compte dès 4 jours
 * tenus sur 7, ou l'objectif de séances) ; chaque semaine d'affilée rapporte 10 éclats de plus (10, 20, 30…, plafond 100 à
 * 10 semaines), une fois par semaine et par suivi. Les paliers de série (en semaines) se paient comme les autres badges.
 */
export const BADGES = {
  families: { strength: [10, 50, 100, 250, 500, 1000, 2000], records: [5, 25, 50, 100, 250, 500, 1000], food: [30, 100, 250, 500, 1000, 2000],
    mind: [300, 1000, 3000, 10000, 25000, 50000], quests: [10, 25, 50, 100, 250, 500, 1000], distance: [50, 250, 1000, 2500, 5000, 10000, 25000] },
  streakWeeks: [1, 4, 10, 20, 30, 52, 104],
  tracks: ['FOOD', 'STRENGTH', 'RUN', 'STEPS', 'SLEEP', 'MIND'],
  reward: [25, 50, 100, 175, 275, 400, 600],
  streakReward: [20, 100, 200, 225, 250, 400, 600],
  streak: { step: 10, weeks: 10 },
};
/** Éclats d'un badge (son rang dans son type), ou null s'il n'existe pas. */
export function badgeReward(id) {
  const m = typeof id === 'string' && /^(?:streak_([A-Z]+)|([a-z_]+))_(\d{1,6})$/.exec(id);
  if (!m) return null;
  const n = Number(m[3]);
  if (m[1]) { const i = BADGES.tracks.includes(m[1]) ? BADGES.streakWeeks.indexOf(n) : -1; return i < 0 ? null : BADGES.streakReward[i]; }
  const i = BADGES.families[m[2]]?.indexOf(n) ?? -1;
  return i < 0 ? null : BADGES.reward[i];
}
/** Bonus d'une semaine de série tenue depuis [weeks] semaines d'affilée. */
export const streakBonus = weeks => BADGES.streak.step * Math.min(Math.max(1, weeks), BADGES.streak.weeks);

const DAY = 86_400_000;
const epochDay = ms => Math.floor(ms / DAY);
/** Lundi (epochDay) de la semaine de l'epochDay [d]. */
const mondayOf = d => d - ((d + 3) % 7);
const monday = ms => mondayOf(epochDay(ms));
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
const STATS = ['PEC', 'DOS', 'BRA', 'JAM'];
const DAILY_KINDS = ['sets', 'rir', 'focus'];
/** Graine déterministe d'une période, propre au compte : deux comptes n'ont pas les mêmes quêtes le même jour. */
const hash = (salt, text) => createHash('sha256').update(salt + ':' + text).digest().readUInt32BE(0);

/** Passage à une nouvelle semaine : bilan de la précédente, ajustement de la difficulté et de l'implication. */
function rollWeek(account, now) {
  const week = monday(now);
  account.adapt ??= { d: 1, w: 1, engage: 0.8 };
  // Les semaines enregistrées par la version 1 n'avaient pas ces compteurs.
  if (account.week?.id === week) return Object.assign(account.week, { target: 3, wq: 0, wdone: 0, ddone: 0, days: 0, active: [], ...account.week });
  const last = account.week;
  if (last && last.id === week - 7) {
    const a = account.adapt;
    const fw = last.wq ? last.wdone / last.wq : 0;
    a.w = clamp(a.w + (fw >= 1 ? 0.4 : fw >= 0.5 ? 0 : -0.6), 0, 2);
    const days = last.days || 0;
    if (days > 0) { const fd = last.ddone / (2 * days); a.d = clamp(a.d + (fd >= 0.9 ? 0.3 : fd >= 0.4 ? 0 : -0.5), 0, 2); }
    a.engage = clamp(0.6 * a.engage + 0.4 * Math.min(1.5, (last.sessions || 0) / (last.target || 3)), 0, 1.5);
  } else if (last) account.adapt.engage = clamp(account.adapt.engage * 0.5, 0, 1.5); // retour après une absence : quêtes faciles
  account.week = { id: week, earned: 0, sessions: 0, league: 0, target: last?.target || 3, wq: 0, wdone: 0, ddone: 0, days: 0, active: [] };
  return account.week;
}

const tierOf = (level, offset = 0) => clamp(Math.round(level) + offset, 0, 2);

/** Quêtes d'un jour : valider une séance, plus une ou deux quêtes qui changent chaque jour (séries, RIR, groupe musculaire). */
function dailyQuests(account, day, salt) {
  const h = hash(salt, 'd' + day); const a = account.adapt;
  const extras = a.d >= 1 && (h >> 12) % 2 === 0 ? 2 : 1;
  const kinds = [0, 1, 2].map(k => DAILY_KINDS[(h + k) % DAILY_KINDS.length]).slice(0, extras);
  return [{ id: `d:${day}:session`, kind: 'session', scope: 'd', tier: 0, reward: REWARDS.daily.session },
    ...kinds.map((kind, k) => {
      // La deuxième quête variable est toujours un cran plus facile.
      const tier = tierOf(a.d, k === 1 || (h >> 8) % 3 === 0 ? -1 : 0);
      const q = { id: `d:${day}:${kind}`, kind, scope: 'd', tier, reward: REWARDS.daily.other + tier };
      if (kind === 'focus') q.stat = STATS[(h >> 4) % STATS.length];
      return q;
    })];
}

/** Quêtes d'une semaine : l'objectif de séances, puis deux défis dont un plus facile ; budget selon l'implication. */
function weeklyQuests(account, week, salt) {
  const h = hash(salt, 'w' + week); const a = account.adapt;
  const budget = Math.round(REWARDS.weekly.base + REWARDS.weekly.span * clamp(a.engage, 0, 1.25) / 1.25);
  const stat = STATS[h % STATS.length];
  const second = (h >> 3) % 2 ? 'volume' : 'family';
  // Le record est réservé à qui suit bien ; sinon un groupe musculaire, plus simple à viser.
  const third = a.w >= 0.8 && second !== 'pr' && (h >> 5) % 3 !== 0 ? 'pr' : second === 'family' ? 'volume' : 'family';
  const easy = (h >> 7) % 2 ? 1 : 2; // laquelle des deux est la plus facile
  const quests = [
    { id: `w:${week}:target`, kind: 'target', scope: 'w', tier: tierOf(a.w), share: 0.4 },
    { id: `w:${week}:${second}`, kind: second, scope: 'w', tier: tierOf(a.w, easy === 1 ? -1 : 0), share: 0.3 },
    { id: `w:${week}:${third}`, kind: third, scope: 'w', tier: third === 'pr' ? 2 : tierOf(a.w, easy === 2 ? -1 : 0), share: 0.3 },
  ];
  // Un palier plus dur rapporte un peu plus, un palier facile un peu moins ; le total reste proche du budget.
  return quests.map(({ share, ...q }) => ({ ...q, reward: Math.round(budget * share * (0.85 + 0.15 * q.tier)), ...(q.kind === 'family' ? { stat } : {}) }));
}

/** Quêtes d'une période, générées une fois puis gardées : elles ne changent plus si la difficulté bouge ensuite. */
function questsFor(account, key, salt) {
  account.quests ??= {};
  if (!account.quests[key]) {
    const [scope, n] = [key[0], Number(key.slice(2))];
    account.quests[key] = scope === 'd' ? dailyQuests(account, n, salt) : weeklyQuests(account, n, salt);
    if (scope === 'w' && account.week && account.week.id - n <= 1 && n - account.week.id <= 1) account.week.wq += account.quests[key].length;
    const keep = Object.keys(account.quests).sort((x, y) => Number(y.slice(2)) - Number(x.slice(2))).slice(0, 14);
    for (const k of Object.keys(account.quests)) if (!keep.includes(k)) delete account.quests[k];
  }
  return account.quests[key];
}

/** Jour local de l'app, accepté à un jour près (fuseaux horaires) ; sinon le jour UTC du serveur. */
const localDay = (today, now) => Number.isSafeInteger(today) && Math.abs(today - epochDay(now)) <= 1 ? today : epochDay(now);

function credit(account, amount, now) {
  const week = rollWeek(account, now);
  const gained = Math.max(0, Math.floor(amount));
  week.earned += gained; account.eclats += gained;
  return gained;
}

function remember(account, key, now) {
  account.claimed[key] = now;
  // Les clés de plus de 30 jours ne peuvent plus être rejouées (fenêtres de temps), on les oublie.
  for (const [k, t] of Object.entries(account.claimed)) if (t < now - 30 * DAY) delete account.claimed[k];
}

/** Applique un événement déclaré par l'app ; renvoie les éclats gagnés, ou lève une erreur 400/409. */
export function applyEvent(account, event, now, salt = '') {
  const bad = () => { const e = new Error('event'); e.status = 400; throw e; };
  const seen = () => { const e = new Error('seen'); e.status = 409; throw e; };
  if (!event || typeof event !== 'object') bad();
  const week = rollWeek(account, now);
  if (event.type === 'session') {
    if (typeof event.id !== 'string' || !/^[\w-]{1,64}$/.test(event.id) || !Number.isSafeInteger(event.start)) bad();
    if (event.start > now + 300_000 || event.start < now - 4 * DAY) bad();
    const key = 's:' + event.id; if (account.claimed[key]) seen();
    const target = clamp(Number.isSafeInteger(event.target) ? event.target : 3, 2, 6);
    remember(account, key, now);
    week.target = target;
    const d = epochDay(event.start); if (!week.active.includes(d)) { week.active.push(d); week.days++; }
    const n = week.sessions++;
    const full = Math.round(REWARDS.sessionPool / target);
    const amount = n < target ? full : n < target + REWARDS.extraSessions ? Math.round(full / 2) : 0;
    return credit(account, amount, now);
  }
  if (event.type === 'quest') {
    const m = typeof event.id === 'string' && /^(d|w):(\d{4,6}):([a-z]{2,10})$/.exec(event.id);
    if (!m) bad();
    const [, scope, dayText] = m; const day = Number(dayText);
    // Une quête du jour vaut pour aujourd'hui (±1 jour de fuseau), une quête de la semaine pour la semaine en cours.
    if (scope === 'd' && Math.abs(day - epochDay(now)) > 1) bad();
    if (scope === 'w' && (day !== mondayOf(day) || Math.abs(day - monday(now)) > 7)) bad();
    const quest = questsFor(account, `${scope}:${day}`, salt).find(q => q.id === event.id);
    if (!quest) bad();
    const key = 'q:' + event.id; if (account.claimed[key]) seen();
    remember(account, key, now);
    if (scope === 'w') week.wdone++; else week.ddone++;
    return credit(account, quest.reward, now);
  }
  // Départ de la mise à jour : les badges déjà obtenus avant comptent comme payés, sans éclats (une seule fois par compte).
  if (event.type === 'baseline') {
    if (account.baseline) seen();
    if (!Array.isArray(event.badges) || event.badges.length > 400) bad();
    account.badges ??= [];
    for (const id of event.badges) if (badgeReward(id) != null && !account.badges.includes(id)) account.badges.push(id);
    account.baseline = true;
    return 0;
  }
  if (event.type === 'badge') {
    const reward = badgeReward(event.id); if (reward == null) bad();
    account.badges ??= [];
    if (account.badges.includes(event.id)) seen();
    account.badges.push(event.id);
    return credit(account, reward, now);
  }
  if (event.type === 'streak') {
    if (!BADGES.tracks.includes(event.track)) bad();
    const week = monday(now);
    account.streakBonus ??= {};
    const prev = account.streakBonus[event.track];
    if (prev?.week === week) seen();
    // L'appli dit depuis combien de semaines la série tient ; sans réclamer chaque semaine on ne perd rien, mais on ne
    // peut pas dépasser ce que les semaines écoulées depuis le dernier bonus permettent.
    const asked = Number.isInteger(event.weeks) ? event.weeks : 1;
    const n = Math.max(1, Math.min(asked, BADGES.streak.weeks, prev ? prev.n + (week - prev.week) / 7 : BADGES.streak.weeks));
    account.streakBonus[event.track] = { week, n };
    return credit(account, streakBonus(n), now);
  }
  if (event.type === 'league') {
    // Quête de la semaine : « lq:{jour}:{domaine} », suffixe « :s » pour la secondaire ; une de chaque par semaine.
    if (typeof event.id !== 'string' || !/^[\w:-]{1,80}$/.test(event.id)) bad();
    const key = 'l:' + event.id; if (account.claimed[key]) seen();
    remember(account, key, now);
    const secondary = event.id.endsWith(':s');
    week.leagueDone ??= [];
    const slot = secondary ? 's' : 'p';
    if (week.leagueDone.includes(slot)) return 0;
    week.leagueDone.push(slot);
    return credit(account, secondary ? REWARDS.league.secondary : REWARDS.league.primary, now);
  }
  bad();
}

export function openPack(account, booster) {
  if (!Number.isSafeInteger(booster) || booster < 0 || booster >= ELEMENTS.length) fail(400, 'booster');
  if (account.bonusPacks > 0) account.bonusPacks--;
  else if (account.eclats >= PACK_PRICE) account.eclats -= PACK_PRICE;
  else fail(402, 'funds');
  const result = draw(booster, isGold(account.opened));
  account.opened++;
  // Plus de remboursement au niveau max : chaque exemplaire est gardé, pour la fusion, l'échange ou la revente.
  const gains = result.pulls.map(key => {
    const id = key.split(':')[0];
    const before = Object.entries(account.owned).reduce((s, [k, v]) => k.startsWith(id + ':') ? s + v : s, 0);
    give(account, key);
    return { key, copiesBefore: before, copiesAfter: before + 1, refund: 0 };
  });
  return { pulls: gains, prism: result.prism, gold: result.gold };
}

/** Vue envoyée à l'app : portefeuille, collection et quêtes du jour et de la semaine de l'utilisateur (jour local). */
export function view(a, now, today, salt = '', challenge = null) {
  rollWeek(a, now); pruneTrades(a, now);
  const day = localDay(today, now);
  // Les quêtes de la semaine sont celles du Groupe (calculées dans l'app) : le serveur ne donne que celles du jour.
  const quests = questsFor(a, `d:${day}`, salt);
  return { v: ECONOMY_VERSION, eclats: a.eclats, bonusPacks: a.bonusPacks, opened: a.opened, untilGold: RATES.goldEvery - (a.opened % RATES.goldEvery),
    owned: a.owned, merged: a.merged || {}, claimed: Object.keys(a.claimed), quests, price: PACK_PRICE, gifts: a.gifts, tradeId: salt,
    trades: tradesOf(a), challenge: challengeView(challenge), badges: a.badges || [], baseline: !!a.baseline,
    streaks: Object.fromEntries(Object.entries(a.streakBonus || {}).map(([k, v]) => [k, { week: v.week, n: v.n }])) };
}

// ---- Défis entre amis -----------------------------------------------------------------------------------------------
/**
 * Défis de la semaine entre amis réciproques (chacun a l'autre dans sa liste) : une quête commune, même objectif pour
 * tous. En course, le premier qui la finit gagne le bonus ; ensemble, chacun le reçoit quand tous l'ont finie. Deux
 * participants au départ, puis chaque personne de plus est de moins en moins probable (35 %, puis 12 %, puis 4 %…).
 * Au plus un défi par compte et par semaine ; une chance sur deux d'en recevoir un.
 */
export const CHALLENGES = { odds: 0.5, extra: 0.35, maxMembers: 5, race: 40, together: 30,
  kinds: [{ kind: 'target', goal: 3 }, { kind: 'family', goal: 2 }, { kind: 'volume', goal: 30 }] };

/** Forme le défi d'une semaine à partir des candidats (amis réciproques libres), de façon déterministe pour [seed]. */
export function formChallenge(id, week, candidates, seed, friendsOf) {
  const h = seed >>> 0;
  if ((h % 1000) / 1000 >= CHALLENGES.odds || !candidates.length) return null;
  const members = [id, candidates[0]];
  for (let k = 1, x = h; k < candidates.length && members.length < CHALLENGES.maxMembers; k++) {
    x = Math.imul(x ^ (x >>> 15), 2246822519) >>> 0;
    if ((x % 1000) / 1000 >= CHALLENGES.extra ** k) break;
    const c = candidates[k];
    if (members.every(m => friendsOf(c).includes(m) && friendsOf(m).includes(c))) members.push(c);
  }
  const type = (h >>> 10) % 2 ? 'race' : 'together';
  const quest = CHALLENGES.kinds[(h >>> 12) % CHALLENGES.kinds.length];
  const target = Math.min(...members.map(m => friendsOf(m, true) || 3));
  return { id: randomUUID(), week, type, kind: quest.kind, goal: quest.kind === 'target' ? clamp(target, 2, 4) : quest.goal,
    ...(quest.kind === 'family' ? { stat: STATS[(h >>> 14) % STATS.length] } : {}),
    reward: type === 'race' ? CHALLENGES.race : CHALLENGES.together, members, done: {}, winner: null, paid: false };
}

const challengeView = c => c && { id: c.id, type: c.type, kind: c.kind, goal: c.goal, stat: c.stat, reward: c.reward, week: c.week,
  members: c.members, done: Object.keys(c.done), winner: c.winner };

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
  // Défis entre amis : un petit fichier commun, gardé en mémoire, réécrit à chaque changement.
  const challengeFile = join(dir, 'challenges.json');
  const challenges = new Map(Object.entries(await readFile(challengeFile, 'utf8').then(JSON.parse).catch(() => ({}))));
  const saveChallenges = async () => {
    const tmp = challengeFile + '.' + randomUUID() + '.tmp';
    try { await writeFile(tmp, JSON.stringify(Object.fromEntries(challenges)), { mode: 0o600, flag: 'wx' }); await rename(tmp, challengeFile); }
    finally { await unlink(tmp).catch(() => {}); }
  };
  const challengeOf = (id, week) => [...challenges.values()].find(c => c.week === week && c.members.includes(id)) || null;
  /** Une fois par semaine et par compte : peut-être un défi avec des amis réciproques encore libres. */
  const rollChallenge = async (acc, id, t) => {
    const week = monday(t);
    for (const [k, c] of challenges) if (c.week < week - 14) challenges.delete(k);
    if (acc.challengeWeek === week || challengeOf(id, week)) return;
    const busy = new Set([...challenges.values()].filter(c => c.week === week).flatMap(c => c.members));
    const seed = hash(id, 'c' + week);
    const accounts = new Map([[id, acc]]);
    const order = (acc.friends || []).filter(f => !busy.has(f)).sort((x, y) => hash(id + week, x) - hash(id + week, y));
    const candidates = [];
    for (const f of order) {
      if (candidates.length >= 6) break;
      const other = await load(f);
      if (other?.friends?.includes(id)) { accounts.set(f, other); candidates.push(f); }
    }
    // Sans ami réciproque libre, on réessaie plus tard ; sinon le tirage (fixe pour la semaine) est fait une fois.
    if (!candidates.length) return;
    acc.challengeWeek = week;
    const c = formChallenge(id, week, candidates, seed, (m, target) => target ? accounts.get(m)?.week?.target : accounts.get(m)?.friends || []);
    if (c) { challenges.set(c.id, c); await saveChallenges(); }
  };
  /** Défi fini par [id] : course, le premier gagne ; ensemble, tout le monde est payé quand le dernier finit. */
  const finishChallenge = async (acc, id, cid, t) => {
    const c = challenges.get(cid);
    if (!c || !c.members.includes(id) || c.week !== monday(t)) fail(404);
    if (c.done[id]) fail(409);
    c.done[id] = t;
    let gained = 0;
    if (c.type === 'race' && !c.winner) { c.winner = id; gained = credit(acc, c.reward, t); }
    if (c.type === 'together' && !c.paid && c.members.every(m => c.done[m])) {
      c.paid = true; gained = credit(acc, c.reward, t);
      for (const m of c.members.filter(m => m !== id)) { const other = await load(m); if (other) { credit(other, c.reward, t); await save(m, other); } }
    }
    await saveChallenges();
    return { challengeGained: gained };
  };

  /** Opérations d'échange : elles lisent et écrivent aussi le compte de l'ami, dans la même file que le reste. */
  const trade = async (acc, id, body, t) => {
    const mine = tradesOf(acc);
    if (body.op === 'trade-browse') {
      if (typeof body.to !== 'string' || !HEX.test(body.to) || body.to === id) fail(400);
      const other = await load(body.to); if (!other) fail(404);
      return { browse: { owned: other.owned } };
    }
    if (body.op === 'trade-offer') {
      const { to, give: offered, want } = body;
      if (typeof to !== 'string' || !HEX.test(to) || to === id) fail(400);
      if (!validTrade(offered, want)) fail(400);
      if (!has(acc, offered)) fail(402);
      if (mine.out.length >= TRADES.maxOut) fail(429);
      const other = await load(to); if (!other) fail(404);
      pruneTrades(other, t);
      if (tradesOf(other).in.length >= TRADES.maxIn) fail(429);
      const offer = { id: randomUUID(), give: offered.card ? { card: offered.card } : { eclats: offered.eclats }, want: want.card ? { card: want.card } : { eclats: want.eclats }, at: t };
      hold(acc, offer.give);
      mine.out.push({ ...offer, to }); other.trades.in.push({ ...offer, from: id });
      await save(to, other);
      return { trade: offer.id };
    }
    const tid = body.id; if (typeof tid !== 'string' || tid.length > 64) fail(400);
    if (body.op === 'trade-cancel') {
      const o = mine.out.find(x => x.id === tid); if (!o) fail(404);
      mine.out = mine.out.filter(x => x.id !== tid); pay(acc, o.give);
      const other = await load(o.to); if (other) { tradesOf(other).in = other.trades.in.filter(x => x.id !== tid); await save(o.to, other); }
      return {};
    }
    const o = mine.in.find(x => x.id === tid); if (!o) fail(404);
    mine.in = mine.in.filter(x => x.id !== tid);
    const other = await load(o.from);
    const pending = other && tradesOf(other).out.find(x => x.id === tid);
    if (!pending) return { traded: false }; // annulée ou expirée entre-temps
    if (body.op === 'trade-decline') { other.trades.out = other.trades.out.filter(x => x.id !== tid); pay(other, o.give); await save(o.from, other); return {}; }
    if (body.op !== 'trade-accept') fail(400);
    if (!has(acc, o.want)) { mine.in.push(o); fail(402); }
    hold(acc, o.want); pay(acc, o.give); pay(other, o.want);
    other.trades.out = other.trades.out.filter(x => x.id !== tid);
    await save(o.from, other);
    return { traded: true, received: o.give };
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
    try { const raw = await readBody(req); if (Buffer.byteLength(raw) > 16384) return send(res, 413); body = JSON.parse(raw || '{}'); } catch { return send(res, 400); }
    return serialized(async () => {
      let acc = await load(id);
      if (acc && !equal(acc.writeHash, digest(token))) return send(res, 403);
      if (!acc) {
        if (count >= maxAccounts) return send(res, 507);
        const ip = digest(req.socket.remoteAddress || '?'); const hour = Math.floor(now() / 3600000);
        const c = creations.get(ip); if (c && c.hour === hour && c.n >= maxNewPerHour) return send(res, 429);
        if (!creations.has(ip) && creations.size >= 1024) creations.clear();
        creations.set(ip, { hour, n: c?.hour === hour ? c.n + 1 : 1 });
        acc = { writeHash: digest(token), eclats: 0, bonusPacks: 0, opened: 0, owned: {}, claimed: {}, gifts: [], createdAt: now() };
        count++;
      }
      // Le cadeau de lancement arrive au premier contact après la mise à jour, une seule fois par compte.
      // Le rattrapage de la 3.6.2 se décide au premier contact après la mise à jour : seuls les comptes qui avaient déjà
      // ouvert des packs le reçoivent ; un nouveau compte, ou qui n'avait encore rien ouvert, n'y a plus droit ensuite.
      if (!acc.gifts.includes(REGIFT.id)) { acc.gifts.push(REGIFT.id); if (acc.gifts.includes(LAUNCH_GIFT.id) && acc.opened > 0) acc.bonusPacks += REGIFT.packs; }
      if (!acc.gifts.includes(LAUNCH_GIFT.id)) { acc.gifts.push(LAUNCH_GIFT.id); acc.bonusPacks += LAUNCH_GIFT.packs; }
      const t = now(); const salt = id;
      // Amis (leurs identifiants d'échange) : seuls les amis réciproques peuvent partager un défi.
      if (Array.isArray(body.friends)) acc.friends = [...new Set(body.friends.filter(f => typeof f === 'string' && HEX.test(f) && f !== id))].slice(0, 200);
      let extra = {};
      try {
        if (body.op === 'state' || body.op === 'events') await rollChallenge(acc, id, t);
        if (body.op === 'state') {}
        else if (body.op === 'challenge-done') extra = await finishChallenge(acc, id, body.id, t);
        else if (body.op === 'events') {
          if (!Array.isArray(body.events) || body.events.length > 40) return send(res, 400);
          const results = body.events.map(ev => { try { return { gained: applyEvent(acc, ev, t, salt) }; } catch (e) { return { error: e.status || 400 }; } });
          extra = { results };
        } else if (body.op === 'open') extra = openPack(acc, body.booster);
        else if (body.op === 'fuse') extra = fuse(acc, body.card);
        else if (body.op?.startsWith?.('trade-')) extra = await trade(acc, id, body, t);
        else return send(res, 400);
      } catch (e) { await save(id, acc); return send(res, e.status || 400, view(acc, t, body.today, salt, challengeOf(id, monday(t)))); }
      const out = { ...view(acc, t, body.today, salt, challengeOf(id, monday(t))), ...extra };
      await save(id, acc);
      send(res, 200, out);
    });
  };
}
