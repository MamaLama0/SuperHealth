#!/usr/bin/env python3
"""Export or re-import the third-party (copyrighted) assets bundled in the SuperHealth flavor.

    export DEST   copy every third-party asset to DEST, keeping the app's relative paths, and write
                  DEST/index.html (gallery: images, Rive animations, body silhouettes, texts) and
                  DEST/manifest.csv (path, category, origin, size, code that uses it)
    import DEST   copy back the files of DEST that differ from the app tree (same relative paths);
                  new files are copied too, nothing is deleted

The app tree is app/src/superhealth. Everything not listed in CATEGORIES (DM Sans font: OFL; Swiss food
database: OSAV open data; widget/notification vector icons: Material Symbols, Apache 2.0) is not exported.
"""
import csv
import filecmp
import html
import json
import os
import shutil
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "superhealth")

# (relative folder or file, category label, origin, code that reads it)
CATEGORIES = [
    ("res/mipmap-mdpi", "Icône de l'app", "Apple (icône de l'app Santé)", "AndroidManifest (icône du lanceur)"),
    ("res/mipmap-hdpi", "Icône de l'app", "Apple (icône de l'app Santé)", "AndroidManifest (icône du lanceur)"),
    ("res/mipmap-xhdpi", "Icône de l'app", "Apple (icône de l'app Santé)", "AndroidManifest (icône du lanceur)"),
    ("res/mipmap-xxhdpi", "Icône de l'app", "Apple (icône de l'app Santé)", "AndroidManifest (icône du lanceur)"),
    ("res/mipmap-xxxhdpi", "Icône de l'app", "Apple (icône de l'app Santé)", "AndroidManifest (icône du lanceur)"),
    ("assets/misc", "Divers (logos, rubans, assiette)", "MacroFactor / MacroFactor Workouts", "nutrition/ui, training/ui"),
    ("assets/foods", "Icônes d'aliments", "MacroFactor", "nutrition/ui/FoodIcons.kt"),
    ("assets/bodyfat", "Repères de masse grasse", "MacroFactor", "nutrition/ui/screens/BodyScreens.kt"),
    ("assets/body", "Silhouettes (tracés)", "MacroFactor Workouts", "training/ui/components/WorkoutWidgets.kt (BodyMap)"),
    ("assets/muscles", "Vignettes de muscles", "MacroFactor Workouts", "training/ui/components/WorkoutWidgets.kt"),
    ("assets/eq", "Équipements", "MacroFactor Workouts", "training/ui/Assets.kt"),
    ("assets/ex", "Photos d'exercices", "MacroFactor Workouts", "training/ui/Assets.kt"),
    ("assets/md", "Fiches d'exercices (textes)", "MacroFactor Workouts", "training/ui/screens/ExerciseScreens.kt"),
    ("assets/db/exercises.json", "Base d'exercices", "MacroFactor Workouts", "training/domain/ExerciseDb.kt"),
    ("res/raw/beep.wav", "Son du minuteur", "MacroFactor Workouts", "training/ui/RestTimer.kt"),
]

IMAGE = (".webp", ".png", ".jpg", ".jpeg")


def files():
    for rel, cat, origin, used in CATEGORIES:
        p = os.path.join(ROOT, rel)
        if os.path.isfile(p):
            yield rel, cat, origin, used
        elif os.path.isdir(p):
            for dirpath, _, names in os.walk(p):
                for n in sorted(names):
                    full = os.path.join(dirpath, n)
                    yield os.path.relpath(full, ROOT), cat, origin, used


