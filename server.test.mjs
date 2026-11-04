import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createCipheriv, createDecipheriv, randomBytes, createHash } from 'node:crypto';
import { request } from 'node:http';
import { mkdtemp, rm, readFile, readdir, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { MAX_MEDIA, MEDIA_TTL } from './media.mjs';
import { createRelay, validateEnvelope, MAX_BODY, TTL_MS, INVITE_TTL_MS } from './server.mjs';

const publishKey = 'test-only-'.repeat(5);
const token = 'a'.repeat(64);
const route = `/v2/cards/${'b'.repeat(64)}/${'c'.repeat(64)}`;
const otherRoute = `/v2/cards/${'d'.repeat(64)}/${'e'.repeat(64)}`;
const key = randomBytes(32);
const secretPlaintext = 'SH1.signed-private-health-card-no-name-on-server';
const headers = (writeToken = token) => ({ Authorization: `Bearer ${publishKey}`, 'X-Write-Token': writeToken });
const encrypt = (revision = Date.now(), path = route, content = secretPlaintext) => {
  const nonce = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, nonce);
  cipher.setAAD(Buffer.from('SuperHealth relay v2:' + path));
  const ciphertext = Buffer.concat([cipher.update(content), cipher.final(), cipher.getAuthTag()]);
  return JSON.stringify({ v: 2, revision, nonce: nonce.toString('base64url'), ciphertext: ciphertext.toString('base64url') });
};
const decrypt = (raw, path = route) => {
  const x = JSON.parse(raw), bytes = Buffer.from(x.ciphertext, 'base64url');
  const decipher = createDecipheriv('aes-256-gcm', key, Buffer.from(x.nonce, 'base64url'));
  decipher.setAAD(Buffer.from('SuperHealth relay v2:' + path)); decipher.setAuthTag(bytes.subarray(-16));
  return Buffer.concat([decipher.update(bytes.subarray(0, -16)), decipher.final()]).toString();
};
const cardFiles = async dir => (await readdir(dir)).filter(n => /^[a-f0-9]{64}\.json$/.test(n));
async function fixture(options = {}) {
  const dataDir = await mkdtemp(join(tmpdir(), 'sh-encrypted-relay-'));
  let current = Date.now();
  let server;
  const start = async () => {
    server = await createRelay({ dataDir, publishKey, now: () => current, ...options });
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    return `http://127.0.0.1:${server.address().port}`;
  };
  let base = await start();
  return { dataDir, get base() { return base; }, get now() { return current; }, advance(ms) { current += ms; },
    waitForRequest() { return new Promise(resolve => server.once('request', resolve)); },
    async restart() { await new Promise(resolve => server.close(resolve)); base = await start(); },
    async close() { await new Promise(resolve => server.close(resolve)); await rm(dataDir, { recursive: true, force: true }); } };
}

test('opaque cards: encrypted roundtrip, owner authorization, conditional GET, no identity in storage', async () => {
  const f = await fixture();
  const put = (body, opts = {}) => fetch(f.base + route, { method: 'PUT', body, headers: headers(), ...opts });
  try {
    const raw = encrypt(f.now);
    assert.equal((await put(raw, { headers: {} })).status, 401);
    assert.equal((await put(raw, { headers: { Authorization: `Bearer ${publishKey}` } })).status, 401);
    const response = await put(raw); assert.equal(response.status, 204);
    const etag = response.headers.get('etag'); assert.match(etag, /^"[a-f0-9]{64}"$/);
    const got = await fetch(f.base + route); assert.equal(got.status, 200); assert.equal(decrypt(await got.text()), secretPlaintext);
    const notModified = await fetch(f.base + route, { headers: { 'If-None-Match': etag } });
    assert.equal(notModified.status, 304); assert.equal(await notModified.text(), '');
    const files = await cardFiles(f.dataDir); assert.equal(files.length, 1); assert.match(files[0], /^[a-f0-9]{64}\.json$/);
    const stored = await readFile(join(f.dataDir, files[0]), 'utf8');
    for (const hidden of [secretPlaintext, key.toString('base64url'), token, route, publishKey, '127.0.0.1']) assert.equal(stored.includes(hidden), false);
    assert.equal((await stat(join(f.dataDir, files[0]))).mode & 0o777, 0o600);
    const before = stored;
    assert.equal((await put(raw)).status, 204);
    assert.equal(await readFile(join(f.dataDir, files[0]), 'utf8'), before);
    assert.equal((await put(encrypt(f.now + 1), { headers: headers('f'.repeat(64)) })).status, 403);
    await f.restart();
    assert.equal((await fetch(f.base + route, { headers: { 'If-None-Match': etag } })).status, 304);
    assert.equal((await put(encrypt(f.now - 1))).status, 409);
    assert.equal((await put(encrypt(f.now))).status, 409);
    assert.equal((await put(encrypt(f.now + 1))).status, 204);
    assert.equal((await fetch(f.base + '/v2/cards')).status, 404);
    assert.equal((await fetch(f.base + route.replace(/c$/, '0'))).status, 404);
    assert.equal((await fetch(f.base + route + '?key=secret')).status, 404);
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: headers('f'.repeat(64)) })).status, 403);
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: headers() })).status, 204);
    assert.equal((await fetch(f.base + route)).status, 404);
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: headers() })).status, 204);
  } finally { await f.close(); }
});

