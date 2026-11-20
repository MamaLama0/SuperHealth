# Illustrations d'exercices (licence achetée) : une image fixe par illustration dans l'app, les animations dans le plugin.
# tools/exercises/exercise_illustrations_map.csv donne, pour chaque exercice de la base, l'illustration qui le représente
# (plusieurs exercices peuvent partager la même ; un exercice absent du fichier reste sans image).
#   assets/ex/<illustration>.webp : première image de l'animation d'origine, 360 × 360, fond blanc
#   assets/db/exercises.json      : champ « il » (illustration) à la place de « img » / « pi » ; rien d'autre ne change
#   <plugin>/anim/<illustration>.webp + <plugin>/anim.json : images suivantes de l'animation, en planche de 3 colonnes
# Les animations d'origine sont des allers-retours : seules les images distinctes sont gardées, anim.json donne l'ordre de lecture
# (0 = image fixe de l'app, k = k-ième case de la planche) et la durée de chaque image.
# Repart toujours de zéro : assets/ex est vidé puis rempli.
# usage : python3 tools/exercises/exercise_illustrations.py <dossier des animations d'origine> [--plugin <src/animations du dépôt des plugins>]
#         (depuis la racine du dépôt ; ImageMagick `magick` requis)
import csv, json, math, os, subprocess, sys, tempfile
from concurrent.futures import ThreadPoolExecutor

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
APP = os.path.join(ROOT, 'app', 'src', 'superhealth', 'assets')
SIZE, STILL_Q, ANIM_Q, COLS = 360, 80, 72, 3
SAME = 0.004        # écart en dessous duquel deux images de l'animation sont la même (aller et retour : ≤ 0,002 ; voisines : ≥ 0,006)

args = [a for a in sys.argv[1:] if not a.startswith('--')]
if not args: sys.exit(__doc__ or 'usage : exercise_illustrations.py <dossier des animations> [--plugin <dossier>]')
SRC = args[0]
PLUGIN = sys.argv[sys.argv.index('--plugin') + 1] if '--plugin' in sys.argv else None

with open(os.path.join(ROOT, 'tools', 'exercises', 'exercise_illustrations_map.csv')) as f:
    rows = list(csv.DictReader(l for l in f if not l.startswith('#')))
db_path = os.path.join(APP, 'db', 'exercises.json')
db = json.load(open(db_path))
known = {e['id'] for e in db['exercises']}
bad = [r['exercise'] for r in rows if r['exercise'] not in known] + \
      [r['illustration'] for r in rows if not os.path.exists(os.path.join(SRC, r['illustration'] + '.webp'))]
if bad: sys.exit('inconnus dans le fichier de correspondance : ' + ', '.join(sorted(set(bad))))
if len({r['exercise'] for r in rows}) != len(rows): sys.exit('un exercice a deux illustrations')
chosen = {r['exercise']: r['illustration'] for r in rows}
ids = sorted(set(chosen.values()))

def magick(*a, **k): return subprocess.run(['magick', *a], check=True, **k)

exdir = os.path.join(APP, 'ex')
for f in os.listdir(exdir): os.remove(os.path.join(exdir, f))
def still(i):
    magick(os.path.join(SRC, i + '.webp[0]'), '-background', 'white', '-alpha', 'remove', '-alpha', 'off', '-resize', f'{SIZE}x{SIZE}',
           '-strip', '-quality', str(STILL_Q), '-define', 'webp:method=6', os.path.join(exdir, i + '.webp'))
with ThreadPoolExecutor(os.cpu_count()) as pool: list(pool.map(still, ids))

for e in db['exercises']:
    e.pop('img', None); e.pop('pi', None); e.pop('il', None)
    if e['id'] in chosen: e['il'] = chosen[e['id']]
with open(db_path, 'w') as f: f.write(json.dumps(db, ensure_ascii=False, separators=(',', ':')))
print(f'{len(chosen)} exercices illustrés sur {len(db["exercises"])}, {len(ids)} images')

if PLUGIN:
    N = 32
    def dist(a, b): return math.sqrt(sum((x - y) ** 2 for x, y in zip(a, b)) / len(a)) / 255
    def anim(i):
        src = os.path.join(SRC, i + '.webp')
        raw = magick(src, '-coalesce', '-resize', f'{N}x{N}!', '-colorspace', 'Gray', '-depth', '8', 'gray:-', capture_output=True).stdout
        small = [raw[k:k + N * N] for k in range(0, len(raw), N * N)]
        ticks = magick('identify', '-format', '%T ', src, capture_output=True, text=True).stdout.split()
        keep, seq = [0], []                         # keep : images d'origine gardées (la première est l'image fixe de l'app)
        for k, s in enumerate(small):
            same = next((n for n, j in enumerate(keep) if dist(small[j], s) < SAME), None)
            if same is None: keep.append(k); same = len(keep) - 1
            seq.append(same)
        ms = [min(1500, max(40, int(t) * 10)) for t in ticks]
        if len(keep) > 1:
            with tempfile.TemporaryDirectory() as tmp:
                magick(src, '-coalesce', '-background', 'white', '-alpha', 'remove', '-alpha', 'off', os.path.join(tmp, 'f%02d.png'))
                cells = [os.path.join(tmp, 'f%02d.png' % k) for k in keep[1:]]
                magick('montage', *cells, '-tile', f'{COLS}x', '-geometry', f'{SIZE}x{SIZE}+0+0', '-background', 'white', '-strip',
                       '-quality', str(ANIM_Q), '-define', 'webp:method=6', os.path.join(PLUGIN, 'anim', i + '.webp'))
        return i, {'n': len(keep) - 1, 'seq': seq, 'ms': ms}
    os.makedirs(os.path.join(PLUGIN, 'anim'), exist_ok=True)
    for f in os.listdir(os.path.join(PLUGIN, 'anim')): os.remove(os.path.join(PLUGIN, 'anim', f))
    with ThreadPoolExecutor(os.cpu_count()) as pool: index = dict(pool.map(anim, ids))
    index = {i: a for i, a in index.items() if a['n'] > 0}
    with open(os.path.join(PLUGIN, 'anim.json'), 'w') as f:
        f.write(json.dumps({'size': SIZE, 'cols': COLS, 'anim': index}, separators=(',', ':')))
    total = sum(os.path.getsize(os.path.join(PLUGIN, 'anim', f)) for f in os.listdir(os.path.join(PLUGIN, 'anim')))
    print(f'{len(index)} animations, {total / 1e6:.1f} Mo')