def export(dest):
    rows = []
    for rel, cat, origin, used in files():
        out = os.path.join(dest, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        shutil.copy2(os.path.join(ROOT, rel), out)
        rows.append((rel, cat, origin, os.path.getsize(out), used))
    with open(os.path.join(dest, "manifest.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["chemin", "catégorie", "origine", "octets", "utilisé par"])
        w.writerows(rows)
    with open(os.path.join(dest, "index.html"), "w") as f:
        f.write(gallery(rows))
    with open(os.path.join(dest, "ouvrir.sh"), "w") as f:
        f.write('#!/bin/sh\n# Galerie servie en local (les silhouettes et animations Rive ne se chargent pas en file://)\n'
                'cd "$(dirname "$0")" && (sleep 1 && xdg-open http://localhost:8765/index.html) & python3 -m http.server 8765\n')
    os.chmod(os.path.join(dest, "ouvrir.sh"), 0o755)
    total = sum(r[3] for r in rows)
    print(f"{len(rows)} fichiers, {total / 1e6:.1f} Mo -> {dest}")


def gallery(rows):
    by_cat = {}
    for r in rows:
        by_cat.setdefault(r[1], []).append(r)
    nav = "".join(f'<a href="#c{i}">{html.escape(c)} <b>{len(v)}</b></a>' for i, (c, v) in enumerate(by_cat.items()))
    sections = []
    for i, (cat, items) in enumerate(by_cat.items()):
        origin, used = items[0][2], items[0][4]
        body = []
        for rel, _, _, size, _ in items:
            e = html.escape(rel)
            name = html.escape(os.path.basename(rel))
            if rel.lower().endswith(IMAGE):
                body.append(f'<figure><a href="{e}"><img loading="lazy" src="{e}" alt=""></a><figcaption>{name}<span class="dim"></span></figcaption></figure>')
            elif rel.endswith(".riv"):
                body.append(f'<figure class="wide"><canvas data-riv="{e}" width="320" height="240"></canvas><figcaption>{name}</figcaption></figure>')
            elif rel.startswith("assets/body/"):
                body.append(f'<figure class="tall"><svg data-body="{e}"></svg><figcaption>{name}</figcaption></figure>')
            elif rel.endswith(".wav"):
                body.append(f'<figure class="wide"><audio controls src="{e}"></audio><figcaption>{name}</figcaption></figure>')
            else:
                body.append(f'<li><a href="{e}">{name}</a> <small>{size // 1024 or 1} Ko</small></li>')
        grid = "".join(b for b in body if not b.startswith("<li"))
        lst = "".join(b for b in body if b.startswith("<li"))
        sections.append(
            f'<section id="c{i}"><h2>{html.escape(cat)} <small>{len(items)} · {html.escape(origin)}</small></h2>'
            f'<p class="used">Utilisé par : {html.escape(used)}</p>'
            + (f'<div class="grid">{grid}</div>' if grid else "")
            + (f'<details><summary>{len(items)} fichiers</summary><ul class="files">{lst}</ul></details>' if lst else "")
            + "</section>")
    return TEMPLATE.replace("{{NAV}}", nav).replace("{{SECTIONS}}", "".join(sections)).replace("{{COUNT}}", str(len(rows)))


TEMPLATE = """<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Assets sous copyright</title>
<style>
:root{--bg:#f6f6f8;--card:#fff;--text:#111;--sub:#6e6e73;--line:#e3e3e8;--chip:#efeff3}
@media (prefers-color-scheme:dark){:root{--bg:#111;--card:#1c1c1e;--text:#f2f2f2;--sub:#a1a1a6;--line:#2c2c2e;--chip:#2c2c2e}}
body{margin:0;background:var(--bg);color:var(--text);font:15px/1.4 system-ui,sans-serif}
header{position:sticky;top:0;background:var(--bg);padding:16px;border-bottom:1px solid var(--line);z-index:2}
h1{margin:0 0 8px;font-size:22px} nav{display:flex;flex-wrap:wrap;gap:6px}
nav a{background:var(--chip);color:var(--text);text-decoration:none;padding:5px 10px;border-radius:99px;font-size:13px}
nav a b{color:var(--sub);font-weight:500} input{margin-top:10px;width:100%;max-width:420px;padding:8px 12px;border-radius:12px;border:1px solid var(--line);background:var(--card);color:var(--text)}
section{padding:8px 16px 24px} h2{font-size:18px} h2 small,.used{color:var(--sub);font-weight:400;font-size:13px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(120px,1fr));gap:10px}
figure{margin:0;background:var(--card);border-radius:14px;padding:8px;display:flex;flex-direction:column;align-items:center}
figure.wide{grid-column:span 2} figure.tall svg{width:100%;height:220px}
img{max-width:100%;height:110px;object-fit:contain;background:repeating-conic-gradient(var(--chip) 0 25%,transparent 0 50%) 0 0/14px 14px;border-radius:8px}
canvas{max-width:100%;border-radius:8px;background:var(--chip)}
figcaption{font-size:11px;color:var(--sub);word-break:break-all;text-align:center;margin-top:4px}
.files{columns:3 260px;font-size:13px} details{margin-top:8px}
</style></head><body>
<header><h1>Assets sous copyright <small style="color:var(--sub);font-size:14px">{{COUNT}} fichiers</small></h1><nav>{{NAV}}</nav>
<input id="q" placeholder="Filtrer par nom de fichier"></header>
{{SECTIONS}}
<script src="https://unpkg.com/@rive-app/canvas@2.21.6/rive.js"></script>
<script>
document.getElementById('q').addEventListener('input',e=>{const q=e.target.value.toLowerCase();
 document.querySelectorAll('figure,li').forEach(el=>{el.style.display=el.textContent.toLowerCase().includes(q)?'':'none'})});
document.querySelectorAll('img').forEach(i=>i.addEventListener('load',()=>{const s=i.closest('figure').querySelector('.dim');if(s)s.textContent=' · '+i.naturalWidth+'×'+i.naturalHeight}));
document.querySelectorAll('svg[data-body]').forEach(async s=>{try{const j=await (await fetch(s.dataset.body)).json();
 s.setAttribute('viewBox','0 0 '+j.w+' '+j.h);s.innerHTML=j.paths.map(p=>'<path d="'+p.d+'" fill="'+(p.id=='body'?'#dcdcdc':'#276ef1')+'" fill-opacity="'+(p.id=='body'?1:.55)+'"><title>'+p.id+'</title></path>').join('')}catch(e){s.outerHTML='<small>ouvrir via un serveur local (python3 -m http.server)</small>'}});
const io=new IntersectionObserver(es=>es.forEach(e=>{if(!e.isIntersecting||e.target.dataset.on)return;e.target.dataset.on=1;
 try{new rive.Rive({src:e.target.dataset.riv,canvas:e.target,autoplay:true})}catch(x){}}));
document.querySelectorAll('canvas[data-riv]').forEach(c=>io.observe(c));
</script></body></html>"""


def import_back(src):
    changed = added = 0
    for dirpath, _, names in os.walk(src):
        for n in names:
            full = os.path.join(dirpath, n)
            rel = os.path.relpath(full, src)
            if rel in ("index.html", "manifest.csv"):
                continue
            target = os.path.join(ROOT, rel)
            new = not os.path.exists(target)
            if not new and filecmp.cmp(full, target, shallow=False):
                continue
            if new:
                added += 1
            else:
                changed += 1
            os.makedirs(os.path.dirname(target), exist_ok=True)
            shutil.copy2(full, target)
            print(("+ " if new else "~ ") + rel)
    print(f"{changed} remplacé(s), {added} ajouté(s)")


if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in ("export", "import"):
        sys.exit(__doc__)
    (export if sys.argv[1] == "export" else import_back)(os.path.abspath(sys.argv[2]))
