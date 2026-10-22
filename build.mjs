// Construit dist/ : une archive par module (src/<id>, version de son manifest.json) et dist/index.json (catalogue lu par l'app).
// usage : node build.mjs   (zip requis ; archives reproductibles : dates fixes, fichiers triés)
import { readFileSync, writeFileSync, readdirSync, statSync, utimesSync, existsSync, rmSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { createHash } from "node:crypto";
import { join } from "node:path";

const root = new URL(".", import.meta.url).pathname;
const catalog = JSON.parse(readFileSync(join(root, "catalog.json"), "utf8"));
const fixed = new Date("2026-01-01T00:00:00Z");

function files(dir, base = "") {
  return readdirSync(join(dir, base)).sort().flatMap((f) => {
    const rel = base ? `${base}/${f}` : f;
    return statSync(join(dir, rel)).isDirectory() ? files(dir, rel) : [rel];
  });
}

const modules = catalog.modules.map((m) => {
  const src = join(root, "src", m.id);
  const manifest = JSON.parse(readFileSync(join(src, "manifest.json"), "utf8"));
  const list = files(src);
  list.forEach((f) => utimesSync(join(src, f), fixed, fixed));
  const name = `${m.id}-${manifest.version}.zip`;
  const out = join(root, "dist", name);
  if (existsSync(out)) rmSync(out);
  execFileSync("zip", ["-X", "-D", "-q", out, ...list], { cwd: src });
  const bytes = readFileSync(out);
  return { ...m, version: manifest.version, size: bytes.length, sha256: createHash("sha256").update(bytes).digest("hex"), url: name };
});
writeFileSync(join(root, "dist", "index.json"), JSON.stringify({ name: catalog.name, modules }, null, 2) + "\n");
console.log(modules.map((m) => `${m.id} ${m.version} ${m.size} o ${m.sha256.slice(0, 12)}`).join("\n"));
