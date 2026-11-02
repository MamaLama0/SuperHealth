import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createCipheriv, createDecipheriv, randomBytes } from 'node:crypto';
import { request } from 'node:http';
import { mkdtemp, rm, readFile, readdir, stat } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createRelay, validateEnvelope, MAX_BODY, TTL_MS } from './server.mjs';

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
    const files = await readdir(f.dataDir); assert.equal(files.length, 1); assert.match(files[0], /^[a-f0-9]{64}\.json$/);
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
    assert.equal((await readdir(f.dataDir)).length, 1);
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
    assert.equal(status, 413); assert.deepEqual(await readdir(f.dataDir), []);
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
    assert.equal((await readdir(f.dataDir)).length, 1);
    assert.equal((await fetch(f.base + route)).status, 404);
    assert.equal((await fetch(f.base + otherRoute)).status, 200);
  } finally { await f.close(); }
});
