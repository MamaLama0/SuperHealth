import { createSocialStore, SOCIAL_RETENTION_DAYS } from './social.mjs';
import { createMediaStore, DEFAULT_MEDIA_QUOTA, MAX_MEDIA_FILES } from './media.mjs';
import { createFeedbackHandler } from './feedback.mjs';
import { createEconomy } from './economy.mjs';
import { createFaces } from './faces.mjs';
import { createServer } from 'node:http';
import { createHash, timingSafeEqual, randomUUID } from 'node:crypto';
import { mkdir, readFile, writeFile, rename, unlink, readdir, stat } from 'node:fs/promises';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const MAX_BODY = 98_304;
export const TTL_MS = 35 * 86_400_000;
const ROUTE = /^\/v2\/(?:cards\/[a-f0-9]{64}\/[a-f0-9]{64}(?:\/media\/[a-f0-9]{64})?|invites\/[a-f0-9]{64}(?:\/responses(?:\/[a-f0-9]{64})?)?)$/;
export const INVITE_TTL_MS = 24 * 3_600_000;
const ttl = record => record.kind === 'invite' ? INVITE_TTL_MS : record.kind === 'recovery' ? SOCIAL_RETENTION_DAYS * 86400000 : TTL_MS;
const HEX_SECRET = /^[a-f0-9]{64}$/;
const digest = value => createHash('sha256').update(value).digest('hex');
const equal = (a, b) => Buffer.byteLength(a) === Buffer.byteLength(b) && timingSafeEqual(Buffer.from(a), Buffer.from(b));
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
  socialRetentionDays = SOCIAL_RETENTION_DAYS, maxSocialEntries = 200000, maxMediaPerMailbox = 20000, maxNewMailboxesPerHour = 12,
  now = Date.now, sweepIntervalMs = 3_600_000, feedback: feedbackOptions } = {}) {
  if (publishKey != null && publishKey !== '' && (typeof publishKey !== 'string' || publishKey.length < 32 || publishKey.length > 512 || /[\r\n]/.test(publishKey)))
    throw new Error('PUBLISH_KEY must have 32..512 characters');
  integer(maxCards, 1, 10_000, 'MAX_CARDS');
  integer(rateLimitPerMinute, 1, 10_000, 'RATE_LIMIT_PER_MINUTE');
  integer(maxInFlight, 1, 64, 'MAX_IN_FLIGHT');
  integer(maxMediaFiles, 1, 1_000_000, 'MAX_MEDIA_FILES');
  integer(socialRetentionDays, 365, 3650, 'SOCIAL_RETENTION_DAYS');
  integer(maxSocialEntries, 1, 2_000_000, 'MAX_SOCIAL_ENTRIES');
  integer(maxMediaPerMailbox, 1, 100000, 'MAX_MEDIA_PER_MAILBOX');
  integer(maxNewMailboxesPerHour, 1, 10000, 'MAX_NEW_MAILBOXES_PER_HOUR');
  integer(mediaQuotaBytes, 29, Number.MAX_SAFE_INTEGER, 'MEDIA_QUOTA_BYTES');
  await mkdir(dataDir, { recursive: true, mode: 0o700 });
  const media = await createMediaStore(dataDir, { now, maxFiles: maxMediaFiles, quota: mediaQuotaBytes, maxPerMailbox: maxMediaPerMailbox, retentionDays: socialRetentionDays });
  const social = await createSocialStore(dataDir, { now, retentionDays: socialRetentionDays, maxEntries: maxSocialEntries });
  const handoffs = await createSocialStore(join(dataDir, 'handoffs'), { now, retentionDays: socialRetentionDays, maxEntries: maxSocialEntries, maxPerMailbox: 1000 });
  const ownerDir = join(dataDir, 'owners'); await mkdir(ownerDir, { recursive: true, mode: 0o700 });
  const owners = new Map();
  for (const name of await readdir(ownerDir)) {
    if (!/^[a-f0-9]{64}\.json$/.test(name)) continue;
    const path = join(ownerDir, name);
    if ((await stat(path)).size > 1024) throw new Error('invalid owner record');
    const value = JSON.parse(await readFile(path, 'utf8'));
    if (!HEX_SECRET.test(value.writeHash) || typeof value.revoked !== 'boolean') throw new Error('invalid owner');
    owners.set(name.slice(0, -5), value);
  }
  const saveOwner = async (id, value) => {
    const target = join(ownerDir, id + '.json'); const tmp = target + '.' + randomUUID() + '.tmp';
    try { await writeFile(tmp, JSON.stringify(value), { flag: 'wx', mode: 0o600 }); await rename(tmp, target); }
    finally { await unlink(tmp).catch(() => {}); }
    owners.set(id, value);
  };
  const feedback = createFeedbackHandler({ ...feedbackOptions, now });
  const secret = publishKey ? `Bearer ${publishKey}` : null;
  const economy = await createEconomy(dataDir, { now, secret, maxAccounts: maxCards * 10, maxNewPerHour: maxNewMailboxesPerHour });
  const faces = await createFaces(dataDir, { now, maxFaces: maxCards * 10, maxInboxes: maxCards * 10 });
  const index = new Map();
  const rates = new Map();
  const creationRates = new Map();
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
    if (record.kind != null && !['invite', 'recovery'].includes(record.kind)) throw new Error('invalid stored kind');
    if (record.kind !== 'invite' && !owners.has(name.slice(0, -5))) await saveOwner(name.slice(0, -5), { writeHash: record.writeHash, revoked: false });
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
    if (req.url === '/health' && req.method === 'GET') return send(res, 200, JSON.stringify({ status: 'ok', protocol: 2, media: 1, social: 1, socialRetentionDays, recovery: 1, recoveryRevocation: 1, streamFreeze: 1, capabilityStatus: 1, feedback: feedback.enabled ? 1 : 0, economy: 3, faces: 1 }));
    if (req.url === '/v2/feedback') return feedback(req, res);
    // Économie des cartes : portefeuille, tirage et quêtes décidés par le serveur (voir economy.mjs).
    const economyRoute = /^\/v2\/economy\/([a-f0-9]{64})$/.exec(req.url || '');
    if (economyRoute) {
      if (inFlight >= maxInFlight) { req.resume(); return send(res, 503, '', { 'Retry-After': '2' }); }
      inFlight++;
      try { return await economy(req, res, economyRoute[1], readBody); }
      catch (e) { if (!res.headersSent) send(res, e.status || 500); else res.end(); }
      finally { inFlight--; }
    }
    // Visages publics et demandes d'ami chiffrées du Groupe (voir faces.mjs).
    const faceRoute = /^\/v2\/faces\/([a-f0-9]{16})(\/requests)?$/.exec(req.url || '');
    if (faceRoute) {
      if (inFlight >= maxInFlight) { req.resume(); return send(res, 503, '', { 'Retry-After': '2' }); }
      inFlight++;
      try { return await faces(req, res, faceRoute[1], !!faceRoute[2], readBody, send); }
      catch (e) { if (!res.headersSent) send(res, e.status || 500); else res.end(); }
      finally { inFlight--; }
    }
    const archiveRoute = (req.url || '').match(/^(\/v2\/cards\/[a-f0-9]{64}\/[a-f0-9]{64})\/social(?:\/([a-f0-9]{32}))?(?:\?before=([0-9]{16}%3A[a-f0-9]{32}|[0-9]{16}:[a-f0-9]{32}))?$/);
    const handoffRoute = (req.url || '').match(/^(\/v2\/cards\/[a-f0-9]{64}\/[a-f0-9]{64})\/handoffs\/([a-f0-9]{32})$/);
    const statusRoute = /^(\/v2\/cards\/[a-f0-9]{64}\/[a-f0-9]{64})\/status$/.exec(req.url || '');
    const freezeRoute = /^(\/v2\/cards\/[a-f0-9]{64}\/[a-f0-9]{64})\/freeze$/.exec(req.url || '');
    const recoveryRoute = /^\/v2\/recovery\/[a-f0-9]{64}$/.test(req.url || '');
    if (!ROUTE.test(req.url || '') && !archiveRoute && !handoffRoute && !recoveryRoute && !freezeRoute && !statusRoute) return send(res, 404);
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
      if (statusRoute) {
        req.resume(); if (req.method !== 'GET') return send(res, 405);
        const owner = owners.get(digest(statusRoute[1]));
        return owner ? send(res, 200, JSON.stringify({ revoked: owner.revoked, frozen: Boolean(owner.frozen) })) : send(res, 404);
      }
      if (freezeRoute) {
        req.resume();
        if (req.method !== 'POST') return send(res, 405);
        const owner = owners.get(digest(freezeRoute[1]));
        const token = req.headers['x-write-token'];
        if (!owner || owner.revoked || typeof token !== 'string' || !HEX_SECRET.test(token) || !equal(owner.writeHash, digest(token)) ||
            (secret && !equal(req.headers.authorization || '', secret))) return send(res, 403);
        return await serialized(async () => {
          const current = owners.get(digest(freezeRoute[1]));
          if (!current || current.revoked) return send(res, 403);
          await saveOwner(digest(freezeRoute[1]), { ...current, frozen: true });
          send(res, 204);
        });
      }
      if (handoffRoute || archiveRoute) {
        const match = handoffRoute || archiveRoute;
        const parent = owners.get(digest(match[1]));
        const token = req.headers['x-write-token'];
        const exists = parent && (!parent.revoked || Boolean(handoffRoute));
        const authorized = exists && !parent.revoked && !parent.frozen && (!secret || equal(req.headers.authorization || '', secret)) &&
          typeof token === 'string' && HEX_SECRET.test(token) && equal(parent.writeHash, digest(token));
        return await (handoffRoute ? handoffs : social)(req, res, { parent: match[1], id: match[2], before: match[3] ? decodeURIComponent(match[3]) : null, exists,
          authorized: () => authorized && !owners.get(digest(match[1]))?.revoked && !owners.get(digest(match[1]))?.frozen,
          readBody, validateEnvelope });
      }
      const mediaRoute = req.url.match(/^(\/v2\/cards\/[a-f0-9]{64}\/[a-f0-9]{64})\/media\/[a-f0-9]{64}$/);
      if (mediaRoute) {
        const parent = owners.get(digest(mediaRoute[1]));
        const exists = parent && !parent.revoked;
        const token = req.headers['x-write-token'];
        const authorized = exists && !parent.frozen && (!secret || equal(req.headers.authorization || '', secret)) &&
          typeof token === 'string' && HEX_SECRET.test(token) && equal(parent.writeHash, digest(token));
        return await media(req, res, { exists, authorized: () => authorized && !owners.get(digest(mediaRoute[1]))?.revoked && !owners.get(digest(mediaRoute[1]))?.frozen, parent: mediaRoute[1] });
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
          const owner = owners.get(id);
          if (owner && !equal(owner.writeHash, writeHash)) return send(res, 403);
          if (!owner && recoveryRoute) {
            if (owners.size >= maxCards * 10) return send(res, 507);
            const address = digest(req.socket.remoteAddress || '?'); const hour = Math.floor(now() / 3600000);
            for (const [key, value] of creationRates) if (value.hour !== hour) creationRates.delete(key);
            const previous = creationRates.get(address);
            if ((previous?.count || 0) >= maxNewMailboxesPerHour) return send(res, 429, '', { 'Retry-After': '3600' });
            if (!previous && creationRates.size >= 1024) return send(res, 503, '', { 'Retry-After': '60' });
            creationRates.set(address, { hour, count: (previous?.count || 0) + 1 });
            await saveOwner(id, { writeHash, revoked: true });
          }
          if (owner) {
            await saveOwner(id, { ...owner, revoked: true });
            await social.purge(req.url); await media.purge(req.url);
          }
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
        const owner = owners.get(id);
        if (owner && (owner.revoked || owner.frozen || !equal(owner.writeHash, writeHash))) return send(res, 403);
        if (!invitation && !owner && owners.size >= maxCards * 10) return send(res, 507);
        if (!existing && !owner && !invitation) {
          const address = digest(req.socket.remoteAddress || '?'); const hour = Math.floor(now() / 3600000);
          for (const [key, value] of creationRates) if (value.hour !== hour) creationRates.delete(key);
          const previous = creationRates.get(address);
          if ((previous?.count || 0) >= maxNewMailboxesPerHour) return send(res, 429, '', { 'Retry-After': '3600' });
          if (!previous && creationRates.size >= 1024) return send(res, 503, '', { 'Retry-After': '60' });
          creationRates.set(address, { hour, count: (previous?.count || 0) + 1 });
        }
        if (existing) {
          if (!equal(existing.writeHash, writeHash)) return send(res, 403);
          if (envelope.revision < existing.revision || (envelope.revision === existing.revision && etag !== existing.etag))
            return send(res, 409);
          // Retrying an identical PUT does not refresh expiry or rewrite a disk block.
          if (etag === existing.etag) return send(res, 204, '', { ETag: etag });
        } else if (index.size >= maxCards) return send(res, 507);
        if (!invitation && !owner) await saveOwner(id, { writeHash, revoked: false });
        const updatedAt = now();
        const tmp = fileFor(id) + '.' + randomUUID() + '.tmp';
        try {
          await writeFile(tmp, JSON.stringify({ format: 2, raw, writeHash, updatedAt, revision: envelope.revision, etag, ...(invitation ? { kind: 'invite' } : recoveryRoute ? { kind: 'recovery' } : {}) }), { mode: 0o600, flag: 'wx' });
          await rename(tmp, fileFor(id));
        } finally { await unlink(tmp).catch(() => {}); }
        index.set(id, { writeHash, updatedAt, revision: envelope.revision, etag, kind: invitation ? 'invite' : recoveryRoute ? 'recovery' : undefined });
        send(res, 204, '', { ETag: etag });
      });
    } catch (e) {
      if (!res.headersSent) send(res, e.status || 500, '', e.status === 413 ? { Connection: 'close' } : {}); else res.end();
    } finally { inFlight--; }
  });
  const sweep = setInterval(() => { serialized(async () => { await prune(); await media.prune(); await social.prune(); await handoffs.prune(); }).catch(() => {}); }, sweepIntervalMs);
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
    socialRetentionDays: Number(process.env.SOCIAL_RETENTION_DAYS || SOCIAL_RETENTION_DAYS),
    maxSocialEntries: Number(process.env.MAX_SOCIAL_ENTRIES || 200000), maxMediaPerMailbox: Number(process.env.MAX_MEDIA_PER_MAILBOX || 20000),
    maxNewMailboxesPerHour: Number(process.env.MAX_NEW_MAILBOXES_PER_HOUR || 12),
    feedback: { repo: process.env.FEEDBACK_GITHUB_REPO, token: feedbackToken } });
  server.listen(Number(process.env.PORT || 8787), process.env.HOST || '127.0.0.1', () => console.log('SuperHealth encrypted relay ready'));
  const shutdown = () => { server.close(() => process.exit(0)); setTimeout(() => process.exit(1), 10_000).unref(); };
  process.once('SIGTERM', shutdown); process.once('SIGINT', shutdown);
}
