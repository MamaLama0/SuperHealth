#!/usr/bin/env node
// Offre des paquets (et/ou des éclats) à un ou plusieurs comptes précis, en ajoutant un cadeau à rewards.json.
//
//   node give.mjs <id> [<id> …] --packs 2 [--eclats 0] [--note "Bravo !"] [--until 2026-12-31] [--file data/rewards.json]
//
// <id> : identifiant d'échange de 64 caractères hexadécimaux (« ID cadeau » sous la carte de l'ami, touchez pour le copier).
// Chaque cadeau a un identifiant neuf : il est versé une seule fois par compte, au prochain contact de l'app avec le serveur.
import { readFileSync, writeFileSync } from 'node:fs';
import { randomBytes } from 'node:crypto';
import { normalize } from './rewards.mjs';

const args = process.argv.slice(2);
const opt = name => { const i = args.indexOf('--' + name); if (i < 0) return undefined; const v = args[i + 1]; args.splice(i, 2); return v; };
const file = opt('file') ?? new URL('./rewards.json', import.meta.url).pathname;
const packs = Number(opt('packs') ?? 0), eclats = Number(opt('eclats') ?? 0);
const note = opt('note') ?? (packs > 1 ? `${packs} paquets offerts` : packs === 1 ? 'Un paquet offert' : `${eclats} éclats offerts`);
const until = opt('until');
const accounts = args.map(a => a.trim().toLowerCase());

const fail = message => { console.error(message); process.exit(1); };
if (!accounts.length || accounts.some(a => !/^[a-f0-9]{64}$/.test(a))) fail('Donnez au moins un identifiant de 64 caractères hexadécimaux.');
if (!Number.isSafeInteger(packs) || !Number.isSafeInteger(eclats) || packs < 0 || packs > 20 || eclats < 0 || eclats > 5000 || packs + eclats === 0)
  fail('--packs 1 à 20 et/ou --eclats 1 à 5000.');
if (until !== undefined && Number.isNaN(Date.parse(until))) fail('--until doit être une date ISO (2026-12-31).');

const rewards = JSON.parse(readFileSync(file, 'utf8'));
rewards.gifts = Array.isArray(rewards.gifts) ? rewards.gifts : [];
const id = `gift-${new Date().toISOString().slice(0, 10)}-${randomBytes(3).toString('hex')}`;
const gift = { id, ...(packs ? { packs } : {}), ...(eclats ? { eclats } : {}), note, accounts, ...(until ? { until } : {}) };
rewards.gifts.push(gift);
if (rewards.gifts.length > 50) fail('Plus de 50 cadeaux : retirez les anciens (déjà versés) avant d\'en ajouter.');
if (!normalize(rewards).gifts.some(g => g.id === id)) fail('Cadeau refusé par la validation du serveur.');
writeFileSync(file, JSON.stringify(rewards, null, 2) + '\n');
console.log(`Cadeau ${id} ajouté à ${file} :`, JSON.stringify(gift));