test('replay protection remains atomic under concurrent publications and foreign writers cannot race ownership', async () => {
  const f = await fixture();
  try {
    const first = encrypt(f.now);
    assert.equal((await fetch(f.base + route, { method: 'PUT', body: first, headers: headers() })).status, 204);
    const replies = await Promise.all([2, 1].map(n => fetch(f.base + route, { method: 'PUT', body: encrypt(f.now + n), headers: headers() })));
    assert.ok(replies.every(r => [204, 409].includes(r.status)));
    assert.equal((await (await fetch(f.base + route)).json()).revision, f.now + 2);
    const foreign = await fetch(f.base + route, { method: 'PUT', body: encrypt(f.now + 3), headers: headers('f'.repeat(64)) });
    assert.equal(foreign.status, 403);
  } finally { await f.close(); }
});

test('capacity, expiry, no plaintext compatibility, malformed envelopes and body bounds', async () => {
  const f = await fixture({ maxCards: 1 });
  const put = (raw, path = route) => fetch(f.base + path, { method: 'PUT', body: raw, headers: headers() });
  try {
    assert.equal((await fetch(f.base + '/health')).status, 200);
    assert.equal((await put('SH1.old-plaintext.card')).status, 400);
    assert.equal((await put(JSON.stringify({ ...JSON.parse(encrypt(f.now)), relayKey: key.toString('base64url') }))).status, 400);
    for (const change of [{ v: 1 }, { revision: f.now + 301000 }, { revision: 0 }, { nonce: 'short' }, { ciphertext: '=' }])
      assert.equal((await put(JSON.stringify({ ...JSON.parse(encrypt(f.now)), ...change }))).status, 400);
    assert.equal((await put('x'.repeat(MAX_BODY + 1))).status, 413);
    assert.equal((await put(encrypt(f.now))).status, 204);
    assert.equal((await put(encrypt(f.now, otherRoute), otherRoute)).status, 507);
    f.advance(TTL_MS + 1);
    assert.equal((await fetch(f.base + route)).status, 404);
    assert.equal((await put(encrypt(f.now, otherRoute), otherRoute)).status, 204);
    assert.equal((await cardFiles(f.dataDir)).length, 1);
    await f.restart(); assert.equal((await fetch(f.base + route)).status, 404);
    const delivered = await (await fetch(f.base + otherRoute)).text();
    assert.equal(decrypt(delivered, otherRoute), secretPlaintext);
    assert.throws(() => decrypt(delivered, route));
  } finally { await f.close(); }
});

test('chunked oversized body returns 413 without retaining it', async () => {
  const f = await fixture();
  try {
    const status = await new Promise((resolve, reject) => {
      const req = request(f.base + route, { method: 'PUT', headers: headers() }, res => { res.resume(); resolve(res.statusCode); });
      req.on('error', reject);
      req.write('a'.repeat(MAX_BODY)); req.end('x');
    });
    assert.equal(status, 413); assert.deepEqual(await cardFiles(f.dataDir), []);
  } finally { await f.close(); }
});

