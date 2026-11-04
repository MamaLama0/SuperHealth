#!/usr/bin/env python3
"""Vendor the portable social implementation into the independent iOS repository."""
from pathlib import Path
import argparse, hashlib, json, shutil
parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
source=Path(__file__).resolve().parents[1]/'social-core'
target=source.parent.parent/'SuperHealth-ios'/'social-core'
files={p.relative_to(source):p.read_bytes() for p in source.rglob('*.kt')}
if args.check:
    expected={str(p):hashlib.sha256(data).hexdigest() for p,data in files.items()}
    actual={str(p.relative_to(target)):hashlib.sha256(p.read_bytes()).hexdigest() for p in target.rglob('*.kt')}
    if actual!=expected:raise SystemExit('Portable social sources differ. Run tools/sync-social-core.py.')
else:
    for rel,data in files.items():
        p=target/rel;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(data)
    for p in target.rglob('*.kt'):
        if p.relative_to(target) not in files:p.unlink()
    (target/'README.md').write_text('Sources partagées avec Android, copiées par `SuperHealth/tools/sync-social-core.py`. Modifier la source Android puis synchroniser. `--check` vérifie leur identité. Le projet iOS compile de façon autonome.\n')
print(f'{len(files)} portable sources verified' if args.check else f'{len(files)} portable sources synchronized')
