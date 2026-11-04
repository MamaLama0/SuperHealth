import { createReadStream, createWriteStream } from 'node:fs';
import { mkdir, readdir, readFile, writeFile, rename, unlink, stat } from 'node:fs/promises';
import { join } from 'node:path';
import { createHash, randomUUID } from 'node:crypto';
import { pipeline } from 'node:stream/promises';

export const MAX_MEDIA = 50 * 1024 * 1024 + 28;
export const MAX_MEDIA_FILES = 100_000;
export const DEFAULT_MEDIA_QUOTA = 100_000_000_000;
export const MEDIA_TTL = 5 * 366 * 86400000;
/** Opaque, capability-addressed attachments. One upload at a time, streamed to disk. */
export async function createMediaStore(dataDir, { now = Date.now, quota = DEFAULT_MEDIA_QUOTA, maxFiles = MAX_MEDIA_FILES, maxPerMailbox = 5000, retentionDays = 5 * 366 } = {}) {
  const dir = join(dataDir, 'media'); await mkdir(dir, { recursive: true, mode: 0o700 });
  const entries = new Map(); let used = 0; let uploading = false;
  const file = id => join(dir, id + '.bin');
  const remove = async id => {
    const entry = entries.get(id);
    if (!entry) return;
    await unlink(file(id)).catch(e => { if (e.code !== 'ENOENT') throw e; });
    await unlink(join(dir, id + '.json')).catch(e => { if (e.code !== 'ENOENT') throw e; });
    entries.delete(id); used -= entry.size;
  };
  for (const name of await readdir(dir)) {
    if (name.endsWith('.tmp')) { await unlink(join(dir, name)); continue; }
    if (!/^[a-f0-9]{64}\.json$/.test(name)) continue;
    const id = name.slice(0, -5); const meta = JSON.parse(await readFile(join(dir, name), 'utf8'));
    if (!Number.isSafeInteger(meta.size) || meta.size < 29 || meta.size > MAX_MEDIA || !Number.isSafeInteger(meta.at) || !/^[a-f0-9]{64}$/.test(meta.hash)) throw new Error('invalid media');
    const size = await stat(file(id)).then(s => s.size).catch(() => -1);
    if (size !== meta.size) throw new Error('invalid media file');
    entries.set(id, meta); used += size;
  }
  // Remove orphaned ciphertexts after an interrupted atomic commit.
  for (const name of await readdir(dir)) if (/^[a-f0-9]{64}\.bin$/.test(name) && !entries.has(name.slice(0, -4))) await unlink(join(dir, name));
  const prune = async () => { for (const [id, e] of entries) if (e.at <= now() - retentionDays * 86400000) await remove(id); };
  await prune();
  const send = (res, status) => { res.writeHead(status, { 'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' }); res.end(); };
  const handle = async (req, res, { exists, authorized, parent }) => {
    const id = createHash('sha256').update(req.url).digest('hex');
    if (!exists) { req.resume(); return send(res, 404); }
    if (req.method === 'GET') {
      const entry = entries.get(id);
      if (!entry || entry.at <= now() - retentionDays * 86400000) return send(res, 404);
      res.writeHead(200, { 'Content-Type': 'application/octet-stream', 'Content-Length': entry.size,
        'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer' });
      await pipeline(createReadStream(file(id)), res); return;
    }
    if (!(typeof authorized === 'function' ? authorized() : authorized)) { req.resume(); return send(res, 403); }
    if (req.method === 'DELETE') { req.resume(); await remove(id); return send(res, 204); }
    if (req.method !== 'PUT') { req.resume(); return send(res, 405); }
    if (uploading) { req.resume(); res.setHeader('Retry-After', '2'); return send(res, 503); }
    const length = Number(req.headers['content-length']);
    if (Number.isFinite(length) && (length > MAX_MEDIA || length < 29)) { req.resume(); return send(res, 413); }
    if (entries.has(id)) {
      const existing = entries.get(id);
      const same = existing.parent === createHash('sha256').update(parent).digest('hex') &&
        req.headers['x-content-sha256'] === existing.hash && Number(req.headers['content-length']) === existing.size;
      req.resume(); return send(res, same ? 204 : 409);
    }
    uploading = true;
    const tmp = file(id) + '.' + randomUUID() + '.tmp';
    let size = 0; const hash = createHash('sha256');
    try {
      await prune();
      if (entries.size >= maxFiles || [...entries.values()].filter(e => e.parent === createHash('sha256').update(parent).digest('hex')).length >= maxPerMailbox) { req.resume(); return send(res, 507); }
      await new Promise((resolveUpload, reject) => {
        const out = createWriteStream(tmp, { flags: 'wx', mode: 0o600 });
        let failed = false;
        const cleanup = () => { req.off('data', data); req.off('end', end); req.off('error', fail); req.off('aborted', abort); out.off('drain', drain); };
        const fail = error => {
          if (failed) return; failed = true; cleanup();
          req.once('error', () => {}); req.resume();
          out.once('close', () => reject(error)); out.destroy();
        };
        const abort = () => { const error = new Error('aborted'); error.status = 400; fail(error); };
        const drain = () => req.resume();
        const data = chunk => {
          size += chunk.length;
          if (size > MAX_MEDIA || used + size > quota) {
            const error = new Error('quota'); error.status = size > MAX_MEDIA ? 413 : 507; fail(error); return;
          }
          hash.update(chunk); if (!out.write(chunk)) req.pause();
        };
        const end = () => out.end();
        out.once('error', fail); out.on('drain', drain);
        out.once('finish', () => { cleanup(); resolveUpload(); });
        req.on('data', data); req.once('end', end); req.once('error', fail); req.once('aborted', abort);
      });
      if (size < 29) return send(res, 400);
      const meta = { size, at: now(), hash: hash.digest('hex'), parent: createHash('sha256').update(parent).digest('hex') };
      await rename(tmp, file(id));
      await writeFile(tmp + '.meta.tmp', JSON.stringify(meta), { flag: 'wx', mode: 0o600 });
      await rename(tmp + '.meta.tmp', join(dir, id + '.json'));
      if (!(typeof authorized === 'function' ? authorized() : authorized)) {
        await unlink(file(id)).catch(() => {}); await unlink(join(dir, id + '.json')).catch(() => {});
        return send(res, 403);
      }
      entries.set(id, meta); used += size;
      return send(res, 204);
    } catch (e) { if (!res.headersSent && !res.destroyed) send(res, e.status || 500); }
    finally { uploading = false; await unlink(tmp).catch(() => {}); await unlink(tmp + '.meta.tmp').catch(() => {}); }
  };
  handle.prune = prune;
  handle.purge = async parent => {
    const box = createHash('sha256').update(parent).digest('hex');
    for (const [id, entry] of entries) if (entry.parent === box) await remove(id);
  };
  return handle;
}
