"""Authored, deterministic Blender equipment. No generated images or imported art.
Run: blender -b -t 6 --python art/arsenal-v4/build.py -- --ids ID,ID --samples 40
Every mesh, material and camera is reproducible; .blend originals remain editable.
"""
import bpy, math, json, sys, argparse
from pathlib import Path
from mathutils import Vector
from bpy_extras.object_utils import world_to_camera_view
P=Path(__file__).resolve().parent
parser=argparse.ArgumentParser();parser.add_argument('--ids',default='');parser.add_argument('--samples',type=int,default=40);parser.add_argument('--size',type=int,default=720);parser.add_argument('--skip-existing',action='store_true')
a=parser.parse_args(sys.argv[sys.argv.index('--')+1:] if '--' in sys.argv else [])
PI=math.pi
METALS={'iron':'#8795a3','steel':'#718593','darksteel':'#4a5666','silver':'#cfdee2','pewter':'#9396a2','bronze':'#b58746','brass':'#c1a064','copper':'#b5714d','gold':'#c8a35b'}
def rgb(h):
 return tuple(((int(h[i:i+2],16)/255+.055)/1.055)**2.4 if int(h[i:i+2],16)/255>.04045 else int(h[i:i+2],16)/255/12.92 for i in (1,3,5))
def mat(name,color,metal=0,rough=.45,texture=0):
 m=bpy.data.materials.new(name);m.diffuse_color=(*rgb(color),1);m.use_nodes=True
 p=m.node_tree.nodes.get('Principled BSDF');p.inputs['Base Color'].default_value=(*rgb(color),1);p.inputs['Metallic'].default_value=metal;p.inputs['Roughness'].default_value=rough
 if texture:
  n=m.node_tree.nodes.new('ShaderNodeTexNoise');n.inputs['Scale'].default_value=95 if texture==1 else 18;n.inputs['Detail'].default_value=2
  bump=m.node_tree.nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.10;bump.inputs['Distance'].default_value=.0015 if texture==1 else .006
  m.node_tree.links.new(n.outputs['Fac'],bump.inputs['Height']);m.node_tree.links.new(bump.outputs['Normal'],p.inputs['Normal'])
 return m
def finish(o,m,bevel=0,smooth=False):
 if m:o.data.materials.append(m)
 if bevel:
  mod=o.modifiers.new('Hand-finished edges','BEVEL');mod.width=bevel;mod.segments=3
  mod=o.modifiers.new('Weighted surface normals','WEIGHTED_NORMAL')
 if smooth:
  for f in o.data.polygons:f.use_smooth=True
 return o
def mesh(name,v,f,m,bevel=0,smooth=False):
 d=bpy.data.meshes.new(name);d.from_pydata(v,[],f);d.update();o=bpy.data.objects.new(name,d);bpy.context.collection.objects.link(o);return finish(o,m,bevel,smooth)
def cube(name,loc,scale,m,bevel=.025):
 bpy.ops.mesh.primitive_cube_add(size=1,location=loc);o=bpy.context.object;o.name=name;o.dimensions=scale;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);return finish(o,m,bevel)
def uv(name,loc,scale,m):
 bpy.ops.mesh.primitive_uv_sphere_add(segments=24,ring_count=12,location=loc);o=bpy.context.object;o.name=name;o.scale=scale;return finish(o,m,0,True)
def ico(name,loc,scale,m):
 bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=1,location=loc);o=bpy.context.object;o.name=name;o.scale=scale;return finish(o,m)
def cylinder(name,A,B,r,m,r2=None,verts=24):
 A=Vector(A);B=Vector(B);D=B-A
 bpy.ops.mesh.primitive_cone_add(vertices=verts,radius1=r,radius2=r if r2 is None else r2,depth=D.length,location=(A+B)/2)
 o=bpy.context.object;o.name=name;o.rotation_euler=D.to_track_quat('Z','Y').to_euler();return finish(o,m,.005,True)
def line(name,pts,r,m,closed=False):
 d=bpy.data.curves.new(name,'CURVE');d.dimensions='3D';d.resolution_u=16;d.bevel_depth=r;d.bevel_resolution=3;s=d.splines.new('POLY');s.points.add(len(pts)-1)
 for p,co in zip(s.points,pts):p.co=(*co,1)
 s.use_cyclic_u=closed;o=bpy.data.objects.new(name,d);bpy.context.collection.objects.link(o);o.data.materials.append(m);return o
def ring(name,center,r,wire,m,axis='Y',n=48):
 x,y,z=center
 pts=[(x+r*math.cos(i*2*PI/n),y,z+r*math.sin(i*2*PI/n)) if axis=='Y' else (x+r*math.cos(i*2*PI/n),y+r*math.sin(i*2*PI/n),z) for i in range(n)]
 o=line(name,[(px-x,py-y,pz-z) for px,py,pz in pts],wire,m,True);o.location=(x,y,z);return o
def plate(name,points,depth,m,y=0,bevel=.018):
 # Outline in XZ; front surface points toward camera (-Y).
 v=[(x,y-depth/2,z) for x,z in points]+[(x,y+depth/2,z) for x,z in points];n=len(points)
 return mesh(name,v,[tuple(reversed(range(n))),tuple(range(n,2*n))]+[(i,(i+1)%n,(i+1)%n+n,i+n) for i in range(n)],m,bevel)
def loft(name,profiles,m,n=40,cap=True):
 # z, x-radius, y-radius, y-center; cloth and leather have purposeful profiles.
 v=[(rx*math.cos(i*2*PI/n),cy+ry*math.sin(i*2*PI/n),z) for z,rx,ry,cy in profiles for i in range(n)]
 f=[(j*n+i,j*n+(i+1)%n,(j+1)*n+(i+1)%n,(j+1)*n+i) for j in range(len(profiles)-1) for i in range(n)]
 if cap:f+=[tuple(reversed(range(n))),tuple((len(profiles)-1)*n+i for i in range(n))]
 return mesh(name,v,f,m,.008,True)
def ellipse(name,z,rx,ry,cy,r,m,cx=0):return line(name,[(cx+rx*math.cos(i*PI/24),cy+ry*math.sin(i*PI/24),z) for i in range(48)],r,m,True)
def move_since(before,dx=0,dy=0,dz=0):
 for o in set(bpy.context.scene.objects)-before:o.location+=Vector((dx,dy,dz))
