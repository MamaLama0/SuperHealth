#!/usr/bin/env python3
"""Build a small real OSM route pack. No raster tile scraping. Retain source responses."""
import json, math, time, urllib.request, urllib.parse, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
OUT=ROOT/'SuperHealth-modules/src/hiking'; RAW=ROOT/'Reference/Rando/analysis/osm'; RAW.mkdir(parents=True,exist_ok=True)
def query(q,name):
 p=RAW/(name+'.json')
 if p.exists(): return json.loads(p.read_text())
 if '--offline' in sys.argv: raise RuntimeError('No saved response')
 url='https://overpass-api.de/api/interpreter?'+urllib.parse.urlencode({'data':q})
 with urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'SuperHealth/3.1 (offline hiking data pack)'}),timeout=70) as r: d=r.read()
 p.write_bytes(d); return json.loads(d)
def dist(a,b):
 r=math.pi/180; x=(b['lon']-a['lon'])*math.cos((a['lat']+b['lat'])*r/2);y=b['lat']-a['lat'];return math.hypot(x,y)*r*6371000
def stitch(members):
 parts=[m['geometry'] for m in members if m.get('type')=='way' and len(m.get('geometry',[]))>1 and m.get('role','') not in ('alternative','excursion')]
 if not parts:return []
 line=parts.pop(0)
 while parts:
  choices=[(dist(a,b),i,side,reverse) for i,p in enumerate(parts) for side,a in enumerate((line[-1],line[0])) for reverse,b in enumerate((p[0],p[-1]))]
  gap,i,side,reverse=min(choices)
  if gap>15:return []
  p=parts.pop(i)
  if side==0:line+=p[::-1] if reverse else p
  else:line=(p if reverse else p[::-1])+line
 return [p for i,p in enumerate(line) if i==0 or dist(line[i-1],p)>0.2]
routes=[]
# Small local paths from several countries. Incomplete relations are excluded.
areas=[('Alpes · France',45.88,6.87,0.09),('Lac Léman · Suisse',46.45,6.85,0.12),('Dolomites · Italie',46.56,11.75,0.08),('Peak District · Royaume-Uni',53.34,-1.78,0.09),('Forêt-Noire · Allemagne',48.15,8.18,0.09),('Catalogne · Espagne',42.02,2.47,0.10)]
for idx,(region,lat,lon,radius) in enumerate(areas):
 try:
  q=f'[out:json][timeout:45];rel[route=hiking][name][network~"lwn|rwn"]({lat-radius},{lon-radius},{lat+radius},{lon+radius});out geom 35;'
  data=query(q,f'routes-{idx}')
  candidates=[]
  for e in data.get('elements',[]):
   t=e.get('tags',{}); points=stitch(e.get('members',[]))
   if len(points)<8:continue
   km=sum(dist(a,b) for a,b in zip(points,points[1:]))/1000
   if not 1<km<24 or t.get('fixme'):continue
   candidates.append((e,points,km))
  candidates.sort(key=lambda x: abs(x[2]-8))
  for e,points,km in candidates[:2]:
   t=e['tags'];pad=.008
   bbox=(min(p['lat'] for p in points)-pad,min(p['lon'] for p in points)-pad,max(p['lat'] for p in points)+pad,max(p['lon'] for p in points)+pad)
   q='[out:json][timeout:45];(way[highway~"path|footway|track|steps|residential|tertiary|secondary"](%s);way[natural~"water|wood"](%s);way[landuse=forest](%s);way[waterway](%s););out geom 350;'%(((','.join(map(str,bbox)),)*4))
   try: bg=query(q,f'map-{e["id"]}')
   except Exception:
    # Nearby hiking relations supply authentic trail linework even when the area query is unavailable.
    bg={'elements':[{'tags':{'highway':'path'},'geometry':m['geometry']} for other in data.get('elements',[]) for m in other.get('members',[]) if m.get('type')=='way' and m.get('geometry') and any(bbox[0]<=p['lat']<=bbox[2] and bbox[1]<=p['lon']<=bbox[3] for p in m['geometry'])][:500]}
   features=[]
   for w in bg.get('elements',[]):
    tags=w.get('tags',{});geom=w.get('geometry',[])
    if len(geom)<2:continue
    kind='water' if tags.get('natural')=='water' or 'waterway' in tags else 'forest' if tags.get('natural')=='wood' or tags.get('landuse')=='forest' else 'path' if tags.get('highway') in ('path','footway','track','steps') else 'road'
    features.append({'kind':kind,'points':[{'lat':p['lat'],'lon':p['lon']} for p in geom]})
   ascent=t.get('ascent','').replace(' m','')
   route={'id':f'osm-{e["id"]}','name':t['name'],'region':region,'source':f'https://www.openstreetmap.org/relation/{e["id"]}','points':[{'lat':p['lat'],'lon':p['lon']} for p in points], 'features':features,'description':t.get('description',''),'loop':t.get('roundtrip')=='yes' or dist(points[0],points[-1])<80}
   if ascent.isdigit():route['ascent']=float(ascent)
   routes.append(route);print(region,t['name'],round(km,1),len(points),len(features),flush=True)
 except Exception as e:print('Unavailable',region,str(e),flush=True)
OUT.mkdir(parents=True,exist_ok=True)
(OUT/'hiking.json').write_text(json.dumps({'routes':routes},ensure_ascii=False,separators=(',',':'))+'\n')
(OUT/'credits.txt').write_text('Cartographie et itinéraires : © les contributeurs OpenStreetMap, ODbL 1.0.\nhttps://www.openstreetmap.org/copyright\nExtraction : 2026-11-03. Données non vérifiées sur le terrain.\nVisuel décoratif : asset Visorando fourni par le titulaire des droits.\n')
print('Built',len(routes),'routes',flush=True)
