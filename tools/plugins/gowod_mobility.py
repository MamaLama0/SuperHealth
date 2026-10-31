# usage : gowod_mobility.py dis.hasm strings.json mobility.json : tests, points, images et moyennes du test de mobilité (lecture statique du bundle)
import re,ast,json,sys
class N(ast.NodeTransformer):
    def visit_Name(self,n): return ast.Constant({'null':None,'true':True,'false':False}.get(n.id))
lit=lambda x: ast.literal_eval(N().visit(ast.parse(x,mode='eval')).body)
ls=open(sys.argv[1],errors='ignore').read().split('\n')
s=json.load(open(sys.argv[2]))
ids={'TheCompass':'the_compass','TheFreezeTest':'the_freeze_test','TheWizard':'the_wizard','TheWallSquat':'the_wall_squat','TheLotus':'the_lotus','TheShield':'the_shield','StandingForwardBend':'standing_forward_bend_test','KneeToWall':'knee_to_wall'}
tests=[];cur=None
start=next(i for i,l in enumerate(ls) if "'archetype': null" in l)
for l in ls[start-1:start+800]:
    m=re.search(r"# Object: (\{'id': null, 'archetype'.*\})\s*$",l)
    if m: cur=lit(m.group(1));cur.update(id=None,archetype=None,pts=[]);tests.append(cur);continue
    if not cur: continue
    m=re.search(r"# Object: \{'points': ([0-9.]+)\}",l)
    if m: cur['pts'].append(float(m.group(1)));continue
    m=re.search(r"String: '([A-Za-z]+)' \(Identifier\)",l)
    if m and m.group(1) not in('MobilityTestV4','PriorityName','MobilityTestNameV4'):
        if cur['id'] is None: cur['id']=m.group(1)
        elif cur['archetype'] is None: cur['archetype']=m.group(1)
# consignes : tables d'objets {1:..,'anatomy':..}, clés dans l'ordre de l'objet parent, en et fr
def tables():
    out=[]
    for i,l in enumerate(ls):
        m=re.search(r"# Object: (\{'the_compass': null, 'the_freeze_test': null, 'the_wizard': null, 'vsit_test'.*\})\s*$",l)
        if m:
            keys=list(lit(m.group(1)).keys());t={};k=0
            for x in ls[i+1:i+40]:
                mm=re.search(r"# Object: (\{1: .*\})\s*$",x)
                if mm and k<len(keys):
                    try: t[keys[k]]=lit(mm.group(1))
                    except Exception: pass
                    k+=1
            out.append(t)
    return out
T=tables();en,fr=T[0],T[1]
names=[x for x in s if re.fullmatch(r'[a-z_]+/[a-z_]+_\d+',x)]
imgs={}
for n in names:
    a,b=n.split('/');side='left' if '_left_' in b else 'right' if '_right_' in b else 'both'
    idx=int(b.rsplit('_',1)[1]);imgs.setdefault(a,{}).setdefault(side,{})[idx]=n
out=[]
for t in tests:
    i=ids.get(t['id'])
    if not i: continue
    d=imgs.get(i,{})
    f=fr.get(i,{});e=en.get(i,{})
    steps=lambda o:[o[k] for k in sorted(k for k in o if isinstance(k,int))]
    out.append(dict(id=i,focus=t['archetype'],coeff=t['coeff'],max=t['maxPoints'],uni=bool(t.get('unilateral')),pts=t['pts'],
      images={k:[v[j] for j in sorted(v)] for k,v in d.items()},fr=steps(f),en=steps(e),frAnat=f.get('anatomy'),enAnat=e.get('anatomy')))
    print(i,t['archetype'],len(t['pts']),{k:len(v) for k,v in d.items()},(f.get('anatomy')))
# moyennes
avg={}
st=next(i for i,l in enumerate(ls) if "Object: {'male': null, 'female': null, 'global': null}" in l)
print('avg at',st, ls[st][:100])
seq=[]
for l in ls[st:st+900]:
    m=re.search(r"# Object: \{'byFocus': null, 'score': ([0-9.]+)\}",l)
    if m: seq.append(['s',float(m.group(1))]);continue
    m=re.search(r"# Object: \{'score': ([0-9.]+)\}",l)
    if m: seq.append(['f',float(m.group(1))])
print(len(seq),seq[:8])
json.dump({'image':'https://content.gowod.eu/in_app_v5/mobitest_responses/{name}.jpg','tests':out,'averageSeq':seq},open(sys.argv[3],'w'),ensure_ascii=False,separators=(',',':'))