def studs(points,m,r=.018):
 for x,y,z in points:uv('Rivet', (x,y,z),(r,r*.7,r),m)
def leaf(x,y,z,s,m,flip=1):
 pts=[(0,0),(.28,.22),(.40,.55),(.12,1),(-.12,.55),(-.15,.22)]
 return plate('Repoussé leaf',[(x+flip*px*s,z+pz*s) for px,pz in pts],.025,m,y,.006)
def emblem(x,y,z,s,kind,m,g):
 if kind in ('sun','star'):
  ring('Solar setting',(x,y,z),s*.32,.015,m)
  for i in range(8 if kind=='sun' else 4):
   t=i*2*PI/(8 if kind=='sun' else 4);cylinder('Ray',(x+math.cos(t)*s*.43,y,z+math.sin(t)*s*.43),(x+math.cos(t)*s*.73,y,z+math.sin(t)*s*.73),.014,m)
  ico('Inset stone',(x,y-.025,z),(s*.20,.06,s*.20),g)
 elif kind in ('feather','leaf','antler','thorn','rose'):
  line('Branch',[(x,y,z-s*.5),(x,y,z+s*.6)],.018,m)
  for side in [-1,1]:
   for j in range(3):
    if kind=='antler':line('Antler tine',[(x,y,z-s*.35+j*s*.3),(x+side*s*.35,y,z-s*.1+j*s*.3),(x+side*s*.43,y,z+s*.1+j*s*.3)],.022,m)
    else:leaf(x+side*s*.03,y,z-s*.4+j*s*.27,s*.5,m,side)
  if kind=='rose':
   for i in range(7):uv('Rose petal',(x+math.cos(i*2*PI/7)*s*.18,y-.025,z+s*.3+math.sin(i*2*PI/7)*s*.18),(s*.12,.035,s*.16),m)
   ico('Rose heart',(x,y-.06,z+s*.3),(s*.08,.04,s*.08),g)
 elif kind=='moon':
  line('Crescent',[(x+math.cos(t)*s*.45,y,z+math.sin(t)*s*.45) for t in [PI*.25+i*PI*1.5/32 for i in range(33)]],.035,m)
  ico('Moonstone',(x+s*.18,y-.03,z),(s*.16,.06,s*.22),g)
 elif kind=='dragon':
  for side in [-1,1]:plate('Dragon wing',[(x,z),(x+side*s*.65,z+s*.5),(x+side*s*.45,z-s*.15),(x+side*s*.28,z),(x+side*s*.1,z-s*.5)],.035,m,y)
  ico('Dragon eye',(x,y-.06,z+s*.1),(s*.12,.05,s*.20),g)
 elif kind=='lyre':
  line('Lyre',[(x-s*.35,y,z+s*.5),(x-s*.4,y,z-s*.2),(x,y,z-s*.5),(x+s*.4,y,z-s*.2),(x+s*.35,y,z+s*.5)],.025,m)
  for d in [-.2,0,.2]:line('Lyre string',[(x+s*d,y,z-s*.25),(x+s*d,y,z+s*.42)],.006,m)
 else:
  plate('Runic inlay',[(x,z+s*.45),(x+s*.3,z),(x,z-s*.45),(x-s*.3,z)],.018,m,y)
  ico('Runic center',(x,y-.025,z),(s*.13,.045,s*.2),g)

def blade(z0,z1,w,m,style='sword'):
 # Raised central ridge, actual bevels; sharp edges remain straight and symmetric.
 levels=[(z0,w*.76),(z0+.15,w),(z1-.28,w*.75),(z1,0)]
 v=[]
 for z,r in levels:v += [(-r,0,z),(0,-.045,z),(r,0,z),(0,.045,z)]
 f=[]
 for j in range(3):
  for i in range(4):f.append((j*4+i,j*4+(i+1)%4,(j+1)*4+(i+1)%4,(j+1)*4+i))
 f.append((3,2,1,0));return mesh('Forged blade',v,f,m)
def handle(z0,z1,r,leather,trim):
 cylinder('Leather grip',(0,0,z0),(0,0,z1),r,leather)
 for i in range(7):ellipse('Grip winding',z0+(z1-z0)*(i+.5)/7,r+.002,r+.002,0,.009,trim)