test('bounded request rate and invalid configuration', async () => {
  const f = await fixture({ rateLimitPerMinute: 2 });
  try {
    assert.equal((await fetch(f.base + route)).status, 404);
    assert.equal((await fetch(f.base + route)).status, 404);
    const limited = await fetch(f.base + route); assert.equal(limited.status, 429); assert.equal(limited.headers.get('retry-after'), '60');
    f.advance(60_000); assert.equal((await fetch(f.base + route)).status, 404);
    assert.throws(() => validateEnvelope(encrypt(f.now).replace('"v":2', '"v":1')));
    await assert.rejects(createRelay({ dataDir: f.dataDir, publishKey: 'short' }));
    await assert.rejects(createRelay({ dataDir: f.dataDir, publishKey, maxCards: 0 }));
  } finally { await f.close(); }
});


test('active-request cap releases after an incomplete request finishes', async () => {
  const f = await fixture({ maxInFlight: 1 });
  let req;
  try {
    const accepted = f.waitForRequest();
    const completed = new Promise((resolve, reject) => {
      req = request(f.base + route, { method: 'PUT', headers: headers() }, res => { res.resume(); resolve(res.statusCode); });
      req.on('error', reject); req.write('{');
    });
    // Wait for actual HTTP arrival, independent of machine scheduling speed.
    await accepted;
    const busy = await fetch(f.base + route); assert.equal(busy.status, 503); assert.equal(busy.headers.get('retry-after'), '2');
    req.end(); assert.equal(await completed, 400);
    assert.equal((await fetch(f.base + route)).status, 404);
  } finally { req?.destroy(); await f.close(); }
});

test('expired records are pruned before enforcing a reduced startup quota', async () => {
  const options = { maxCards: 2 }; const f = await fixture(options);
  try {
    assert.equal((await fetch(f.base + route, { method: 'PUT', headers: headers(), body: encrypt(f.now) })).status, 204);
    f.advance(TTL_MS / 2);
    assert.equal((await fetch(f.base + otherRoute, { method: 'PUT', headers: headers(), body: encrypt(f.now, otherRoute) })).status, 204);
    f.advance(TTL_MS / 2 + 1); options.maxCards = 1;
    await f.restart();
    assert.equal((await cardFiles(f.dataDir)).length, 1);
    assert.equal((await fetch(f.base + route)).status, 404);
    assert.equal((await fetch(f.base + otherRoute)).status, 200);
  } finally { await f.close(); }
});


test('standard server needs no shared secret but still enforces mailbox ownership', async () => {
  const f = await fixture({ publishKey: undefined });
  try {
    const body = encrypt(f.now);
    assert.equal((await fetch(f.base + route, { method: 'PUT', body })).status, 401);
    assert.equal((await fetch(f.base + route, { method: 'PUT', body, headers: { 'X-Write-Token': token } })).status, 204);
    assert.equal((await fetch(f.base + route, { method: 'PUT', body: encrypt(f.now + 1), headers: { 'X-Write-Token': 'f'.repeat(64) } })).status, 403);
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: { 'X-Write-Token': 'f'.repeat(64) } })).status, 403);
    await f.restart();
    assert.equal((await fetch(f.base + route, { method: 'PUT', body: encrypt(f.now + 2), headers: { 'X-Write-Token': token } })).status, 204);
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: { 'X-Write-Token': token } })).status, 204);
  } finally { await f.close(); }
});


