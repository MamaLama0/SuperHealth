import { createMediaStore, DEFAULT_MEDIA_QUOTA, MAX_MEDIA_FILES } from './media.mjs';
import { createFeedbackHandler } from './feedback.mjs';
import { createServer } from 'node:http';
import { createHash, timingSafeEqual, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, unlink, readdir, stat } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const MAX_BODY = 98_304;
export const TTL_MS = 35 * 86_400_000;
const ROUTE = /^\/v2\/(?:cards\/[a-f0-9]{64}\/[a-f0-9]{64}(?:\/media\/[a-f0-9]{64})?|invites\/[a-f0-9]{64}(?:\/responses(?:\/[a-f0-9]{64})?)?)$/;
export const INVITE_TTL_MS = 24 * 3_600_000;
const ttl = record => record.kind === 'invite' ? INVITE_TTL_MS : TTL_MS;
const HEX_SECRET = /^[a-f0-9]{64}$/;
const digest = value => createHash('sha256').update(value).digest('hex');
const equal = (a, b) => a.length === b.length && timingSafeEqual(Buffer.from(a), Buffer.from(b));
const integer = (value, min, max, name) => {
  if (!Number.isSafeInteger(value) || value < min || value > max) throw new Error(name);
  return value;
};

/** The server validates transport bounds, never a person's identity or decrypted health data. */
export function validateEnvelope(raw, now = Date.now()) {
  if (Buffer.byteLength(raw) > MAX_BODY) throw new Error('size');
  const envelope = JSON.parse(raw);
  if (!envelope || Array.isArray(envelope) || typeof envelope !== 'object' ||
      Object.keys(envelope).sort().join(',') !== 'ciphertext,nonce,revision,v' || envelope.v !== 2) throw new Error('format');
  integer(envelope.revision, Math.max(1, now - 7 * 86_400_000), now + 300_000, 'revision');
  const decode = (text, min, max) => {
    if (typeof text !== 'string' || !/^[A-Za-z0-9_-]+$/.test(text)) throw new Error('base64');
    const bytes = Buffer.from(text, 'base64url');
    if (bytes.length < min || bytes.length > max || bytes.toString('base64url') !== text) throw new Error('base64');
    return text;
  };
  return { v: 2, revision: envelope.revision,
    nonce: decode(envelope.nonce, 12, 12), ciphertext: decode(envelope.ciphertext, 17, 73_000) };
}