def weapon(c,M):
 metal,trim,cloth,leather,wood,dark,gem=M;f=c['form'];tier=c['tier'];motif=c['motif']
 if f in ('sword','greatsword'):
  blade(-.35,1.45,.105 if f=='sword' else .14,metal)
  plate('Swept crossguard',[(-.4,-.44),(-.39,-.34),(-.12,-.27),(0,-.32),(.12,-.27),(.39,-.34),(.4,-.44),(.13,-.39),(-.13,-.39)],.10,trim)
  handle(-1.05,-.43,.064,leather,trim);uv('Wheel pommel',(0,0,-1.13),(.10,.065,.10),trim)
  if tier>=3:
   emblem(0,-.075,-.34,.18,motif,trim,gem)
   for side in [-1,1]:
    for j in range(4):
     pts=[(side*(.08+j*.055+.017*math.sin(k*PI/12)),-.057,-.35+.02*math.cos(k*PI/12)) for k in range(25)]
     line('Guard chased scroll',pts,.0035,metal)
    line('Fuller inlay',[(side*.035,-.046,-.12),(side*.035,-.046,.95),(0,-.046,1.20)],.003,trim)
   uv('Pommel stone',(0,-.06,-1.13),(.046,.018,.046),gem)
   for z in [.05,.25,.45,.65,.85]:plate('Blade inlay',[(-.018,z),(.018,z+.04),(.018,z+.10),(-.018,z+.06)],.005,trim,-.046,.002)
 elif f in ('axe','halberd'):
  cylinder('Ash haft',(0,0,-1.2),(0,0,1.16),.048,wood)
  handle(-1.15,-.65,.055,leather,trim)
  points=[(.02,.62),(.24,.68),(.51,.60),(.60,.75),(.57,1.08),(.46,1.25),(.20,1.04),(.02,1.02)]
  plate('Forged axe head',points,.10,metal);line('Sharpened edge',[(x,-.056,z) for x,z in points[2:6]],.012,trim)
  cylinder('Head socket',(0,0,.55),(0,0,1.12),.085,metal)
  if f=='halberd':
   blade(1.05,1.63,.075,metal);plate('Rear beak',[(-.04,.82),(-.38,.68),(-.29,.93),(-.04,1.06)],.085,metal)
   emblem(.29,-.075,.89,.24,motif,trim,gem)
  for z in [.53,1.10]:ellipse('Socket collar',z,.088,.088,0,.018,trim)
 elif f in ('mace','hammer','flail'):
  cylinder('Wooden haft',(0,0,-1.10),(0,0,.8),.055,wood);handle(-1.08,-.48,.066,leather,trim)
  uv('Butt cap',(0,0,-1.11),(.08,.08,.06),trim)
  if f=='hammer':
   cube('Hammer head',(0,0,.86),(.73,.29,.33),metal,.035)
   for x in [-.37,.37]:cube('Striking face',(x,0,.86),(.07,.32,.37),trim,.015)
   emblem(0,-.16,.86,.22,motif,trim,gem)
  elif f=='mace':
   cylinder('Mace core',(0,0,.55),(0,0,1.02),.09,metal)
   for i in range(6):
    o=plate('Forged flange',[(0,.48),(.20,.60),(.24,.86),(.15,1.08),(0,1.14)],.035,metal);o.rotation_euler.z=i*PI/3
   uv('Crown',(0,0,1.10),(.09,.09,.07),trim)
  else:
   for i in range(9):
    o=ring('Chain link',(.035*i,0,.78+.07*i),.055,.014,metal);o.rotation_euler.z=(i%2)*PI/2
   ico('Flail head',(.34,0,1.45),(.23,.23,.23),metal)
   for i in range(6):
    t=i*PI/3;cylinder('Flail stud',(.34+math.cos(t)*.17,-.03,1.45+math.sin(t)*.17),(.34+math.cos(t)*.32,-.03,1.45+math.sin(t)*.32),.045,trim,0)
 elif f in ('shortbow','longbow','rosebow','antlerbow'):
  h={'shortbow':1.05,'longbow':1.48,'rosebow':1.37,'antlerbow':1.43}[f]
  pts=[]
  for i in range(41):
   z=-h+2*h*i/40;x=.08+.28*math.sin(PI*i/40)**1.3-.09*math.sin(3*PI*i/40);pts.append((x,0,z))
  line('Carved bow stave',pts,.041 if tier<3 else .05,wood)
  line('Taut bowstring',[(pts[0][0],0,-h),(-.15,0,0),(pts[-1][0],0,h)],.007,cloth)
  cylinder('Wrapped riser',(.28,0,-.15),(.28,0,.15),.061,leather)
  if tier>=2:
   for sign in [-1,1]:
    line('Silver limb inlay',[(x,-.041,z) for x,y,z in pts if .25<abs(z)<h*.9 and z*sign>0],.009,trim)
    for j in range(4):leaf(.17,-.055,sign*(.4+j*.18),.18,trim,sign)
   emblem(.29,-.07,0,.20,motif,trim,gem)
  if f=='antlerbow':
   for sign in [-1,1]:
    for j in range(3):line('Carved antler branch',[(.17,0,sign*(.65+j*.20)),(.43+j*.035,0,sign*(.85+j*.16)),(.49+j*.035,0,sign*(1.01+j*.16))],.021,trim)
 elif f=='crossbow':
  cube('Walnut stock',(0,.02,-.1),(.19,.18,1.8),wood,.055)
  pts=[(-.85,0,.55),(-.64,-.09,.67),(-.30,-.15,.68),(0,-.17,.62),(.30,-.15,.68),(.64,-.09,.67),(.85,0,.55)]
  line('Steel bow prod',pts,.045,metal);line('Crossbow string',[pts[0],(0,-.16,-.18),pts[-1]],.009,cloth)
  cylinder('Quarrel',(0,-.19,-.3),(0,-.19,.98),.012,wood);cylinder('Quarrel tip',(0,-.19,.98),(0,-.19,1.13),.035,metal,0)
  ring('Foot stirrup',(0,0,1.03),.17,.025,metal);cube('Trigger housing',(0,.10,-.25),(.24,.21,.20),metal)
  line('Trigger',[(0,.16,-.27),(0,.27,-.45),(0,.20,-.53)],.018,metal)
  for z in [-.72,-.48,.12,.38]:cube('Stock band',(0,-.005,z),(.215,.195,.045),trim,.01)
 elif f in ('wand','scepter','sunstaff','astralstaff'):
  tall=f in ('sunstaff','astralstaff');low=-1.40 if tall else -.80;head=.78 if tall else .54
  cylinder('Turned shaft',(0,0,low),(0,0,head),.040 if f=='wand' else .055,wood,.032)
  for z in [low+.05,low+.17,head-.25,head-.13]:ellipse('Fitted collar',z,.06,.06,0,.018,trim)
  if f=='astralstaff':
   for j in range(3):
    o=ring('Armillary ring',(0,0,1.12),.30,.020,trim);o.rotation_euler.x=j*PI/3;o.rotation_euler.z=j*PI/5
   uv('Celestial sphere',(0,0,1.12),(.16,.16,.16),gem)
   for z in [-1.17,-.70,-.12,.45]:
    cylinder('Engraved silver ferrule',(0,0,z),(0,0,z+.13),.061,trim)
    for i in range(6):
     t=i*PI/3;ico('Ferrule inset',(math.cos(t)*.063,math.sin(t)*.063,z+.065),(.012,.012,.027),gem)
   for x in [-.30,.30]:uv('Armillary bearing',(x,0,1.12),(.035,.035,.035),trim)
   for side in [-1,1]:leaf(side*.02,-.01,.64,.30,trim,side)
  elif f=='sunstaff':
   ring('Solar halo',(0,0,1.02),.28,.027,trim)
   for i in range(12):
    t=i*PI/6;cylinder('Sun ray',(math.cos(t)*.29,0,1.02+math.sin(t)*.29),(math.cos(t)*.42,0,1.02+math.sin(t)*.42),.019,trim,.003)
   ico('Opal sun',(0,0,1.02),(.15,.11,.18),gem)
  else:
   ico('Crystal focus',(0,0,head+.17),(.12,.10,.24),gem)
   for side in [-1,1]:line('Crystal prong',[(side*.04,0,head-.04),(side*.13,0,head+.06),(side*.11,0,head+.23)],.018,trim)
  if tier>=2:
   for j in range(7):emblem(0,-.058,low+.4+j*.12,.04,'rune',trim,gem)
 elif f=='orb':
  uv('Polished crystal',(0,0,.22),(.47,.47,.47),gem)
  for side in [-1,1]:line('Orb cradle',[(side*.14,0,-.55),(side*.43,0,-.05),(side*.45,0,.20)],.046,trim)
  cylinder('Cradle stem',(0,0,-.70),(0,0,-.34),.10,metal)
  cylinder('Cradle foot',(0,0,-.76),(0,0,-.69),.30,trim)
  o=ring('Equatorial band',(0,0,.22),.49,.025,trim,axis='Z');o.rotation_euler.x=.25
 elif f in ('bell','censer','lantern'):
  if f=='bell':
   loft('Cast bronze bell',[(-.55,.43,.43,0),(-.48,.45,.45,0),(-.35,.34,.34,0),(.02,.23,.23,0),(.30,.16,.16,0),(.34,.06,.06,0)],metal,cap=False)
   cylinder('Bell grip',(0,0,.32),(0,0,.70),.055,wood);uv('Clapper',(0,-.03,-.50),(.10,.10,.12),trim)
   ellipse('Bell rim',-.52,.44,.44,0,.025,trim)
  else:
   if f=='censer':
    loft('Censer bowl',[(-.62,.06,.06,0),(-.49,.20,.20,0),(-.23,.36,.36,0),(-.15,.38,.38,0),(.05,.29,.29,0),(.20,.18,.18,0),(.31,.06,.06,0)],metal)
    for z,r in [(-.18,.38),(-.45,.23),(.04,.29)]:ellipse('Gilded bowl band',z,r,r,0,.023,trim)
    for i in range(10):
     t=i*PI/5;ico('Censer opening',(math.cos(t)*.27,math.sin(t)*.27,.02),(.037,.037,.08),dark)
    for j in range(3):
     t=j*2*PI/3
     for i in range(11):
      k=i/10;x=math.cos(t)*.31*(1-k);y=math.sin(t)*.31*(1-k);z=.0+k*.92;o=ring('Suspension link',(x,y,z),.038,.010,trim);o.rotation_euler.z=t+(i%2)*PI/2
   else:
    cube('Lantern glass',(0,0,-.18),(.48,.35,.65),gem,.035)
    for x in [-.27,.27]:
     for y in [-.20,.20]:cylinder('Lantern corner',(x,y,-.56),(x,y,.22),.026,metal)
    cube('Lantern foot',(0,0,-.57),(.61,.47,.12),metal)
    loft('Peaked lantern roof',[(.21,.40,.30,0),(.29,.40,.30,0),(.56,.05,.04,0)],metal,n=4)
    emblem(0,-.225,-.17,.26,motif,trim,gem)
   ring('Suspension handle',(0,0,1.03 if f=='censer' else .72),.12,.022,trim)
   if tier>=3:
    for side in [-1,1]:leaf(side*.09,-.30,-.46,.27,trim,side)
 elif f=='lute':
  uv('Pear-shaped lute body',(0,0,-.32),(.38,.21,.55),wood)
  # Thin soundboard on the front with a dark recessed rosette.
  uv('Soundboard',(0,-.176,-.31),(.34,.035,.49),cloth)
  ring('Soundhole rosette',(0,-.218,-.18),.105,.016,trim);uv('Soundhole',(0,-.216,-.18),(.088,.007,.088),dark)
  cube('Lute neck',(0,0,.53),(.12,.12,1.04),wood,.015);cube('Fingerboard',(0,-.075,.54),(.115,.02,1.03),dark,.005)
  cube('Bridge',(0,-.216,-.56),(.24,.035,.055),wood,.01)
  cube('Pegbox',(0,.015,1.12),(.15,.15,.26),wood,.025)
  for i in range(6):
   x=(i-2.5)*.014;line('String',[(x,-.24,-.55),(x,-.10,1.21)],.0035,trim)
  for i in range(10):cube('Fret',(0,-.091,.10+i*.085),(.12,.012,.009),trim,.001)
  for side in [-1,1]:
   for j in range(3):cylinder('Tuning peg',(side*.07,0,1.02+j*.08),(side*.16,0,1.02+j*.08),.022,trim)
  emblem(0,-.219,-.40,.12,'lyre',trim,gem)
 else:raise ValueError(f)