test('short invitations expire after 24h and reciprocal requests stay opaque, bounded and owner-readable', async () => {
  const f = await fixture({ publishKey: undefined });
  const invite = '/v2/invites/' + 'a'.repeat(64);
  const responses = invite + '/responses';
  const sender = 'e'.repeat(64);
  const ownerHeaders = { 'X-Write-Token': token };
  try {
    const envelope = encrypt(f.now, invite);
    assert.equal((await fetch(f.base + invite, { method: 'PUT', body: envelope, headers: ownerHeaders })).status, 204);
    assert.equal((await fetch(f.base + invite)).status, 200);
    const requestBody = encrypt(f.now, responses + '/' + sender);
    assert.equal((await fetch(f.base + responses + '/' + sender, { method: 'POST', body: requestBody })).status, 204);
    assert.equal((await fetch(f.base + responses)).status, 401);
    assert.equal((await fetch(f.base + responses, { headers: { 'X-Write-Token': 'f'.repeat(64) } })).status, 401);
    let result = await (await fetch(f.base + responses, { headers: ownerHeaders })).json();
    assert.equal(result[sender], requestBody);
    await f.restart();
    result = await (await fetch(f.base + responses, { headers: ownerHeaders })).json();
    assert.equal(result[sender], requestBody);
    const disk = (await Promise.all((await cardFiles(f.dataDir)).map(n => readFile(join(f.dataDir, n), 'utf8')))).join('');
    assert.equal(disk.includes(secretPlaintext), false);
    assert.equal((await fetch(f.base + responses + '/' + sender, { method: 'DELETE', headers: ownerHeaders })).status, 204);
    assert.deepEqual(await (await fetch(f.base + responses, { headers: ownerHeaders })).json(), {});
    f.advance(INVITE_TTL_MS + 1);
    assert.equal((await fetch(f.base + invite)).status, 404);
    assert.equal((await fetch(f.base + responses + '/' + sender, { method: 'POST', body: encrypt(f.now, responses + '/' + sender) })).status, 404);
    await f.restart();
    assert.equal((await cardFiles(f.dataDir)).length, 0);
  } finally { await f.close(); }
});

test('media remain opaque, owned by an existing mailbox, bounded, persistent and expiring', async () => {
  const f = await fixture();
  const mediaRoute = route + '/media/' + '1'.repeat(64);
  const otherMedia = route + '/media/' + '2'.repeat(64);
  const nonce = randomBytes(12);
  const cipher = createCipheriv('aes-256-gcm', key, nonce);
  cipher.setAAD(Buffer.from('SuperHealth media v1:' + '1'.repeat(64)));
  const plain = Buffer.from('a private photo or video never readable by the relay');
  const opaque = Buffer.concat([nonce, cipher.update(plain), cipher.final(), cipher.getAuthTag()]);
  const put = (path, body, auth = headers()) => fetch(f.base + path, { method: 'PUT', body, headers: auth });
  try {
    assert.equal((await put(mediaRoute, opaque)).status, 404);
    assert.equal((await put(route, encrypt(f.now))).status, 204);
    assert.equal((await put(mediaRoute, opaque, headers('f'.repeat(64)))).status, 403);
    assert.equal((await put(mediaRoute, opaque)).status, 204);
    assert.equal((await put(mediaRoute, opaque)).status, 409);
    assert.equal((await put(otherMedia, Buffer.alloc(20))).status, 413);
    assert.equal((await put(otherMedia, Buffer.alloc(MAX_MEDIA + 1))).status, 413);
    const response = await fetch(f.base + mediaRoute);
    assert.equal(response.headers.get('content-type'), 'application/octet-stream');
    const downloaded = Buffer.from(await response.arrayBuffer()); assert.deepEqual(downloaded, opaque);
    const decipher = createDecipheriv('aes-256-gcm', key, downloaded.subarray(0, 12));
    decipher.setAAD(Buffer.from('SuperHealth media v1:' + '1'.repeat(64))); decipher.setAuthTag(downloaded.subarray(-16));
    assert.deepEqual(Buffer.concat([decipher.update(downloaded.subarray(12, -16)), decipher.final()]), plain);
    const files = await readdir(join(f.dataDir, 'media'));
    assert.equal(files.length, 2);
    for (const name of files) {
      assert.match(name, /^[a-f0-9]{64}\.(json|bin)$/);
      const stored = await readFile(join(f.dataDir, 'media', name));
      assert.equal(stored.includes(plain), false); assert.equal(stored.includes(Buffer.from(mediaRoute)), false);
    }
    await f.restart(); assert.equal((await fetch(f.base + mediaRoute)).status, 200);
    assert.equal((await fetch(f.base + mediaRoute + '?x=1')).status, 404);
    assert.equal((await fetch(f.base + mediaRoute, { method: 'DELETE', headers: headers('f'.repeat(64)) })).status, 403);
    assert.equal((await fetch(f.base + mediaRoute, { method: 'DELETE', headers: headers() })).status, 204);
    assert.equal((await fetch(f.base + mediaRoute)).status, 404);
    assert.equal((await put(mediaRoute, opaque)).status, 204);
    f.advance(TTL_MS + 1); await f.restart();
    assert.equal((await fetch(f.base + mediaRoute)).status, 200);
    f.advance(MEDIA_TTL); await f.restart();
    assert.equal((await fetch(f.base + mediaRoute)).status, 404);
    assert.deepEqual(await readdir(join(f.dataDir, 'media')), []);
  } finally { await f.close(); }
});

