#!/usr/bin/env python3
"""Build the hiking data archive and embed the same verified bytes in Android. No network."""
import hashlib
import json
from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[3]
MODULES = ROOT / "SuperHealth-modules"
SRC = MODULES / "src/hiking"
APP = ROOT / "SuperHealth/app/src/superhealth/assets/plugins"
manifest = json.loads((SRC / "manifest.json").read_text())
assert manifest["id"] == "hiking" and manifest["kind"] == "hiking"
archive = MODULES / "dist" / f"hiking-{manifest['version']}.zip"
with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as out:
    for file in sorted(SRC.rglob("*")):
        if file.is_file():
            info = zipfile.ZipInfo(file.relative_to(SRC).as_posix(), (2026, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            out.writestr(info, file.read_bytes())
data = archive.read_bytes()
digest = hashlib.sha256(data).hexdigest()
for path in (MODULES / "dist/index.json", APP / "index.json"):
    index = json.loads(path.read_text())
    entry = next(m for m in index["modules"] if m["id"] == "hiking")
    entry.update(version=manifest["version"], url=archive.name, size=len(data), sha256=digest)
    path.write_text(json.dumps(index, ensure_ascii=False, indent=2) + "\n")
APP.mkdir(parents=True, exist_ok=True)
(APP / archive.name).write_bytes(data)
print(f"hiking {manifest['version']}: {len(data)} bytes, SHA-256 {digest}")
