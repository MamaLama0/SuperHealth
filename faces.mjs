import { createHash, createPublicKey, verify, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, unlink, readdir } from 'node:fs/promises';
import { join } from 'node:path';

/**
 * Visages et demandes d'ami du Groupe, adressés par l'identifiant d'auteur (16 hex = début du SHA-256 de la clé de groupe).
 * Visage : petite vignette JPEG publique, pour qu'un commentaire d'une personne qu'on n'a pas en ami montre sa vraie photo.
 * Demandes : le code d'invitation d'un expéditeur, chiffré par l'app pour le seul destinataire ; le serveur ne voit que du
 * chiffré. Tout est signé avec la clé de groupe de l'auteur (même format que les messages : SHG1.charge.signature).
 */
export const FACE_MAX_IMAGE = 12_000;
export const REQUEST_MAX_DATA = 4_000;
// Un code d'invitation ne vit que 24 h : une demande plus vieille ne mènerait plus à rien.
export const REQUEST_TTL_MS = 86_400_000;
export const MAX_REQUESTS_PER_INBOX = 30;
const FRESH_MS = 10 * 60_000;
const MAX_BODY = 24_000;
const PREFIX = Buffer.from('SuperHealth group v1:');
const B64 = /^[A-Za-z0-9_-]+$/;

export const authorOf = key => createHash('sha256').update(Buffer.from(key, 'base64url')).digest('hex').slice(0, 16);

/** Vérifie une charge signée par [key] (clé publique EC en SPKI base64url) et renvoie l'objet JSON signé. */
export function openSigned(signed, key) {
  if (typeof signed !== 'string' || typeof key !== 'string' || signed.length > 20_000 || key.length > 512 || !B64.test(key)) throw new Error('format');
  const parts = signed.split('.');
  if (parts.length !== 3 || parts[0] !== 'SHG1' || !B64.test(parts[1]) || !B64.test(parts[2])) throw new Error('format');
  const bytes = Buffer.from(parts[1], 'base64url');
  const pub = createPublicKey({ key: Buffer.from(key, 'base64url'), format: 'der', type: 'spki' });
  if (pub.asymmetricKeyType !== 'ec' || !verify('sha256', Buffer.concat([PREFIX, bytes]), pub, Buffer.from(parts[2], 'base64url'))) throw new Error('signature');
  const payload = JSON.parse(bytes.toString('utf8'));
  if (!payload || typeof payload !== 'object' || Array.isArray(payload)) throw new Error('format');
  return payload;
}

export async function createFaces(dataDir, { now = Date.now, maxFaces = 200_000, maxInboxes = 200_000 } = {}) {
  const dir = join(dataDir, 'faces');
  await mkdir(dir, { recursive: true, mode: 0o700 });
  const faces = new Set(); const inboxes = new Set();
  for (const name of await readdir(dir)) {
    if (/^[a-f0-9]{16}\.json$/.test(name)) faces.add(name.slice(0, 16));
    else if (/^[a-f0-9]{16}\.requests\.json$/.test(name)) inboxes.add(name.slice(0, 16));
    else if (name.endsWith('.tmp')) await unlink(join(dir, name)).catch(() => {});
  }
  let writes = Promise.resolve();
  const serialized = task => { const result = writes.then(task); writes = result.catch(() => {}); return result; };
  const facePath = id => join(dir, id + '.json');
  const inboxPath = id => join(dir, id + '.requests.json');
  const load = async path => { try { return JSON.parse(await readFile(path, 'utf8')); } catch (e) { if (e.code === 'ENOENT') return null; throw e; } };
  const save = async (path, value) => {
    const tmp = path + '.' + randomUUID() + '.tmp';
    try { await writeFile(tmp, JSON.stringify(value), { mode: 0o600, flag: 'wx' }); await rename(tmp, path); }
    finally { await unlink(tmp).catch(() => {}); }
  };
  const live = list => (Array.isArray(list) ? list : []).filter(r => r.at > now() - REQUEST_TTL_MS);
  const fresh = at => Number.isSafeInteger(at) && Math.abs(at - now()) <= FRESH_MS;
  const parse = raw => {
    if (Buffer.byteLength(raw) > MAX_BODY) throw new Error('size');
    const body = JSON.parse(raw);
    if (!body || typeof body !== 'object' || Object.keys(body).sort().join(',') !== 'key,signed') throw new Error('format');
    return { key: body.key, payload: openSigned(body.signed, body.key), signed: body.signed };
  };

  return async function handle(req, res, id, requests, readBody, send) {
    const json = (status, value, headers = {}) => send(res, status, value === undefined ? '' : JSON.stringify(value), headers);
    if (req.method === 'GET') {
      if (!requests) {
        const face = await load(facePath(id));
        return face ? json(200, { key: face.key, signed: face.signed }, { 'Cache-Control': 'public, max-age=600' }) : json(404);
      }
      const items = live(await load(inboxPath(id)));
      return json(200, { items: items.map(r => ({ key: r.key, signed: r.signed })) });
    }
    if (!['PUT', 'POST', 'DELETE'].includes(req.method)) { req.resume(); return json(405); }
    let body;
    try { body = parse(await readBody(req)); } catch (e) { return json(e.status === 413 || e.message === 'size' ? 413 : 400); }
    const { key, payload, signed } = body;
    if (!requests) {
      // Seul l'auteur publie ou retire son visage ; une vignette vide le retire.
      if (req.method !== 'PUT' || authorOf(key) !== id || payload.t !== 'face' || payload.id !== id || !fresh(payload.at) ||
          typeof payload.image !== 'string' || payload.image.length > FACE_MAX_IMAGE || (payload.image && (!B64.test(payload.image) || !payload.image.startsWith('_9j_')))) return json(400);
      return serialized(async () => {
        const previous = await load(facePath(id));
        if (previous && previous.at >= payload.at) return json(409);
        if (!payload.image) { await unlink(facePath(id)).catch(() => {}); faces.delete(id); return json(204); }
        if (!previous && faces.size >= maxFaces) return json(507);
        await save(facePath(id), { key, signed, at: payload.at }); faces.add(id);
        return json(204);
      });
    }
    if (req.method === 'POST') {
      // N'importe quel membre peut déposer une demande signée ; une seule par expéditeur, la plus récente remplace l'ancienne.
      const from = authorOf(key);
      if (from === id || payload.t !== 'friend' || payload.to !== id || !fresh(payload.at) ||
          typeof payload.data !== 'string' || !payload.data || payload.data.length > REQUEST_MAX_DATA || !B64.test(payload.data)) return json(400);
      return serialized(async () => {
        const list = live(await load(inboxPath(id))).filter(r => authorOf(r.key) !== from);
        if (list.length >= MAX_REQUESTS_PER_INBOX) return json(429);
        if (!inboxes.has(id) && inboxes.size >= maxInboxes) return json(507);
        await save(inboxPath(id), [...list, { key, signed, at: payload.at }]); inboxes.add(id);
        return json(204);
      });
    }
    if (req.method !== 'DELETE' || authorOf(key) !== id || payload.t !== 'clear' || payload.id !== id || !fresh(payload.at) ||
        (payload.from !== undefined && !/^[a-f0-9]{16}$/.test(payload.from))) return json(400);
    // Le destinataire efface une demande traitée (ou toutes).
    return serialized(async () => {
      const list = live(await load(inboxPath(id))).filter(r => payload.from !== undefined && authorOf(r.key) !== payload.from);
      if (list.length) await save(inboxPath(id), list);
      else { await unlink(inboxPath(id)).catch(() => {}); inboxes.delete(id); }
      return json(204);
    });
  };
}