export async function createRelay({ dataDir, publishKey, maxCards = 500, rateLimitPerMinute = 240,
  maxInFlight = 8, maxMediaFiles = MAX_MEDIA_FILES, mediaQuotaBytes = DEFAULT_MEDIA_QUOTA,
  now = Date.now, sweepIntervalMs = 3_600_000, feedback: feedbackOptions } = {}) {
  if (publishKey != null && publishKey !== '' && (typeof publishKey !== 'string' || publishKey.length < 32 || publishKey.length > 512 || /[\r\n]/.test(publishKey)))
    throw new Error('PUBLISH_KEY must have 32..512 characters');
  integer(maxCards, 1, 10_000, 'MAX_CARDS');
  integer(rateLimitPerMinute, 1, 10_000, 'RATE_LIMIT_PER_MINUTE');
  integer(maxInFlight, 1, 64, 'MAX_IN_FLIGHT');
  integer(maxMediaFiles, 1, 10_000, 'MAX_MEDIA_FILES');
  integer(mediaQuotaBytes, 29, Number.MAX_SAFE_INTEGER, 'MEDIA_QUOTA_BYTES');
  await mkdir(dataDir, { recursive: true, mode: 0o700 });
  const media = await createMediaStore(dataDir, { now, maxFiles: maxMediaFiles, quota: mediaQuotaBytes });
  const feedback = createFeedbackHandler({ ...feedbackOptions, now });
  const secret = publishKey ? `Bearer ${publishKey}` : null;
  const index = new Map();
  const rates = new Map();
  let writes = Promise.resolve();
  let inFlight = 0;
  const fileFor = id => join(dataDir, id + '.json');

  // Metadata-only index: a conditional GET needs no disk read, no decrypt and no history query.
  for (const name of await readdir(dataDir)) {
    if (/^[a-f0-9]{64}\.json\.[\w-]+\.tmp$/.test(name)) { await unlink(join(dataDir, name)); continue; }
    if (!/^[a-f0-9]{64}\.json$/.test(name)) continue;
    const path = join(dataDir, name);
    if ((await stat(path)).size > MAX_BODY * 4 + 2048) throw new Error('invalid stored record');
    const record = JSON.parse(await readFile(path, 'utf8'));
    if (record.format !== 2 || !HEX_SECRET.test(record.writeHash) || !Number.isSafeInteger(record.updatedAt) ||
        typeof record.raw !== 'string' || Buffer.byteLength(record.raw) > MAX_BODY ||
        record.etag !== `"${digest(record.raw)}"`) throw new Error('invalid stored record');
    if (record.responses != null && (record.kind !== 'invite' || typeof record.responses !== 'object' || Array.isArray(record.responses) || Object.keys(record.responses).length > 16 || Object.entries(record.responses).some(([key, value]) => !HEX_SECRET.test(key) || typeof value !== 'string' || Buffer.byteLength(value) > 16384))) throw new Error('invalid responses');
    const envelope = validateEnvelope(record.raw, record.updatedAt);
    if (record.revision !== envelope.revision) throw new Error('invalid stored revision');
    if (record.kind != null && record.kind !== 'invite') throw new Error('invalid stored kind');
    if (record.updatedAt <= now() - ttl(record)) { await unlink(path); continue; }
    if (index.size >= maxCards) throw new Error('stored mailboxes exceed MAX_CARDS');
    index.set(name.slice(0, -5), { writeHash: record.writeHash, updatedAt: record.updatedAt,
      revision: record.revision, etag: record.etag, kind: record.kind });
  }

  const prune = async () => {
    for (const [id, record] of index) if (record.updatedAt <= now() - ttl(record)) {
      await unlink(fileFor(id)).catch(e => { if (e.code !== 'ENOENT') throw e; });
      index.delete(id);
    }
  };
  const serialized = task => {
    const result = writes.then(task);
    writes = result.catch(() => {});
    return result;
  };
  const send = (res, status, body = '', headers = {}) => {
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store',
      'X-Content-Type-Options': 'nosniff', 'Referrer-Policy': 'no-referrer', ...headers });
    res.end(body);
  };
  const readBody = req => new Promise((resolveBody, reject) => {
    let size = 0;
    const chunks = [];
    const cleanup = () => { req.off('data', data); req.off('end', end); req.off('error', fail); req.off('aborted', abort); };
    const fail = error => { cleanup(); reject(error); };
    const abort = () => { const error = new Error('aborted'); error.status = 400; fail(error); };
    const data = chunk => {
      size += chunk.length;
      if (size > MAX_BODY) {
        const error = new Error('size'); error.status = 413;
        fail(error); req.resume();
      } else chunks.push(chunk);
    };
    const end = () => { cleanup(); resolveBody(Buffer.concat(chunks).toString('utf8')); };
    req.on('data', data); req.once('end', end); req.once('error', fail); req.once('aborted', abort);
  });
  const server = createServer({ maxHeaderSize: 8192 }, async (req, res) => {
    // Never log requests, credentials, plaintext contents, IP addresses or capability URLs.
    if (req.url === '/health' && req.method === 'GET') return send(res, 200, JSON.stringify({ status: 'ok', protocol: 2, media: 1, feedback: feedback.enabled ? 1 : 0 }));
    if (req.url === '/v2/feedback') return feedback(req, res);
    if (!ROUTE.test(req.url || '')) return send(res, 404);
    if (!['GET', 'PUT', 'POST', 'DELETE'].includes(req.method)) return send(res, 405, '', { Allow: 'GET, PUT, DELETE' });
    if (inFlight >= maxInFlight) { req.resume(); return send(res, 503, '', { 'Retry-After': '2' }); }
    // Bound all connections, including unauthenticated attempts; behind a proxy this is a shared limit.
    const invitation = req.url.startsWith('/v2/invites/');
    const bucket = digest((req.socket.remoteAddress || '?') + (invitation ? ':invites' : ':cards'));
    const minute = Math.floor(now() / 60_000);
    const previous = rates.get(bucket);
    const requests = previous?.minute === minute ? previous.requests + 1 : 1;
    if (!rates.has(bucket) && rates.size >= 1024) rates.clear();
    rates.set(bucket, { minute, requests });
    if (requests > (invitation ? Math.min(12, rateLimitPerMinute) : rateLimitPerMinute)) { req.resume(); return send(res, 429, '', { 'Retry-After': '60' }); }
    inFlight++;
    try {
      const mediaRoute = req.url.match(/^(\/v2\/cards\/[a-f0-9]{64}\/[a-f0-9]{64})\/media\/[a-f0-9]{64}$/);
      if (mediaRoute) {
        const parent = index.get(digest(mediaRoute[1]));
        const exists = parent && parent.updatedAt > now() - TTL_MS;
        const token = req.headers['x-write-token'];
        const authorized = exists && (!secret || equal(req.headers.authorization || '', secret)) &&
          typeof token === 'string' && HEX_SECRET.test(token) && equal(parent.writeHash, digest(token));
        return await media(req, res, { exists, authorized });
      }
      const responseRoute = req.url.match(/^(\/v2\/invites\/[a-f0-9]{64})\/responses(?:\/([a-f0-9]{64}))?$/);
      const id = digest(responseRoute ? responseRoute[1] : req.url);
      const record = index.get(id);
      const expired = !record || record.updatedAt <= now() - ttl(record);
      if (responseRoute) {
        if (expired || record.kind !== 'invite') { req.resume(); return send(res, 404); }
        const responseId = responseRoute[2];
        const authorized = typeof req.headers['x-write-token'] === 'string' && HEX_SECRET.test(req.headers['x-write-token']) && equal(record.writeHash, digest(req.headers['x-write-token']));
        if (req.method === 'GET' && !responseId) {
          if (!authorized) return send(res, 401);
          const saved = JSON.parse(await readFile(fileFor(id), 'utf8'));
          return send(res, 200, JSON.stringify(saved.responses || {}));
        }
        if (req.method === 'POST' && responseId) {
          if (Number(req.headers['content-length'] || 0) > 16384) { req.resume(); return send(res, 413); }
          const raw = await readBody(req);
          if (Buffer.byteLength(raw) > 16384) return send(res, 413);
          let envelope;
          try { envelope = validateEnvelope(raw, now()); } catch { return send(res, 400); }
          return await serialized(async () => {
            const saved = JSON.parse(await readFile(fileFor(id), 'utf8'));
            const responses = saved.responses || {};
            if (!responses[responseId] && Object.keys(responses).length >= 16) return send(res, 429);
            // A retry keeps the first response; another request cannot replace it.
            if (responses[responseId]) return send(res, 204);
            responses[responseId] = JSON.stringify(envelope);
            const tmp = fileFor(id) + '.' + randomUUID() + '.tmp';
            try { await writeFile(tmp, JSON.stringify({ ...saved, responses }), { mode: 0o600, flag: 'wx' }); await rename(tmp, fileFor(id)); }
            finally { await unlink(tmp).catch(() => {}); }
            send(res, 204);
          });
        }
        if (req.method === 'DELETE' && responseId) {
          if (!authorized) { req.resume(); return send(res, 401); }
          req.resume();
          return await serialized(async () => {
            const saved = JSON.parse(await readFile(fileFor(id), 'utf8'));
            const responses = saved.responses || {}; delete responses[responseId];
            const tmp = fileFor(id) + '.' + randomUUID() + '.tmp';
            try { await writeFile(tmp, JSON.stringify({ ...saved, responses }), { mode: 0o600, flag: 'wx' }); await rename(tmp, fileFor(id)); }
            finally { await unlink(tmp).catch(() => {}); }
            send(res, 204);
          });
        }
        req.resume(); return send(res, 405);
      }
      if (req.method === 'POST') { req.resume(); return send(res, 405); }
      if (req.method === 'GET') {
        if (expired) return send(res, 404);
        if (req.headers['if-none-match'] === record.etag) return send(res, 304, '', { ETag: record.etag });
        try {
          const saved = JSON.parse(await readFile(fileFor(id), 'utf8'));
          return send(res, 200, saved.raw, { ETag: saved.etag });
        } catch (e) { if (e.code === 'ENOENT') return send(res, 404); throw e; }
      }
      if (secret && !equal(req.headers.authorization || '', secret)) { req.resume(); return send(res, 401); }
      const token = req.headers['x-write-token'];
      if (typeof token !== 'string' || !HEX_SECRET.test(token)) { req.resume(); return send(res, 401); }
      const writeHash = digest(token);
      if (req.headers['content-length'] && Number(req.headers['content-length']) > MAX_BODY) {
        req.resume(); return send(res, 413);
      }
      const body = await readBody(req);
      if (req.method === 'DELETE') {
        if (body.length) return send(res, 400);
        return await serialized(async () => {
          const existing = index.get(id);
          if (existing && !equal(existing.writeHash, writeHash)) return send(res, 403);
          await unlink(fileFor(id)).catch(e => { if (e.code !== 'ENOENT') throw e; });
          index.delete(id);
          send(res, 204);
        });
      }
      let envelope;
      try { envelope = validateEnvelope(body, now()); } catch { return send(res, 400); }
      const raw = JSON.stringify(envelope);
      const etag = `"${digest(raw)}"`;
      return await serialized(async () => {
        await prune();
        const existing = index.get(id);
        if (existing) {
          if (!equal(existing.writeHash, writeHash)) return send(res, 403);
          if (envelope.revision < existing.revision || (envelope.revision === existing.revision && etag !== existing.etag))
            return send(res, 409);
          // Retrying an identical PUT does not refresh expiry or rewrite a disk block.
          if (etag === existing.etag) return send(res, 204, '', { ETag: etag });
        } else if (index.size >= maxCards) return send(res, 507);
        const updatedAt = now();
        const tmp = fileFor(id) + '.' + randomUUID() + '.tmp';
        try {
          await writeFile(tmp, JSON.stringify({ format: 2, raw, writeHash, updatedAt, revision: envelope.revision, etag, ...(invitation ? { kind: 'invite' } : {}) }), { mode: 0o600, flag: 'wx' });
          await rename(tmp, fileFor(id));
        } finally { await unlink(tmp).catch(() => {}); }
        index.set(id, { writeHash, updatedAt, revision: envelope.revision, etag, kind: invitation ? 'invite' : undefined });
        send(res, 204, '', { ETag: etag });
      });
    } catch (e) {
      if (!res.headersSent) send(res, e.status || 500, '', e.status === 413 ? { Connection: 'close' } : {}); else res.end();
    } finally { inFlight--; }
  });
  const sweep = setInterval(() => { serialized(async () => { await prune(); await media.prune(); }).catch(() => {}); }, sweepIntervalMs);
  sweep.unref();
  server.on('close', () => clearInterval(sweep));
  server.requestTimeout = 300_000;
  server.headersTimeout = 10_000;
  server.keepAliveTimeout = 5_000;
  server.maxRequestsPerSocket = 100;
  server.maxConnections = 32;
  return server;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const publishKey = process.env.PUBLISH_KEY_FILE
    ? (await readFile(process.env.PUBLISH_KEY_FILE, 'utf8')).trim() : process.env.PUBLISH_KEY;
  const feedbackToken = process.env.FEEDBACK_GITHUB_TOKEN_FILE
    ? await readFile(process.env.FEEDBACK_GITHUB_TOKEN_FILE, 'utf8').then(value => value.trim()).catch(error => {
      // Optional feedback must not prevent existing cards/media from starting when its secret is not installed yet.
      if (['ENOENT', 'EACCES', 'EISDIR'].includes(error.code)) return undefined;
      throw error;
    }) : process.env.FEEDBACK_GITHUB_TOKEN;
  const server = await createRelay({ dataDir: process.env.DATA_DIR || './data', publishKey,
    maxCards: Number(process.env.MAX_CARDS || 500), rateLimitPerMinute: Number(process.env.RATE_LIMIT_PER_MINUTE || 240),
    maxInFlight: Number(process.env.MAX_IN_FLIGHT || 8), maxMediaFiles: Number(process.env.MAX_MEDIA_FILES || MAX_MEDIA_FILES),
    mediaQuotaBytes: Number(process.env.MEDIA_QUOTA_BYTES ?? DEFAULT_MEDIA_QUOTA),
    feedback: { repo: process.env.FEEDBACK_GITHUB_REPO, token: feedbackToken } });
  server.listen(Number(process.env.PORT || 8787), process.env.HOST || '127.0.0.1', () => console.log('SuperHealth encrypted relay ready'));
  const shutdown = () => { server.close(() => process.exit(0)); setTimeout(() => process.exit(1), 10_000).unref(); };
  process.once('SIGTERM', shutdown); process.once('SIGINT', shutdown);
}