def book(c,M,f):
 metal,trim,cloth,leather,wood,dark,gem=M
 cube('Bound pages',(0,0,0),(.76,.18,1.05),mat('Vellum','#d9c6a2',0,.8),.025)
 for y in [-.12,.12]:cube('Leather cover',(0,y,0),(.84,.055,1.13),leather,.027)
 cube('Rounded spine',(-.40,0,0),(.10,.27,1.11),cloth,.045)
 for z in [-.37,-.18,.18,.37]:cube('Spine rib',(-.42,0,z),(.12,.28,.025),trim,.01)
 for side in [-1,1]:
  for z in [-.49,.49]:plate('Book corner',[(side*.36,z),(side*.20,z),(side*.36,z-math.copysign(.16,z))],.025,trim,-.16)
 for z in [-.25,.25]:cube('Book clasp',(.39,-.08,z),(.13,.20,.07),trim,.01)
 emblem(0,-.165,0,.45,c['motif'],trim,gem)
 if f=='chainedbook':
  for i in range(15):
   x=-.39+i*.055;z=-.28-.12*math.sin(i*PI/14);o=ring('Binding chain',(x,-.20,z),.041,.010,trim);o.rotation_euler.z=i%2*PI/2
 if f=='musicbook':
  for z in [-.32,-.28,-.24]:line('Score inlay',[(-.22,-.16,z),(.22,-.16,z)],.005,trim)