test('chunked media overflow returns 413, leaves no partial files, then accepts another upload', async () => {
  const f = await fixture();
  const path = route + '/media/' + '3'.repeat(64);
  try {
    await fetch(f.base + route, { method: 'PUT', body: encrypt(f.now), headers: headers() });
    const status = await new Promise((resolveStatus, reject) => {
      const req = request(f.base + path, { method: 'PUT', headers: headers() }, res => { res.resume(); resolveStatus(res.statusCode); });
      req.on('error', reject);
      req.flushHeaders(); // Force Transfer-Encoding: chunked rather than an inferred Content-Length.
      req.write(Buffer.alloc(8 * 1024 * 1024));
      req.end(Buffer.alloc(MAX_MEDIA - 8 * 1024 * 1024 + 100));
    });
    assert.equal(status, 413);
    assert.deepEqual(await readdir(join(f.dataDir, 'media')), []);
    assert.equal((await fetch(f.base + path, { method: 'PUT', body: Buffer.alloc(50), headers: headers() })).status, 204);
  } finally { await f.close(); }
});

test('attachment count is bounded independently of bytes and deletion frees the quota', async () => {
  const f = await fixture({ maxMediaFiles: 1 });
  const first = route + '/media/' + '4'.repeat(64);
  const second = route + '/media/' + '5'.repeat(64);
  const put = path => fetch(f.base + path, { method: 'PUT', body: Buffer.alloc(40), headers: headers() });
  try {
    await fetch(f.base + route, { method: 'PUT', body: encrypt(f.now), headers: headers() });
    assert.equal((await put(first)).status, 204);
    assert.equal((await put(second)).status, 507);
    await fetch(f.base + first, { method: 'DELETE', headers: headers() });
    assert.equal((await put(second)).status, 204);
  } finally { await f.close(); }
});

test('global media byte quota survives restart and deletion restores capacity', async () => {
  const f = await fixture({ mediaQuotaBytes: 100 });
  const first = route + '/media/' + '6'.repeat(64);
  const second = otherRoute + '/media/' + '7'.repeat(64);
  const third = route + '/media/' + '8'.repeat(64);
  const put = (path, size) => fetch(f.base + path, { method: 'PUT', body: Buffer.alloc(size), headers: headers() });
  try {
    assert.equal((await fetch(f.base + route, { method: 'PUT', body: encrypt(f.now), headers: headers() })).status, 204);
    assert.equal((await fetch(f.base + otherRoute, { method: 'PUT', body: encrypt(f.now, otherRoute), headers: headers() })).status, 204);
    assert.equal((await put(first, 40)).status, 204);
    assert.equal((await put(second, 60)).status, 204);
    assert.equal((await put(third, 29)).status, 507);
    await f.restart();
    assert.equal((await put(third, 29)).status, 507);
    assert.equal((await fetch(f.base + first, { method: 'DELETE', headers: headers() })).status, 204);
    assert.equal((await put(third, 29)).status, 204);
    assert.equal((await (await fetch(f.base + second)).arrayBuffer()).byteLength, 60);
  } finally { await f.close(); }
});

test('invalid media byte quotas fail before startup', async () => {
  const f = await fixture();
  try {
    for (const mediaQuotaBytes of [0, -1, 28, 99.5, NaN, Infinity, Number.MAX_SAFE_INTEGER + 1]) {
      await assert.rejects(createRelay({ dataDir: f.dataDir, mediaQuotaBytes }), /MEDIA_QUOTA_BYTES/);
    }
  } finally { await f.close(); }
});


