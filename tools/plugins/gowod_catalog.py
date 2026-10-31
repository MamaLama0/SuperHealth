import re,json,sys,ast

class _N(ast.NodeTransformer):
    def visit_Name(self,n):
        m={'null':None,'true':True,'false':False,'undefined':None}
        if n.id in m: return ast.Constant(m[n.id])
        raise ValueError(n.id)
def lit(x): return ast.literal_eval(_N().visit(ast.parse(x,mode='eval')).body)
hasm,out=sys.argv[1],sys.argv[2]
lines=open(hasm,errors='ignore').read().split('\n')
rx=re.compile(r"# Object: (\{'id': '[a-z0-9_]+', .*\})\s*$")
ex={}
cur=None;state=None
for i,l in enumerate(lines):
    m=rx.search(l)
    if m and "'name'" in l and "'unilateral'" in l:
        try: o=lit(m.group(1))
        except Exception as e: continue
        cur={k:o.get(k) for k in('id','name','unilateral','isScalable','prewod','postwod','daily','multi','backup','durations')}
        cur.update(type=None,area=None,zones=[],gears=[],recup=None,likability=None,body=[],_line=i)
        if cur['id'] not in ex: ex[cur['id']]=cur
        else: cur=None
        state=None
        continue
    if cur is None: continue
    if 'NewFunction' in l or 'Ret>' in l: cur=None; continue
    ma=re.search(r"# Array: (\[[0-9, ]+\])\s*$",l)
    if ma and cur.get('durations') is None: cur['durations']=lit(ma.group(1))
    m=re.search(r"String: '([A-Za-z0-9_]+)' \(Identifier\)",l)
    if not m:
        continue
    w=m.group(1)
    if w in('Type','Area','Zone','Recup','Likability','BodyZone','Gear','Gears','Equipment','Durations'): state=w; continue
    if state=='Type': cur['type']=w;state=None
    elif state=='Area': cur['area']=w;state=None
    elif state=='Zone': cur['zones'].append(w);state=None
    elif state=='Recup': cur['recup']=w;state=None
    elif state=='Likability': cur['likability']=w;state=None
    elif state=='BodyZone': cur['body'].append(w);state=None
    elif state in('Gear','Gears','Equipment'): cur['gears'].append(w);state=None
# instruction tables
keys_rx=re.compile(r"# Object: (\{'cossack_squat': .*\})\s*$")
step_rx=re.compile(r"# Object: (\{1: .*\})\s*$")
tables=[]
for i,l in enumerate(lines):
    m=keys_rx.search(l)
    if m and 'UInt32: 81621' in l:
        keys=list(lit(m.group(1)).keys())
        t={};j=i+1;pending=None;last=-1
        while j<len(lines) and j<i+1400:
            s_=lines[j]
            ms=step_rx.search(s_)
            if ms:
                try: pending=lit(ms.group(1))
                except Exception: pending=None
            mp=re.search(r"PutOwnBySlotIdx(?:Long)?>: <Reg8: 1, Reg8: 3, (?:UInt8|UInt16|UInt32): (\d+)>",s_)
            if mp:
                idx=int(mp.group(1))
                if idx<last: break
                last=idx
                if pending is not None and idx<len(keys):
                    t[keys[idx]]={'s':[pending[k] for k in sorted(k for k in pending if isinstance(k,int))],'f':pending.get('feeling')}
                pending=None
            j+=1
        tables.append((i,keys,t))
print(len(ex),'exercises;',[ (i,len(t)) for i,k,t in tables])
for i,k,t in tables:
    print(i,len(k),list(t.items())[:1])
json.dump({'exercises':list(ex.values()),'tables':[t for _,_,t in tables]},open(out,'w'),ensure_ascii=False,indent=0)
