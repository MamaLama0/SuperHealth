import { mkdir, readdir, readFile, writeFile, rename, unlink, rm, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { createHash, randomUUID } from 'node:crypto';

export const SOCIAL_RETENTION_DAYS = 5 * 366;
const hash = value => createHash('sha256').update(value).digest('hex');
const ID = /^[a-f0-9]{32}$/;
const MAX_ENTRY = 24 * 1024;

/** Independent opaque social events. Never archive the short-lived health card. */
export async function createSocialStore(dataDir, { now = Date.now, retentionDays = SOCIAL_RETENTION_DAYS,
  maxEntries = 200000, maxPerMailbox = 50000, quota = 2_000_000_000 } = {}) {
  const dir = join(dataDir, 'social');
  await mkdir(dir, { recursive: true, mode: 0o700 });
  const boxes = new Map(); let count = 0; let bytes = 0;
  let writes = Promise.resolve();
  const path = (box, id) => join(dir, box, id + '.json');
  for (const box of await readdir(dir)) {
    if (!/^[a-f0-9]{64}$/.test(box)) continue;
    const entries = new Map();
    for (const name of await readdir(join(dir, box))) {
      if (name.endsWith('.tmp')) { await unlink(join(dir, box, name)); continue; }
      if (!/^[a-f0-9]{32}\.json$/.test(name)) continue;
      const file = join(dir, box, name); const size = (await stat(file)).size;
      if (size > MAX_ENTRY + 2048) throw new Error('invalid social record');
      const record = JSON.parse(await readFile(file, 'utf8'));
      if (!Number.isSafeInteger(record.at) || !Number.isSafeInteger(record.revision) ||
          typeof record.raw !== 'string' || Buffer.byteLength(record.raw) > MAX_ENTRY) throw new Error('invalid social record');
      entries.set(name.slice(0, -5), { at: record.at, revision: record.revision, size }); count++; bytes += size;
    }
    boxes.set(box, entries);
  }
  const prune = async () => {
    for (const [box, entries] of boxes) for (const [id, entry] of entries) {
      if (entry.at > now() - retentionDays * 86400000) continue;
      await unlink(path(box, id)).catch(e => { if (e.code !== 'ENOENT') throw e; });
      entries.delete(id); count--; bytes -= entry.size;
    }
  };
  await prune();
  const serialized = operation => { const result = writes.then(operation); writes = result.catch(() => {}); return result; };
  const send = (res, status, body) => {
    res.writeHead(status, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store',
      'Referrer-Policy': 'no-referrer', 'X-Content-Type-Options': 'nosniff' });
    res.end(body == null ? '' : JSON.stringify(body));
  };
  const handle = async (req, res, { parent, id, before, exists, authorized, readBody, validateEnvelope }) => {
    if (!exists) { req.resume(); return send(res, 404); }
    const box = hash(parent);
    if (req.method === 'GET' && id) {
      const entry = boxes.get(box)?.get(id);
      if (!entry || entry.at <= now() - retentionDays * 86400000) return send(res, 404);
      const saved = JSON.parse(await readFile(path(box, id), 'utf8'));
      res.writeHead(200, { 'Content-Type': 'application/json', 'Cache-Control': 'no-store', 'Referrer-Policy': 'no-referrer' });
      return res.end(saved.raw);
    }
    if (req.method === 'GET' && !id) {
      const eligible = [...(boxes.get(box) || [])].filter(([key, e]) => e.at > now() - retentionDays * 86400000 &&
        (!before || `${String(e.revision).padStart(16, '0')}:${key}` < before))
        .sort(([a, x], [b, y]) => y.revision - x.revision || b.localeCompare(a));
      const page = eligible.slice(0, 20);
      const items = await Promise.all(page.map(async ([id]) => {
        const record = JSON.parse(await readFile(path(box, id), 'utf8')); return { id, raw: record.raw };
      }));
      const last = page.at(-1);
      return send(res, 200, { items, next: eligible.length > page.length && last ? `${String(last[1].revision).padStart(16, '0')}:${last[0]}` : null });
    }
    if (req.method !== 'PUT' || !ID.test(id || '')) { req.resume(); return send(res, 405); }
    if (!(typeof authorized === 'function' ? authorized() : authorized)) { req.resume(); return send(res, 403); }
    const rawBody = await readBody(req);
    if (Buffer.byteLength(rawBody) > MAX_ENTRY) return send(res, 413);
    let envelope;
    try { envelope = validateEnvelope(rawBody, now()); } catch { return send(res, 400); }
    const raw = JSON.stringify(envelope);
    return serialized(async () => {
      if (!(typeof authorized === 'function' ? authorized() : authorized)) return send(res, 403);
      const entries = boxes.get(box) || new Map(); const previous = entries.get(id);
      if (previous && envelope.revision < previous.revision) return send(res, 409);
      if (previous && envelope.revision === previous.revision) {
        const saved = JSON.parse(await readFile(path(box, id), 'utf8'));
        return send(res, saved.raw === raw ? 204 : 409);
      }
      if (!previous && (count >= maxEntries || entries.size >= maxPerMailbox)) return send(res, 507);
      const record = JSON.stringify({ at: now(), revision: envelope.revision, raw }); const size = Buffer.byteLength(record);
      if (bytes - (previous?.size || 0) + size > quota) return send(res, 507);
      await mkdir(join(dir, box), { recursive: true, mode: 0o700 });
      const tmp = path(box, id) + '.' + randomUUID() + '.tmp';
      try { await writeFile(tmp, record, { flag: 'wx', mode: 0o600 }); await rename(tmp, path(box, id)); }
      finally { await unlink(tmp).catch(() => {}); }
      if (!previous) count++;
      bytes += size - (previous?.size || 0); entries.set(id, { at: now(), revision: envelope.revision, size }); boxes.set(box, entries);
      send(res, 204);
    });
  };
  handle.prune = () => serialized(prune);
  handle.purge = parent => serialized(async () => {
    const box = hash(parent); const entries = boxes.get(box);
    if (!entries) return;
    await rm(join(dir, box), { recursive: true, force: true });
    count -= entries.size; for (const entry of entries.values()) bytes -= entry.size; boxes.delete(box);
  });
  return handle;
}