test('anonymous feedback is private, minimal and idempotent, and refuses public destinations', async () => {
  let privateRepo = true; const created = [];
  const f = await fixture({ feedback: { repo: 'owner/private-feedback', token: 'test-token-not-real-'.repeat(2), githubFetch: async (url, options) => {
    if (url.endsWith('/issues')) { created.push(JSON.parse(options.body)); return { status: 201 }; }
    return { ok: true, json: async () => ({ private: privateRepo }) };
  } } });
  const report = { id: '12345678-1234-1234-1234-123456789012', kind: 'BUG', message: 'The update button remains visible', version: '3.0.4', rating: 4 };
  const submit = body => fetch(f.base + '/v2/feedback', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });
  try {
    assert.equal((await submit(report)).status, 201);
    assert.equal((await submit(report)).status, 201);
    assert.equal(created.length, 1);
    assert.equal(created[0].body.includes(report.message), true);
    assert.equal(created[0].body.includes('test-token'), false);
    assert.equal((await submit({ ...report, id: '22345678-1234-1234-1234-123456789012', message: 'short' })).status, 400);
    privateRepo = false;
    assert.equal((await submit({ ...report, id: '32345678-1234-1234-1234-123456789012' })).status, 503);
    assert.equal(created.length, 1);
  } finally { await f.close(); }
});

test('feedback is disabled without server credentials and relay remains available', async () => {
  const f = await fixture();
  try {
    assert.equal((await fetch(f.base + '/v2/feedback', { method: 'POST' })).status, 503);
    assert.equal((await fetch(f.base + '/health')).status, 200);
    assert.equal((await (await fetch(f.base + '/health')).json()).feedback, 0);
  } finally { await f.close(); }
});

test('social history is opaque, paginated, owner-written and survives expired health cards and restart', async () => {
  const f = await fixture();
  try {
    assert.equal((await fetch(f.base + route, { method: 'PUT', body: encrypt(f.now), headers: headers() })).status, 204);
    for (let n = 1; n <= 43; n++) {
      const path = route + '/social/' + n.toString(16).padStart(32, '0');
      assert.equal((await fetch(f.base + path, { method: 'PUT', body: encrypt(f.now + n, path, 'SHG1.signed-social-only'), headers: headers() })).status, 204);
    }
    const first = await (await fetch(f.base + route + '/social')).json();
    assert.equal(first.items.length, 20); assert.ok(first.next);
    const second = await (await fetch(f.base + route + '/social?before=' + encodeURIComponent(first.next))).json();
    const third = await (await fetch(f.base + route + '/social?before=' + encodeURIComponent(second.next))).json();
    assert.equal(second.items.length, 20); assert.equal(third.items.length, 3); assert.equal(third.next, null);
    assert.equal(new Set([...first.items, ...second.items, ...third.items].map(item => item.id)).size, 43);
    const path = route + '/social/' + first.items[0].id;
    assert.equal(decrypt(first.items[0].raw, path), 'SHG1.signed-social-only');
    assert.equal((await fetch(f.base + path, { method: 'PUT', body: encrypt(f.now + 100, path), headers: headers('f'.repeat(64)) })).status, 403);
    assert.equal((await fetch(f.base + route + '/social?before=../../bad')).status, 404);
    f.advance(TTL_MS + 1); await f.restart();
    assert.equal((await fetch(f.base + route)).status, 404);
    assert.equal((await (await fetch(f.base + route + '/social')).json()).items.length, 20);
    const box = (await readdir(join(f.dataDir, 'social')))[0];
    const saved = await readFile(join(f.dataDir, 'social', box, first.items[0].id + '.json'), 'utf8');
    assert.equal(saved.includes('signed-social-only'), false); assert.equal(saved.includes(route), false);
    f.advance(5 * 366 * 86400000); await f.restart();
    assert.deepEqual((await (await fetch(f.base + route + '/social')).json()).items, []);
  } finally { await f.close(); }
});

