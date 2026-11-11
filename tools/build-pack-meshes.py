"""Build continuous foil pouches: shared edge coordinates, thin welded seams.

Eight triangle lists: front/back/edge/inside for body, then tear strip.
Vertex layout is xyz, uv, normal. The cut at y=0 has matching boundary samples;
the runtime separates the two halves only during an opening gesture.
"""
from pathlib import Path
import math
import struct

ROOT = Path(__file__).resolve().parents[1] / 'app/src/superhealth/assets/arsenal'
BOTTOM, TOP = -1.74, .26
NX = 64


def smooth(a, b, x):
    t = max(0., min(1., (x-a)/(b-a)))
    return t*t*(3-2*t)


def surface(u, y, width, face):
    x = (u-.5)*width
    v = (y-BOTTOM)/(TOP-BOTTOM)
    s = 2*u-1
    # The seam is always just 0.006 deep. Both faces use this same envelope.
    across = max(0., 1-s*s)
    body = smooth(.035,.17,v)*(1-smooth(.83,.965,v))
    depth = .003 + .061*across*across*body
    # Coherent low-amplitude folds; vanish at the welded perimeter.
    folds = .0025*math.sin(y*24+s*5)*math.sin(s*13)*across*body
    ribs = .0012*math.sin(u*math.pi*128)*(1-body)*across
    z = face*(depth+folds+ribs)
    return (x,y,z)


def vertex(u,y,width,face,inside=False):
    p=surface(u,y,width,face)
    eps=.0001
    du=tuple(a-b for a,b in zip(surface(u+eps,y,width,face),surface(u-eps,y,width,face)))
    dy=tuple(a-b for a,b in zip(surface(u,y+eps,width,face),surface(u,y-eps,width,face)))
    n=(du[1]*dy[2]-du[2]*dy[1],du[2]*dy[0]-du[0]*dy[2],du[0]*dy[1]-du[1]*dy[0])
    length=math.sqrt(sum(a*a for a in n))
    n=tuple(a/length*face*(-1 if inside else 1) for a in n)
    if inside:
        p=(p[0],p[1],p[2]-face*.0007)
    return (*p,u if face==1 else 1-u,(y-BOTTOM)/2,*n)


def panel(width,face,ys,inside=False):
    vertices=[]
    for a,b in zip(ys,ys[1:]):
        for i in range(NX):
            u,v=i/NX,(i+1)/NX
            corners=[vertex(u,a,width,face,inside),vertex(v,a,width,face,inside),vertex(v,b,width,face,inside),vertex(u,b,width,face,inside)]
            order=(0,1,2,0,2,3) if (face==1)!=inside else (0,2,1,0,3,2)
            vertices.extend(corners[j] for j in order)
    return vertices


def edges(width,ys,cap):
    vertices=[]
    def quad(points,normal):
        verts=[(*p,.5,(p[1]-BOTTOM)/2,*normal) for p in points]
        # Orient each patch outward, matching the adjacent face boundary exactly.
        a,b,c=points[:3]
        ab=tuple(b[i]-a[i] for i in range(3));ac=tuple(c[i]-a[i] for i in range(3))
        cross=(ab[1]*ac[2]-ab[2]*ac[1],ab[2]*ac[0]-ab[0]*ac[2],ab[0]*ac[1]-ab[1]*ac[0])
        order=(0,1,2,0,2,3) if sum(cross[i]*normal[i] for i in range(3))>0 else (0,2,1,0,3,2)
        vertices.extend(verts[i] for i in order)
    for u in (0.,1.):
        for a,b in zip(ys,ys[1:]):
            quad([surface(u,a,width,1),surface(u,b,width,1),surface(u,b,width,-1),surface(u,a,width,-1)],(-1 if u==0 else 1,0,0))
    y=ys[0] if cap=='bottom' else ys[-1]
    for i in range(NX):
        a,b=i/NX,(i+1)/NX
        quad([surface(a,y,width,1),surface(b,y,width,1),surface(b,y,width,-1),surface(a,y,width,-1)],(0,-1 if cap=='bottom' else 1,0))
    return vertices


def build(width):
    parts=[]
    for low,high,ny,cap in [(BOTTOM,0.,64,'bottom'),(0.,TOP,16,'top')]:
        ys=[low+(high-low)*i/ny for i in range(ny+1)]
        parts.extend([panel(width,1,ys),panel(width,-1,ys),edges(width,ys,cap),panel(width,1,ys,True)+panel(width,-1,ys,True)])
    # Structural checks: lateral seam is thin, symmetric and meets both surfaces.
    for y in [BOTTOM,-1.5,-.8,0.,.1,TOP]:
        for u in (0.,1.):
            assert abs(surface(u,y,width,1)[2]-.003)<1e-9
            assert abs(surface(u,y,width,-1)[2]+.003)<1e-9
    blob=struct.pack('<8I',*(len(p) for p in parts))
    for part in parts:
        blob+=struct.pack('<'+str(len(part)*8)+'f',*(v for row in part for v in row))
    return blob


if __name__=='__main__':
    for name,width in [('weapon',1.16),('chest',1.24)]:
        (ROOT/(name+'.mesh')).write_bytes(build(width))
        print(name+': continuous pouch, 0.006-deep side seam')
