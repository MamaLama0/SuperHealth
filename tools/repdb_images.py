# Images d'exercices RepDB (repdb.co, offre gratuite, attribution obligatoire) à la place de celles de MacroFactor, pour les
# exercices listés dans tools/repdb/map.csv. Repart toujours des originaux (../Asset-free/assets) : retirer une ligne du
# fichier puis relancer remet les images d'origine.
#   assets/db/exercises.json : img = phases RepDB (s = début, p = fin du mouvement, m = image unique), pi = la première
#   assets/ex/<id>_<s|p|m>.webp : copie des images RepDB (512 × 512, carrées) ; les angles a/b/c de l'exercice sont retirés
# usage : python3 tools/repdb_images.py   (depuis la racine du dépôt ; RepDB décompressé dans ../Asset-free/RepDB)
import csv, json, os, shutil, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIG = os.path.join(ROOT, '..', 'Asset-free', 'assets')
REPDB = os.path.join(ROOT, '..', 'Asset-free', 'RepDB')
APP = os.path.join(ROOT, 'app', 'src', 'superhealth', 'assets')
KEY = {'start': 's', 'peak': 'p', 'main': 'm'}

rep = {e['id']: e for e in json.load(open(os.path.join(REPDB, 'exercises.json')))['exercises']}
with open(os.path.join(ROOT, 'tools', 'repdb', 'map.csv')) as f:
    rows = [r for r in csv.DictReader(l for l in f if not l.startswith('#'))]
mapping = {r['mf_id']: r['repdb_id'] for r in rows}

db = json.load(open(os.path.join(ORIG, 'db', 'exercises.json')))
ids = {e['id'] for e in db['exercises']}
bad = [k for k, v in mapping.items() if k not in ids or v not in rep]
if bad: sys.exit('inconnus dans map.csv : ' + ', '.join(bad))

exdir = os.path.join(APP, 'ex')
# images de tous les exercices remises à l'état d'origine, puis remplacement
for f in os.listdir(exdir):
    if f.rsplit('_', 1)[-1] in ('s.webp', 'p.webp', 'm.webp'): os.remove(os.path.join(exdir, f))
for f in os.listdir(os.path.join(ORIG, 'ex')):
    if f.split('_')[0] not in mapping and not os.path.exists(os.path.join(exdir, f)):
        shutil.copyfile(os.path.join(ORIG, 'ex', f), os.path.join(exdir, f))
for e in db['exercises']:
    rid = mapping.get(e['id'])
    if rid is None: continue
    for k in e.get('img', []):
        p = os.path.join(exdir, f"{e['id']}_{k}.webp")
        if os.path.exists(p): os.remove(p)
    phases = rep[rid]['images']['flat']
    keys = [KEY[ph] for ph in ('start', 'peak', 'main') if ph in phases]
    for ph, rel in phases.items():
        shutil.copyfile(os.path.join(REPDB, rel.removeprefix('images/')), os.path.join(exdir, f"{e['id']}_{KEY[ph]}.webp"))
    e['img'], e['pi'] = keys, keys[0]

with open(os.path.join(APP, 'db', 'exercises.json'), 'w') as f:
    f.write(json.dumps(db, ensure_ascii=False, separators=(',', ':')))
print(f'{len(mapping)} exercices avec images RepDB')
