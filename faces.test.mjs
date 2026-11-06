import { test } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, sign } from 'node:crypto';
import { mkdtemp, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createRelay } from './server.mjs';
import { authorOf, REQUEST_TTL_MS, MAX_REQUESTS_PER_INBOX } from './faces.mjs';

// Même format que League.signSocial : SHG1.charge.signature, signature ECDSA P-256 sur « SuperHealth group v1: » + charge.
const identity = () => {
  const { publicKey, privateKey } = generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
  const key = publicKey.export({ format: 'der', type: 'spki' }).toString('base64url');
  const signed = payload => {
    const bytes = Buffer.from(JSON.stringify(payload));
    return `SHG1.${bytes.toString('base64url')}.${sign('sha256', Buffer.concat([Buffer.from('SuperHealth group v1:'), bytes]), privateKey).toString('base64url')}`;
  };
  return { key, id: authorOf(key), body: payload => JSON.stringify({ key, signed: signed(payload) }) };
};
const jpeg = Buffer.from([0xff, 0xd8, 0xff, 0xe0, 1, 2, 3, 4]).toString('base64url');

async function fixture() {
  const dataDir = await mkdtemp(join(tmpdir(), 'sh-faces-'));
  let current = Date.now();
  const server = await createRelay({ dataDir, publishKey: 'test-only-'.repeat(5), now: () => current });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const base = `http://127.0.0.1:${server.address().port}`;
  return { base, get now() { return current; }, advance(ms) { current += ms; },
    async close() { await new Promise(resolve => server.close(resolve)); await rm(dataDir, { recursive: true, force: true }); } };
}

test('visage : seul l\'auteur le publie, il est lisible par tous et se retire avec une vignette vide', async () => {
  const f = await fixture(); const me = identity(); const other = identity();
  const url = `${f.base}/v2/faces/${me.id}`;
  try {
    assert.equal((await fetch(url)).status, 404);
    assert.equal((await fetch(url, { method: 'PUT', body: me.body({ t: 'face', id: me.id, at: f.now, image: jpeg }) })).status, 204);
    const got = await fetch(url); assert.equal(got.status, 200);
    const face = await got.json(); assert.equal(face.key, me.key); assert.match(face.signed, /^SHG1\./);
    // Une autre clé ne peut pas écrire à cette adresse, même avec une charge valable.
    assert.equal((await fetch(url, { method: 'PUT', body: other.body({ t: 'face', id: me.id, at: f.now + 1, image: jpeg }) })).status, 400);
    // Rejouer une version plus ancienne est refusé ; une charge trafiquée aussi.
    assert.equal((await fetch(url, { method: 'PUT', body: me.body({ t: 'face', id: me.id, at: f.now - 1, image: jpeg }) })).status, 409);
    const forged = JSON.parse(me.body({ t: 'face', id: me.id, at: f.now + 5, image: jpeg }));
    forged.signed = forged.signed.replace(/^SHG1\.[^.]+/, 'SHG1.' + Buffer.from(JSON.stringify({ t: 'face', id: me.id, at: f.now + 5, image: '' })).toString('base64url'));
    assert.equal((await fetch(url, { method: 'PUT', body: JSON.stringify(forged) })).status, 400);
    // Pas un JPEG, trop vieux : refusés.
    assert.equal((await fetch(url, { method: 'PUT', body: me.body({ t: 'face', id: me.id, at: f.now + 2, image: 'aGVsbG8' }) })).status, 400);
    assert.equal((await fetch(url, { method: 'PUT', body: me.body({ t: 'face', id: me.id, at: f.now - 3_600_000, image: jpeg }) })).status, 400);
    f.advance(1000);
    assert.equal((await fetch(url, { method: 'PUT', body: me.body({ t: 'face', id: me.id, at: f.now, image: '' }) })).status, 204);
    assert.equal((await fetch(url)).status, 404);
  } finally { await f.close(); }
});

test('demandes d\'ami : une par expéditeur, effacées par le destinataire seulement, expirées après 24 h', async () => {
  const f = await fixture(); const me = identity(); const a = identity(); const b = identity();
  const inbox = `${f.base}/v2/faces/${me.id}/requests`;
  const ask = (who, data = 'Y2lwaGVy') => fetch(inbox, { method: 'POST', body: who.body({ t: 'friend', to: me.id, at: f.now, data }) });
  try {
    assert.deepEqual(await (await fetch(inbox)).json(), { items: [] });
    assert.equal((await ask(a)).status, 204);
    assert.equal((await ask(a, 'bm91dmVhdQ')).status, 204);
    assert.equal((await ask(b)).status, 204);
    let items = (await (await fetch(inbox)).json()).items;
    assert.equal(items.length, 2);
    assert.equal(items.filter(r => r.key === a.key).length, 1);
    // On ne se demande pas soi-même ; une demande adressée à quelqu'un d'autre ne passe pas.
    assert.equal((await fetch(inbox, { method: 'POST', body: me.body({ t: 'friend', to: me.id, at: f.now, data: 'eA' }) })).status, 400);
    assert.equal((await fetch(inbox, { method: 'POST', body: a.body({ t: 'friend', to: b.id, at: f.now, data: 'eA' }) })).status, 400);
    // Un tiers ne peut pas vider la boîte ; le destinataire efface une demande précise, puis tout.
    assert.equal((await fetch(inbox, { method: 'DELETE', body: a.body({ t: 'clear', id: me.id, at: f.now }) })).status, 400);
    assert.equal((await fetch(inbox, { method: 'DELETE', body: me.body({ t: 'clear', id: me.id, at: f.now, from: a.id }) })).status, 204);
    items = (await (await fetch(inbox)).json()).items;
    assert.deepEqual(items.map(r => r.key), [b.key]);
    f.advance(REQUEST_TTL_MS + 1);
    assert.deepEqual(await (await fetch(inbox)).json(), { items: [] });
    for (let i = 0; i < MAX_REQUESTS_PER_INBOX; i++) assert.equal((await ask(identity())).status, 204);
    assert.equal((await ask(identity())).status, 429);
    assert.equal((await fetch(inbox, { method: 'DELETE', body: me.body({ t: 'clear', id: me.id, at: f.now }) })).status, 204);
    assert.deepEqual(await (await fetch(inbox)).json(), { items: [] });
  } finally { await f.close(); }
});
