# Images d'exercices redessinées (../Asset-free/assets/exbis, droits achetés) à la place des originales (../Asset-free/assets/ex).
# tools/exercise_images/map.csv donne pour chaque image redessinée le nom de l'originale qu'elle remplace (mêmes angles a/b/c).
#   assets/ex/<id>_<a|b|c>.webp : image redessinée convertie en WebP (352 × 640, recadrée comme les originales 360 × 640)
#   assets/db/exercises.json   : copie compacte de l'original (angles a/b/c d'origine)
# Repart toujours de zéro : le dossier assets/ex de l'app est vidé puis rempli ; une originale absente du fichier est copiée telle quelle.
# usage : python3 tools/exercise_images.py   (depuis la racine du dépôt ; ImageMagick `magick` requis)
import csv, json, os, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIG = os.path.join(ROOT, '..', 'Asset-free', 'assets')
APP = os.path.join(ROOT, 'app', 'src', 'superhealth', 'assets')

with open(os.path.join(ROOT, 'tools', 'exercise_images', 'map.csv')) as f:
    rows = list(csv.DictReader(l for l in f if not l.startswith('#')))
orig = set(os.listdir(os.path.join(ORIG, 'ex')))
bad = [r['ex'] for r in rows if r['ex'] not in orig] + [r['exbis'] for r in rows if not os.path.exists(os.path.join(ORIG, 'exbis', r['exbis']))]
if bad: sys.exit('inconnus dans map.csv : ' + ', '.join(bad))
if len({r['ex'] for r in rows}) != len(rows): sys.exit('map.csv : une image d\'origine est remplacée deux fois')

exdir = os.path.join(APP, 'ex')
for f in os.listdir(exdir): os.remove(os.path.join(exdir, f))
done = {r['ex'] for r in rows}
for f in orig - done: shutil.copyfile(os.path.join(ORIG, 'ex', f), os.path.join(exdir, f))

def convert(r):
    subprocess.run(['magick', os.path.join(ORIG, 'exbis', r['exbis']), '-strip', '-quality', '85', '-define', 'webp:method=6',
                    os.path.join(exdir, r['ex'])], check=True)
with ThreadPoolExecutor(os.cpu_count()) as pool: list(pool.map(convert, rows))

db = json.load(open(os.path.join(ORIG, 'db', 'exercises.json')))
with open(os.path.join(APP, 'db', 'exercises.json'), 'w') as f:
    f.write(json.dumps(db, ensure_ascii=False, separators=(',', ':')))
print(f'{len(rows)} images redessinées, {len(orig - done)} originales')
