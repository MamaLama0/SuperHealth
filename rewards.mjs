import { readFile, stat } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Récompenses de l'économie des cartes, réglables sans mise à jour de l'app ni de l'image : montants des quêtes, paquets
 * des quêtes de la semaine et des défis, prix d'un pack et cadeaux de paquets. Trois sources, la première trouvée gagne :
 *   1. `rewards.json` dans le dossier de données du serveur (réglage local, relu dès qu'il change) ;
 *   2. `REWARDS_URL` (par défaut le fichier `rewards.json` de la branche server/homeserver), relu toutes les 10 minutes ;
 *   3. les valeurs par défaut ci-dessous.
 * Toute valeur est bornée : un fichier mal écrit ne peut ni casser le serveur ni distribuer des milliers de packs.
 *
 * Un cadeau (`gifts`) est versé une seule fois par compte, au premier contact après sa mise en ligne : `packs` et/ou
 * `eclats`, réservé si besoin à certains comptes (`accounts` : identifiants d'échange, visibles dans l'app) et à une
 * période (`from`, `until` : dates ISO). `note` s'affiche dans l'app avec le cadeau.
 */
export const DEFAULT_REWARDS = {
  packPrice: 100,
  sessions: { pool: 150, extra: 2 },
  // Quêtes du jour : éclats de la quête « séance », des autres, et un peu plus par palier de difficulté.
  daily: { session: 25, other: 20, tierStep: 5 },
  // Quêtes de la semaine : trois, chacune rapporte des paquets gratuits (plus d'éclats).
  weekly: { count: 3, packs: 1 },
  // Défis entre amis : course (le premier gagne) et ensemble (chacun quand tous ont fini).
  challenge: { race: { packs: 2, eclats: 0 }, together: { packs: 1, eclats: 0 } },
  gifts: [],
};
export const DEFAULT_REWARDS_URL = 'https://raw.githubusercontent.com/Ouinon0/SuperHealth/server/homeserver/rewards.json';

const int = (v, lo, hi, fallback) => Number.isSafeInteger(v) ? Math.min(hi, Math.max(lo, v)) : fallback;
const date = v => typeof v === 'string' && !Number.isNaN(Date.parse(v)) ? Date.parse(v) : null;
const prize = (p, d) => ({ packs: int(p?.packs, 0, 10, d.packs), eclats: int(p?.eclats, 0, 1000, d.eclats) });

/** Réglage validé et borné ; les champs absents ou invalides gardent la valeur par défaut. */
export function normalize(raw) {
  const r = raw && typeof raw === 'object' && !Array.isArray(raw) ? raw : {};
  const d = DEFAULT_REWARDS;
  const gifts = (Array.isArray(r.gifts) ? r.gifts : []).filter(g => g && typeof g.id === 'string' && /^[\w.-]{1,40}$/.test(g.id)).slice(0, 50).map(g => ({
    id: g.id, packs: int(g.packs, 0, 20, 0), eclats: int(g.eclats, 0, 5000, 0),
    note: typeof g.note === 'string' ? g.note.slice(0, 140) : '',
    accounts: Array.isArray(g.accounts) ? g.accounts.filter(a => typeof a === 'string' && /^[a-f0-9]{64}$/.test(a)).slice(0, 500) : null,
    from: date(g.from), until: date(g.until),
  })).filter(g => g.packs > 0 || g.eclats > 0);
  return {
    packPrice: int(r.packPrice, 20, 1000, d.packPrice),
    sessions: { pool: int(r.sessions?.pool, 0, 1000, d.sessions.pool), extra: int(r.sessions?.extra, 0, 7, d.sessions.extra) },
    daily: { session: int(r.daily?.session, 0, 200, d.daily.session), other: int(r.daily?.other, 0, 200, d.daily.other), tierStep: int(r.daily?.tierStep, 0, 50, d.daily.tierStep) },
    weekly: { count: int(r.weekly?.count, 1, 5, d.weekly.count), packs: int(r.weekly?.packs, 0, 5, d.weekly.packs) },
    challenge: { race: prize(r.challenge?.race, d.challenge.race), together: prize(r.challenge?.together, d.challenge.together) },
    gifts,
  };
}

/** Le cadeau [g] revient-il au compte [id] maintenant ? */
export const giftFor = (g, id, t) => (!g.accounts || g.accounts.includes(id)) && (g.from == null || t >= g.from) && (g.until == null || t <= g.until);

/** Source de réglage : `current()` renvoie toujours un réglage valable, sans jamais attendre le réseau. */
export async function createRewards(dataDir, { url = DEFAULT_REWARDS_URL, now = Date.now, fetchImpl = globalThis.fetch, refreshMs = 600_000 } = {}) {
  const local = join(dataDir, 'rewards.json');
  let localValue = null; let localStamp = null; let remoteValue = null; let remoteAt = 0; let fetching = null; let checkedAt = 0;
  const readLocal = async () => {
    // Relu au plus toutes les 5 s, et seulement quand le fichier a changé.
    if (now() - checkedAt < 5000 && localStamp !== null) return;
    checkedAt = now();
    try {
      const s = await stat(local); const stamp = s.mtimeMs + ':' + s.size;
      if (stamp !== localStamp) { localStamp = stamp; localValue = normalize(JSON.parse(await readFile(local, 'utf8'))); }
    } catch { localStamp = ''; localValue = null; }
  };
  const refreshRemote = () => {
    if (!url || !fetchImpl || fetching || now() - remoteAt < refreshMs) return;
    remoteAt = now();
    fetching = (async () => {
      try {
        const r = await fetchImpl(url, { signal: AbortSignal.timeout(8000), redirect: 'error' });
        if (!r.ok) return;
        const text = await r.text(); if (text.length > 64_000) return;
        remoteValue = normalize(JSON.parse(text));
      } catch { /* on garde la dernière version lue */ } finally { fetching = null; }
    })();
  };
  await readLocal(); refreshRemote(); await fetching;
  return {
    async current() { await readLocal(); refreshRemote(); return localValue || remoteValue || normalize(null); },
  };
}