test('expired mailbox retains its owner; revocation removes social/media but preserves private handoffs', async () => {
  const f = await fixture();
  const post = route + '/social/' + '1'.repeat(32);
  const handoff = route + '/handoffs/' + '2'.repeat(32);
  const media = route + '/media/' + '3'.repeat(64);
  try {
    const put = (path, body, auth = headers()) => fetch(f.base + path, { method: 'PUT', body, headers: auth });
    assert.equal((await put(route, encrypt(f.now))).status, 204);
    assert.equal((await put(post, encrypt(f.now, post))).status, 204);
    assert.equal((await put(handoff, encrypt(f.now, handoff))).status, 204);
    assert.equal((await put(media, randomBytes(100))).status, 204);
    f.advance(TTL_MS + 1); await f.restart();
    assert.equal((await put(route, encrypt(f.now), headers('f'.repeat(64)))).status, 403);
    assert.equal((await put(route, encrypt(f.now))).status, 204);
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: headers() })).status, 204);
    assert.equal((await fetch(f.base + route + '/social')).status, 404);
    assert.equal((await fetch(f.base + media)).status, 404);
    assert.equal((await fetch(f.base + handoff)).status, 200);
    assert.equal((await put(route, encrypt(f.now + 1))).status, 403);
    assert.equal((await put(handoff, encrypt(f.now + 1, handoff))).status, 403);
    await f.restart();
    assert.equal((await put(route, encrypt(f.now + 2))).status, 403);
    assert.deepEqual(await readdir(join(f.dataDir, 'media')), []);
  } finally { await f.close(); }
});

test('social updates reject replay and conflicting retries, and quotas are bounded', async () => {
  const f = await fixture({ maxSocialEntries: 1, maxMediaPerMailbox: 1 });
  const path = route + '/social/' + 'a'.repeat(32);
  try {
    const put = (path, body) => fetch(f.base + path, { method: 'PUT', body, headers: headers() });
    assert.equal((await put(route, encrypt(f.now))).status, 204);
    const first = encrypt(f.now, path);
    assert.equal((await put(path, first)).status, 204);
    assert.equal((await put(path, first)).status, 204);
    assert.equal((await put(path, encrypt(f.now, path))).status, 409);
    assert.equal((await put(path, encrypt(f.now + 1, path, 'signed deletion'))).status, 204);
    assert.equal((await put(path, first)).status, 409);
    const other = route + '/social/' + 'b'.repeat(32);
    assert.equal((await put(other, encrypt(f.now + 2, other))).status, 507);
    assert.equal((await put(route + '/media/' + 'c'.repeat(64), randomBytes(100))).status, 204);
    assert.equal((await put(route + '/media/' + 'd'.repeat(64), randomBytes(100))).status, 507);
  } finally { await f.close(); }
});

test('social recovery has its own long-lived opaque slot and owner authorization', async () => {
  const f = await fixture(); const path = '/v2/recovery/' + 'a'.repeat(64);
  try {
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers(), body: encrypt(f.now, path, 'social credentials only') })).status, 204);
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers('f'.repeat(64)), body: encrypt(f.now + 1, path) })).status, 403);
    f.advance(TTL_MS + 1); await f.restart();
    assert.equal(decrypt(await (await fetch(f.base + path)).text(), path), 'social credentials only');
    const health = await (await fetch(f.base + '/health')).json(); assert.equal(health.social, 1); assert.equal(health.recovery, 1);
    assert.equal(health.recoveryRevocation, 1);
    assert.equal((await fetch(f.base + path, { method: 'DELETE', headers: headers() })).status, 204);
    assert.equal((await fetch(f.base + path)).status, 404);
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers(), body: encrypt(f.now + 1, path) })).status, 403);
    await f.restart();
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers(), body: encrypt(f.now + 2, path) })).status, 403);
  } finally { await f.close(); }
});

test('mailbox creation is limited independently from existing users publications', async () => {
  const f = await fixture({ maxNewMailboxesPerHour: 2 });
  try {
    const put = path => fetch(f.base + path, { method: 'PUT', body: encrypt(f.now, path), headers: headers() });
    assert.equal((await put(route)).status, 204);
    assert.equal((await put(otherRoute)).status, 204);
    const third = `/v2/cards/${'1'.repeat(64)}/${'2'.repeat(64)}`;
    const response = await put(third); assert.equal(response.status, 429); assert.equal(response.headers.get('retry-after'), '3600');
    f.advance(1); assert.equal((await put(route)).status, 204);
    f.advance(3600000); assert.equal((await put(third)).status, 204);
  } finally { await f.close(); }
});


