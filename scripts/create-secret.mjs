import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
const path = resolve(process.argv[2] || 'secrets/publish_key');
await mkdir(dirname(path), { recursive: true, mode: 0o700 });
await writeFile(path, randomBytes(32).toString('hex') + '\n', { mode: 0o600, flag: 'wx' });
console.log('Publication secret created; its contents were not printed.');