def guard(c,M):
 metal,trim,cloth,leather,wood,dark,gem=M;f=c['form'];tier=c['tier'];motif=c['motif']
 if f in ('book','chainedbook','musicbook'):book(c,M,f);return
 if f=='bracers':
  for side in [-1,1]:
   before=set(bpy.context.scene.objects);loft('Leather vambrace',[(-.52,.16,.13,0),(.10,.20,.16,0),(.48,.23,.17,0)],leather,cap=False)
   for z,rx,ry in [(-.40,.17,.14),(.34,.22,.17)]:ellipse('Bracer strap',z,rx,ry,0,.035,cloth);cube('Bracer buckle',(.1,-ry-.02,z),(.11,.035,.10),trim,.008)
   plate('Bracer plate',[(-.12,-.38),(.12,-.38),(.16,.31),(0,.43),(-.16,.31)],.025,metal,-.17)
   move_since(before,side*.30,side*.08,side*.07)
  return
 if f in ('medallion','prism','reliquary','wingrelic','triptych','astrolabe','mirror'):
  if f=='prism':
   ico('Faceted prism',(0,0,0),(.36,.25,.70),gem)
   for sign in [-1,1]:
    plate('Prism mounting',[(-.27,sign*.30),(0,sign*.65),(.27,sign*.30),(0,sign*.44)],.08,metal)
   emblem(0,-.26,0,.22,motif,trim,gem)
  elif f=='astrolabe':
   for r in [.60,.49,.30]:ring('Astronomical circle',(0,0,0),r,.022,trim)
   for i in range(36):
    t=i*PI/18;line('Degree index',[(math.cos(t)*.53,-.01,math.sin(t)*.53),(math.cos(t)*(.59 if i%3==0 else .56),-.01,math.sin(t)*(.59 if i%3==0 else .56))],.006,metal)
   line('Alidade',[(-.45,-.06,-.35),(.45,-.06,.35)],.025,metal);ico('Celestial center',(0,-.07,0),(.13,.08,.13),gem)
   ring('Suspension loop',(0,0,.72),.09,.020,trim)
  elif f=='mirror':
   uv('Obsidian mirror',(0,0,0),(.36,.05,.52),gem)
   line('Oval frame',[(math.cos(i*PI/32)*.40,-.01,math.sin(i*PI/32)*.57) for i in range(64)],.035,trim,True)
   cylinder('Mirror handle',(0,0,-.56),(0,0,-.96),.045,metal)
   for side in [-1,1]:leaf(side*.12,-.07,-.52,.30,trim,side)
  elif f=='medallion':
   cylinder('Cast medallion',(0,.04,0),(0,-.04,0),.38,metal);emblem(0,-.065,0,.45,motif,trim,gem)
   ring('Talisman eye',(0,0,.45),.065,.02,trim)
   line('Braided cord',[(-.035,0,.48),(-.29,.04,.90),(0,.07,1.13),(.29,.04,.90),(.035,0,.48)],.018,leather)
  else:
   plate('Gothic reliquary',[(-.28,-.45),(.28,-.45),(.28,.28),(0,.61),(-.28,.28)],.13,metal)
   plate('Inset enamel',[(-.21,-.36),(.21,-.36),(.21,.25),(0,.48),(-.21,.25)],.018,cloth,-.08)
   emblem(0,-.115,.0,.34,motif,trim,gem)
   if f in ('wingrelic','triptych'):
    for side in [-1,1]:
     if f=='triptych':
      plate('Hinged reliquary panel',[(side*.30,-.44),(side*.64,-.35),(side*.64,.26),(side*.30,.40)],.055,trim,.06)
      plate('Ivory wing inlay',[(side*.35,-.35),(side*.57,-.29),(side*.57,.20),(side*.35,.29)],.025,cloth,.02)
      for z in [-.30,.20]:cylinder('Hinge barrel',(side*.29,0,z),(side*.29,0,z+.11),.025,trim)
     else:
      for j in range(5):leaf(side*(.26+j*.06),-.04,-.24+j*.08,.40+j*.035,trim,side)
   ring('Reliquary eye',(0,0,.68),.055,.02,trim)
  return
 # Curved convex shield: concentric rings guarantee connected surface and rim.
 if f in ('roundshield','targe','sunshield','roseshield'):
  outline=[(math.cos(i*PI/24)*.63,math.sin(i*PI/24)*.63) for i in range(48)]
 elif f=='leafshield':outline=[(0,.86),(.33,.58),(.51,.12),(.40,-.42),(0,-.88),(-.40,-.42),(-.51,.12),(-.33,.58)]
 elif f in ('pavise','royalshield'):outline=[(-.54,.74),(-.36,.85),(.36,.85),(.54,.74),(.49,-.38),(.28,-.72),(0,-.90),(-.28,-.72),(-.49,-.38)]
 elif f in ('dragonshield','antlershield'):outline=[(0,.82),(.22,.64),(.49,.68),(.61,.35),(.48,-.35),(0,-.92),(-.48,-.35),(-.61,.35),(-.49,.68),(-.22,.64)]
 else:outline=[(-.53,.63),(0,.73),(.53,.63),(.43,-.32),(0,-.84),(-.43,-.32)]
 n=len(outline);v=[(x*.08,-.18,z*.08) for x,z in outline]+[(x,-.02,z) for x,z in outline]+[(x,.065,z) for x,z in outline]
 faces=[tuple(reversed(range(n)))]+[(i,(i+1)%n,n+(i+1)%n,n+i) for i in range(n)]+[(n+i,n+(i+1)%n,2*n+(i+1)%n,2*n+i) for i in range(n)]+[tuple(range(2*n,3*n))]
 mesh('Convex shield',v,faces,wood if tier==0 else metal,.018,True)
 line('Forged rim',[(x,-.035,z) for x,z in outline],.033,trim,True)
 if f=='roundshield':
  for x in [-.38,-.19,0,.19,.38]:
   h=math.sqrt(.59*.59-x*x);line('Plank seam',[(x,-.03,-h),(x,-.11,0),(x,-.03,h)],.007,dark)
  uv('Shield boss',(0,-.18,0),(.16,.09,.16),metal)
 else:
  inner=[(x*.80,-.08,z*.80) for x,z in outline];line('Inset border',inner,.012,trim,True)
  emblem(0,-.21,.02,.58,motif,trim,gem)
 if tier>=3:
  for side in [-1,1]:
   for j in range(3):leaf(side*(.27-j*.04),-.14,-.40+j*.24,.22,trim,side)
 studs([(x*.91,-.062,z*.91) for x,z in outline[::max(1,n//12)]],trim,.022)
 # Back grip is real and visible at three-quarter angle.
 line('Shield hand grip',[(-.15,.11,-.15),(-.15,.23,.12),(.15,.23,.12),(.15,.11,-.15)],.04,leather)

def torso(c,M):
 metal,trim,cloth,leather,wood,dark,gem=M;f=c['form'];tier=c['tier'];motif=c['motif']
 # Mantles are open cloaks: broad shoulders, no sleeves or closed tunic silhouette.
 if f in ('mantle','leafmantle'):
  n=48;rows=[(-.94,.66,.30),(-.50,.56,.27),(.12,.48,.23),(.55,.51,.20),(.76,.19,.14)]
  v=[]
  for z,rx,ry in rows:
   for i in range(n+1):
    t=-PI*.36+i*PI*1.72/n
    ripple=1+.032*math.cos(i*PI/4)
    v.append((rx*math.cos(t)*ripple,ry*math.sin(t)*ripple,z+(.035*math.sin(i*PI/6) if z<-.9 else 0)))
  faces=[(j*(n+1)+i,j*(n+1)+i+1,(j+1)*(n+1)+i+1,(j+1)*(n+1)+i) for j in range(len(rows)-1) for i in range(n)]
  o=mesh('Open draped cloak',v,faces,cloth,0,True);mod=o.modifiers.new('Woven cloth thickness','SOLIDIFY');mod.thickness=.018
  for edge in [0,n]:line('Cloak edge',[v[j*(n+1)+edge] for j in range(len(rows))],.013,trim)
  line('Woven cloak hem',v[:n+1],.016,trim)
  for j in range(6):
   x=(j-2.5)*.13;leaf(x,-.24,.43-abs(x)*.1,.28,trim,-1 if x<0 else 1)
  ring('Cloak clasp',(0,-.16,.58),.065,.013,trim);ico('Clasp stone',(0,-.18,.58),(.04,.024,.05),gem)
  for side in [-1,1]:
   for j in range(4):emblem(side*(.33+j*.045),-.19,-.03-j*.18,.08,motif,trim,gem)
  return
 robes={'robe','tunic','coat','chasuble','mantle','hoodrobe','seraphrobe','astralrobe','sunrobe','leafmantle'}
 isrobe=f in robes;isplate=f in ('breastplate','dragonplate','royalplate');isscale=f in ('scale','drakescale','dragonplate');ismail=f in ('mail','surcoat')
 low=-.93 if isrobe else -.57
 profiles=[(low,.48 if isrobe else .36,.22,0),(-.40,.34,.19,0),(-.10,.31,.19,0),(.25,.42,.24,0),(.56,.43,.22,0),(.70,.18,.13,0)]
 bodymat=metal if isplate else leather if f in ('leather','brigandine','scale','drakescale') else cloth
 loft('Garment body',profiles,bodymat,n=64,cap=False)
 # Rolled hem and open collar, no invisible body or plugged neck.
 ellipse('Collar binding',.70,.18,.13,0,.025,trim if tier>1 else leather)
 ellipse('Hem binding',low,.48 if isrobe else .36,.22,0,.021,trim if tier>1 else leather)
 # Sleeves constructed as open tapered tubes, physically attach under shoulder.
 for side in [-1,1]:
  A=Vector((side*.38,0,.50));B=Vector((side*(.75 if isrobe else .62),-.01,-.10 if isrobe else .17))
  o=loft('Sleeve',[(0,.20,.18,0),((B-A).length,.15,.14,0)],cloth if isrobe or ismail else leather,n=32,cap=False);o.location=A;o.rotation_euler=(B-A).to_track_quat('Z','Y').to_euler()
  if not isrobe:
   for j in range(3 if tier>=3 else 1):
    cx=side*(.43+j*.075);z=.53-j*.07;v=[];n=24
    for edge in [0,1]:
     for k in range(n+1):
      t=-PI*.58+PI*1.16*k/n
      v.append((cx+side*(edge-.5)*.20,(.235-j*.01)*math.sin(t),z+.13*math.cos(t)-(edge*.045)))
    o=mesh('Forged shoulder lame',v,[(k,k+1,n+2+k,n+1+k) for k in range(n)],metal if isplate or ismail or isscale else leather,.008,True)
    mod=o.modifiers.new('Plate thickness','SOLIDIFY');mod.thickness=.022
    line('Shoulder edge',v[n+1:],.007,trim)

  else:
   o=ring('Cuff trim',(0,0,0),.15,.020,trim,axis='Z');o.location=B;o.rotation_euler=(B-A).to_track_quat('Z','Y').to_euler()
 # Belt follows the actual waist.
 loft('Waist belt',[(-.22,.329,.202,0),(-.12,.328,.202,0)],leather,n=48,cap=False)
 cube('Buckle',(0,-.221,-.17),(.16,.025,.12),trim,.012);cube('Buckle center',(0,-.239,-.17),(.10,.012,.067),dark,.007)
 if isrobe:
  # Long individual folds with amplitude increasing towards the hem.
  for side in [-1,1]:
   for j in range(4):
    x=side*(.07+j*.065)
    line('Stitched drape',[(x,-.198,-.27),(x*1.14,-.225,-.58),(x*1.38,-.23,low+.035)],.009,trim if j==3 and tier>=2 else cloth)
  for side in [-1,1]:line('Lapel seam',[(side*.16,-.11,.65),(side*.24,-.23,.30),(side*.08,-.22,-.09)],.018,trim if tier>1 else leather)
  if f in ('chasuble','seraphrobe','sunrobe'):
   plate('Ceremonial front stole',[(-.14,.59),(.14,.59),(.18,low+.08),(0,low-.02),(-.18,low+.08)],.025,cloth,-.255)
   line('Stole gold edge',[(-.13,-.28,.55),(-.17,-.28,low+.12),(0,-.28,low+.02),(.17,-.28,low+.12),(.13,-.28,.55)],.013,trim)
  if f=='hoodrobe':
   # Folded cowl surrounds the open neckline without a mannequin head.
   loft('Folded cowl',[(.55,.31,.20,.03),(.75,.27,.18,.06),(.92,.21,.13,.10)],cloth,n=48,cap=False)
  if tier>=3:
   for j in range(6):emblem((-.28 if j%2 else .28),-.235,low+.20+(j//2)*.22,.095,motif,trim,gem)
 else:
  if isscale:
   for row in range(5):
    for col in range(7):
     x=(col-3)*.10+(row%2)*.04;z=.47-row*.16
     if abs(x)>.35:continue
     y=-.22*math.sqrt(max(.05,1-(x/.44)**2))-.02
     plate('Overlapping scale',[(x-.058,z+.06),(x+.058,z+.06),(x+.05,z-.04),(x,z-.09),(x-.05,z-.04)],.025,trim if col==3 and tier>=3 else metal,y,.008)
  elif ismail:
   for row in range(15):
    z=-.49+row*.072
    for col in range(11):
     x=(col-5)*.061+(row%2)*.03
     if abs(x)>.34:continue
     y=-.22*math.sqrt(max(.05,1-(x/.44)**2))-.015
     o=ring('Linked mail ring',(x,y,z),.031,.007,metal,n=12);o.rotation_euler.x=(.3 if col%2 else -.3)
  elif f in ('gambeson','doublet','brigandine','leather'):
   for side in [-1,1]:
    for j in range(5):
     x=side*(.06+j*.06);line('Tailored seam',[(x,-.23,-.51),(x*.88,-.208,-.23),(x*1.08,-.25,.32),(x*1.12,-.19,.53)],.007,trim if tier>=3 else leather)
   if f=='brigandine':studs([(x,-.24,z) for x in [-.25,-.12,.12,.25] for z in [-.43,-.30,.03,.18,.34,.48]],trim,.012)
  if isplate:
   line('Breastplate medial ridge',[(0,-.24,-.48),(0,-.215,-.20),(0,-.28,.15),(0,-.24,.52)],.013,trim)
   for side in [-1,1]:line('Raised breastplate border',[(side*.12,-.16,.62),(side*.37,-.17,.49),(side*.33,-.18,.11),(side*.27,-.16,-.39)],.015,trim)
   for row in range(3):
    z=-.47-row*.105
    for side in [-1,1]:plate('Articulated fauld',[(side*.025,z+.04),(side*.32,z+.04),(side*.37,z-.07),(side*.035,z-.08)],.04,metal,-.20+row*.015)
 if tier>=2:emblem(0,-.285,.30,.26,motif,trim,gem)
 if tier>=3:
  for side in [-1,1]:
   for j in range(3):leaf(side*(.32+j*.08),-.20,.49-j*.09,.18,trim,side)
 if tier>=3:
  for side in [-1,1]:
   for j in range(3):
    x=side*(.13+j*.055)
    line('Engraved breastplate scroll',[(x+side*.025*math.sin(k*PI/12),-.245,.04+k*.013) for k in range(32)],.0035,trim)
  for z in [.03,.12,.21,.40]:studs([(-.32,-.165,z),(.32,-.165,z)],trim,.009)



def footwear(c,M):
 metal,trim,cloth,leather,wood,dark,gem=M;f=c['form'];tier=c['tier'];motif=c['motif']
 sandal=f in ('sandals','laced_sandals','wingboots');plateboot=f in ('greaves','sabatons','dragonboots','royalboots','sunboots')
 low=f in ('shoes','bardboots');high=1.03 if plateboot else .84 if f not in ('shoes','sandals') else .32
 for side in [-1,1]:
  before=set(bpy.context.scene.objects)
  # Fitted sole follows the toe, heel and narrow waist; no toy-like bulbous toe.
  loft('Layered leather sole',[(.01,.15,.32,-.11),(.07,.16,.34,-.12),(.10,.155,.33,-.12)],dark,n=40)
  if sandal:
   loft('Leather footbed',[(.10,.15,.32,-.12),(.135,.15,.32,-.12)],leather,n=40)
   for y in [-.30,-.06]:
    pts=[(math.cos(t)*.155,y,.135+math.sin(t)*.14) for t in [i*PI/20 for i in range(21)]]
    line('Sandal strap',pts,.026,leather)
   for xsign in [-1,1]:line('Heel strap',[(xsign*.14,.08,.13),(xsign*.13,.12,.33),(xsign*.08,-.04,.39)],.021,leather)
   line('Heel cup strap',[(.135*math.cos(t),.08+.10*math.sin(t),.31) for t in [i*PI/24 for i in range(25)]],.02,leather)
   if f!='sandals':
    line('Continuous ankle lacing',[(.12*math.cos(i*PI/16),.04+.12*math.sin(i*PI/16),.30+i*.0038) for i in range(129)],.010,leather)
    for side2 in [-1,1]:line('Sandal side support',[(side2*.12,.08,.13),(side2*.12,.08,.79)],.018,leather)
   if f=='wingboots':
    for j in range(3):leaf(side*.13,-.02,.23+j*.07,.25,trim,side)
  else:
   profiles=[(.10,.145,.30,-.12),(.19,.15,.32,-.14),(.27,.135,.25,-.10),(.38,.12,.14,.02),(.53,.13,.13,.04),(high,.17 if not low else .14,.145,.05)]
   profiles=sorted({p[0]:p for p in profiles if p[0]<=high}.values())
   if f in ('elfboots','bardboots'):
    profiles=[(z,rx,ry*1.15 if z<.3 else ry,cy-.035 if z<.3 else cy) for z,rx,ry,cy in profiles]
   loft('Tailored boot upper',profiles,leather,n=48,cap=False)
   ellipse('Open boot cuff',high,.17 if not low else .14,.145,.05,.024,cloth)
   if plateboot:
    # Toe articulation follows the instep, with clearly separated metal lames.
    for j in range(4):
     y=-.39+j*.10;z=.18+j*.025
     line('Sabatons overlapping plate',[(math.cos(t)*(.13 if j<2 else .14),y,z+math.sin(t)*.085) for t in [i*PI/20 for i in range(21)]],.025,metal)
    plate('Shin plate',[(-.12,.40),(.12,.40),(.16,high-.08),(0,high+.04),(-.16,high-.08)],.045,metal,-.115,.014)
    line('Shin ridge',[(0,-.15,.43),(0,-.17,high-.03)],.011,trim)
    if tier>=3:
     plate('Articulated knee',[(0,high+.17),(.17,high+.04),(.11,high-.10),(-.11,high-.10),(-.17,high+.04)],.05,metal,-.10)
     emblem(0,-.155,high+.015,.15,motif,trim,gem)
   elif f in ('laceboots','runeboots','astralboots','bardboots'):
    for j in range(4 if low else 6):
     z=.29+j*.075
     if z>high-.04:continue
     line('Crossed lacing',[(-.08,-.11,z),(.08,-.12,z+.055)],.008,trim if tier>=2 else cloth)
     line('Crossed lacing',[(.08,-.11,z),(-.08,-.12,z+.055)],.008,trim if tier>=2 else cloth)
   else:
    for z in [.42,high-.09]:
     if z>high:continue
     ellipse('Boot strap',z,.143,.14,.04,.022,cloth);cube('Boot buckle',(.09,-.105,z),(.065,.027,.07),trim,.008)
   if f in ('elfboots','bardboots'):
    plate('Turned leather cuff',[(-.17,high),(.17,high),(.15,high-.16),(0,high-.23),(-.15,high-.16)],.02,cloth,-.105)
   if f=='astralboots':
    for z in [.38,.65]:
     ellipse('Astral silver band',z,.144,.146,.04,.012,trim)
     for k in [-1,1]:ico('Inset sapphire',(k*.12,-.06,z),(.025,.025,.035),gem)
   if f in ('furboots','lynxboots'):
    for i in range(22):
     t=i*2*PI/22;uv('Cuff fur tuft',(.17*math.cos(t),.05+.15*math.sin(t),high-.015),(.045,.035,.067),cloth)
   if tier>=3 and not plateboot:emblem(0,-.115,high-.20,.13,motif,trim,gem)
  move_since(before,side*.235,side*.075,-high/2+side*.035)


def setup(c):
 bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
 for m in list(bpy.data.materials):bpy.data.materials.remove(m)
 tier=c['tier'];metal=mat('Forged '+c['metal'],METALS[c['metal']],.78,.35 if tier>=2 else .46,1)
 trim=mat('Fitted trim',METALS['gold'] if tier>=3 and c['damageTypes'][0] in ('MELEE','DIVINE') else METALS[c['metal']],.78,.32,1)
 cloth=mat('Woven textile',c['cloth'],0,.80,1);leather=mat('Vegetable tanned leather',c['leather'],0,.56,2);wood=mat('Carved hardwood',c['leather'],0,.40,2);dark=mat('Shadowed recess','#181b22',.15,.55)
 gemcolors={'MELEE':'#9f3245','RANGED':'#3e9770','ARCANIST':'#5086bd','DIVINE':'#ddc591'}
 gem=mat('Cut mineral',gemcolors[c['damageTypes'][0]],.38,.16);gem.node_tree.nodes.get('Principled BSDF').inputs['Coat Weight'].default_value=.55
 M=(metal,trim,cloth,leather,wood,dark,gem)
 {'weapon':weapon,'chest':torso,'guard':guard,'legs':footwear}[c['slot']](c,M)
 # A slight whole-object pose, not shape distortion. Keep exact scale in all axes.
 objects=[o for o in bpy.context.scene.objects if o.type in ('MESH','CURVE')]
 parent=bpy.data.objects.new('Equipment • '+c['title'],None);bpy.context.collection.objects.link(parent)
 for o in objects:o.parent=parent
 if c['slot']=='weapon':parent.rotation_euler.y=-.18 if c['form'] not in ('bell','censer','lute','orb','lantern','crossbow') else .10
 parent.rotation_euler.z=.07 if c['slot']=='guard' else 0
 bpy.context.view_layer.update()
 # Bounding box camera fit prevents cropping and preserves proportions.
 points=[o.matrix_world@Vector(p) for o in objects for p in o.bound_box]
 center=sum(points,Vector())/len(points);minz=min(p.z for p in points);maxz=max(p.z for p in points);center.z=(minz+maxz)/2
 camera_data=bpy.data.cameras.new('Orthographic catalogue camera');camera=bpy.data.objects.new('Camera',camera_data);bpy.context.collection.objects.link(camera)
 angle=Vector((3.0,-9,2.6 if c['slot']=='legs' else 1.7));camera.location=center+angle;camera.rotation_euler=(center-camera.location).to_track_quat('-Z','Y').to_euler();camera_data.type='ORTHO';camera_data.ortho_scale=3
 scene=bpy.context.scene;scene.camera=camera;scene.render.resolution_x=a.size;scene.render.resolution_y=a.size;scene.render.resolution_percentage=100
 bpy.context.view_layer.update();coords=[world_to_camera_view(scene,camera,p) for p in points];w=max(p.x for p in coords)-min(p.x for p in coords);h=max(p.y for p in coords)-min(p.y for p in coords)
 camera_data.ortho_scale*=max(w,h)/.83
 # Center in camera plane (asymmetric weapons need this, not just bbox world center).
 bpy.context.view_layer.update();coords=[world_to_camera_view(scene,camera,p) for p in points]
 dx=((max(p.x for p in coords)+min(p.x for p in coords))/2-.5)*camera_data.ortho_scale;dy=((max(p.y for p in coords)+min(p.y for p in coords))/2-.5)*camera_data.ortho_scale
 camera.location+=camera.rotation_euler.to_matrix()@Vector((dx,dy,0))
 def light(name,pos,power,size,color):
  d=bpy.data.lights.new(name,'AREA');d.energy=power;d.shape='DISK';d.size=size;d.color=color;o=bpy.data.objects.new(name,d);bpy.context.collection.objects.link(o);o.location=center+Vector(pos);o.rotation_euler=(center-o.location).to_track_quat('-Z','Y').to_euler()
 light('Warm broad key',(-3,-4,5),500,4,(1,.89,.74));light('Cool edge',(3,1,3),700,3,(.68,.82,1));light('Soft frontal fill',(1,-4,1),130,3,(.92,.95,1))
 scene.world.color=(.16,.16,.16);scene.render.engine='CYCLES';scene.cycles.samples=a.samples;scene.cycles.use_denoising=True;scene.cycles.max_bounces=5;scene.render.film_transparent=True
 scene.view_settings.view_transform='AgX';scene.view_settings.exposure=.3;scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGBA';scene.render.image_settings.color_depth='8'
 scene.render.filepath=str(P/'renders'/f"{c['id']}.png")
 bpy.context.preferences.filepaths.save_version=0
 bpy.ops.wm.save_as_mainfile(filepath=str(P/'source'/f"{c['id']}.blend"),compress=True)
 bpy.ops.render.render(write_still=True)
 print('ITEM_DONE',c['id'],len(objects),flush=True)

items=json.loads((P/'items.json').read_text());ids=set(a.ids.split(',')) if a.ids else None
for c in items:
 if ids and c['id'] not in ids:continue
 if a.skip_existing and (P/'renders'/f"{c['id']}.png").exists():continue
 setup(c)

import os
sys.stdout.flush();sys.stderr.flush();os._exit(0)