test('freezing a social stream preserves reads and refuses every subsequent mutation after restart', async () => {
  const f = await fixture();
  try {
    assert.equal((await fetch(f.base + route, { method: 'PUT', headers: headers(), body: encrypt(f.now) })).status, 204);
    const path = route + '/social/' + 'a'.repeat(32);
    const body = encrypt(f.now, path, 'final membership decision');
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers(), body })).status, 204);
    assert.equal((await fetch(f.base + route + '/freeze', { method: 'POST', headers: headers('f'.repeat(64)) })).status, 403);
    assert.equal((await fetch(f.base + route + '/freeze', { method: 'POST', headers: headers() })).status, 204);
    await f.restart();
    assert.equal(decrypt(await (await fetch(f.base + path)).text(), path), 'final membership decision');
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers(), body: encrypt(f.now + 1, path) })).status, 403);
    assert.equal((await fetch(f.base + route, { method: 'PUT', headers: headers(), body: encrypt(f.now + 1) })).status, 403);
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: headers() })).status, 204);
    assert.equal((await fetch(f.base + route, { method: 'PUT', headers: headers(), body: encrypt(f.now + 2) })).status, 403);
  } finally { await f.close(); }
});


test('media retries require the same ciphertext hash and slow uploads cannot commit after freezing', async () => {
  const f = await fixture();
  try {
    assert.equal((await fetch(f.base + route, { method: 'PUT', headers: headers(), body: encrypt(f.now) })).status, 204);
    const path = route + '/media/' + '9'.repeat(64), bytes = randomBytes(128);
    const hash = createHash('sha256').update(bytes).digest('hex');
    const put = (digest) => fetch(f.base + path, { method: 'PUT', headers: { ...headers(), 'X-Content-SHA256': digest }, body: bytes });
    assert.equal((await put(hash)).status, 204);
    assert.equal((await put(hash)).status, 204);
    assert.equal((await put('0'.repeat(64))).status, 409);
    const slow = route + '/media/' + '8'.repeat(64);
    let upload;
    const status = new Promise((resolve, reject) => {
      upload = request(f.base + slow, { method: 'PUT', headers: { ...headers(), 'Content-Length': 128 } }, res => { res.resume(); resolve(res.statusCode); });
      upload.on('error', reject);
    });
    const started = f.waitForRequest(); upload.write(bytes.subarray(0, 32)); await started;
    assert.equal((await fetch(f.base + route + '/freeze', { method: 'POST', headers: headers() })).status, 204);
    upload.end(bytes.subarray(32));
    assert.equal(await status, 403);
    assert.equal((await fetch(f.base + slow)).status, 404);
    assert.deepEqual(await (await fetch(f.base + route + '/status')).json(), { revoked: false, frozen: true });
    assert.equal((await fetch(f.base + route, { method: 'DELETE', headers: headers() })).status, 204);
    assert.deepEqual(await (await fetch(f.base + route + '/status')).json(), { revoked: true, frozen: true });
    await f.restart();
    assert.equal((await fetch(f.base + path)).status, 404);
  } finally { await f.close(); }
});


test('revoking an unused recovery code reserves a permanent bounded tombstone', async () => {
  const f = await fixture({ maxNewMailboxesPerHour: 1 });
  const path = '/v2/recovery/' + '2'.repeat(64);
  try {
    assert.equal((await fetch(f.base + path, { method: 'DELETE', headers: headers() })).status, 204);
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers(), body: encrypt(f.now, path) })).status, 403);
    assert.equal((await fetch(f.base + '/v2/recovery/' + '3'.repeat(64), { method: 'DELETE', headers: headers() })).status, 429);
    await f.restart();
    assert.equal((await fetch(f.base + path, { method: 'PUT', headers: headers(), body: encrypt(f.now + 1, path) })).status, 403);
  } finally { await f.close(); }
});
