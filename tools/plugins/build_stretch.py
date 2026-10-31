# usage : build_stretch.py catalog.json <module>/  (curl requis : vérifie chaque vidéo et télécharge les vignettes)
import json,sys,os,subprocess,concurrent.futures as cf
cat,out=sys.argv[1],sys.argv[2]
d=json.load(open(cat))
en,fr=d['tables'][0],d['tables'][1]
thumbs=os.path.join(out,'thumbs'); os.makedirs(thumbs,exist_ok=True)
VIDEO='https://d6xcsz8niy43r.cloudfront.net/{id}.m3u8'; POSTER='https://content.gowod.eu/thumbnails-v5/hd/{id}.jpg'
def check(e):
    i=e['id']
    code=subprocess.run(['curl','-s','-m','20','-I','-o','/dev/null','-w','%{http_code}',VIDEO.replace('{id}',i)],capture_output=True,text=True).stdout
    f=os.path.join(thumbs,i+'.jpg')
    if not os.path.exists(f) or os.path.getsize(f)==0:
        subprocess.run(['curl','-s','-f','-m','30','-o',f,'https://content.gowod.eu/thumbnails-v5/mini/'+i+'.jpg'])
    return code=='200', os.path.exists(f) and os.path.getsize(f)>0
ex=[e for e in d['exercises'] if 'Theragun' not in e['gears'] and e['type']!='Breathing']
with cf.ThreadPoolExecutor(6) as t: res=list(t.map(check,ex))
keep=[]
for e,(v,h) in zip(ex,res):
    if not v or e['id'] not in fr: print('skip',e['id'],v,h); continue
    keep.append({'id':e['id'],'name':e['name'],'type':e['type'],'area':e['area'],'zones':e['zones'],'gears':e['gears'],'uni':bool(e['unilateral']),
      'daily':bool(e['daily']),'pre':bool(e['prewod']),'post':bool(e['postwod']),'recup':e['recup'],'like':e['likability'],'backup':e.get('backup'),
      'min':(e['durations'] or [1])[0],'thumb':h,
      'fr':fr[e['id']]['s'],'frFeel':fr[e['id']]['f'],'en':en[e['id']]['s'],'enFeel':en[e['id']]['f']})
json.dump({'video':VIDEO,'poster':POSTER,'exercises':keep},open(os.path.join(out,'exercises.json'),'w'),ensure_ascii=False,separators=(',',':'))
print(len(ex),'->',len(keep),'videos',sum(r[0] for r in res),'thumbs',sum(r[1] for r in res))
