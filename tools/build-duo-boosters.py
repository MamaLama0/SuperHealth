"""Print the two booster identities on the existing 3D meshes (no geometry changes)."""
from pathlib import Path
import json
import subprocess
from tempfile import TemporaryDirectory

root = Path(__file__).resolve().parents[1] / 'app/src/superhealth/assets/arsenal'

def wrapper(kind, gold, back):
    weapon = kind == 'weapon'
    color = '#ef9b78' if weapon else '#8de0cb'
    ink = '#221e29' if weapon else '#102c35'
    edge = '#f8d58d' if gold else '#e8e0cf'
    bands = ''.join(f'<path d="M{40+i*12} 18v26m0 738v26"/>' for i in range(37))
    emblem = ('''<g transform="rotate(28)"><path d="M0-155 23-115 15 60 47 76 43 92 12 82 10 142-10 142-12 82-43 92-47 76-15 60-23-115Z" fill="url(#steel)" stroke="#fff2d4" stroke-width="3"/><path d="M0-140V65" stroke="#252d36" stroke-width="5"/><path d="M-9 97h18m-18 14h18m-18 14h18" stroke="#644744" stroke-width="5"/></g>'''
      if weapon else '''<path d="M0-146 118-98 106 50Q88 119 0 160Q-88 119-106 50L-118-98Z" fill="url(#steel)" stroke="#eff8e9" stroke-width="5"/><path d="M0-121 93-82 82 43Q63 99 0 131Q-63 99-82 43L-93-82Z" fill="#15494e" stroke="#e0eddc" stroke-width="3"/><path d="M0-96 24-29 69-8 26 16 0 88-26 16-69-8-24-29Z" fill="url(#steel)"/><path d="M0-70V56" stroke="#e7fff0" stroke-width="3"/>''')
    if back:
        art=f'<g transform="translate(256 371) scale(.56)">{emblem}</g><path d="M178 535h156m-136 16h116m-96 16h76" stroke="{edge}" stroke-width="3" opacity=".65"/><text x="256" y="643" text-anchor="middle" fill="{edge}" font-size="16" letter-spacing="5">SUPERHEALTH</text>'
    else:
        art=f'''<circle cx="256" cy="416" r="177" fill="none" stroke="{color}" opacity=".18" stroke-width="44"/>
        <path d="M256 205 441 416 256 627 71 416Z" fill="none" stroke="{edge}" opacity=".7" stroke-width="2"/>
        <circle cx="256" cy="416" r="155" fill="none" stroke="{edge}" stroke-dasharray="2 14" stroke-width="3"/>
        <g transform="translate(256 416)">{emblem}</g>
        <text x="256" y="666" text-anchor="middle" fill="{edge}" font-family="sans-serif" font-weight="bold" font-size="28" letter-spacing="6">{'I' if weapon else 'II'}</text>
        <path d="M95 654h75m172 0h75" stroke="{edge}" stroke-width="2"/>'''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="826" viewBox="0 0 512 826">
      <defs><linearGradient id="base" x2=".9" y2="1"><stop stop-color="{ink}"/><stop offset=".5" stop-color="{'#705330' if gold else '#33424b'}"/><stop offset="1" stop-color="{ink}"/></linearGradient>
      <linearGradient id="steel"><stop stop-color="{edge}"/><stop offset=".42" stop-color="{color}"/><stop offset=".49" stop-color="#fff8dc"/><stop offset=".53" stop-color="{edge}"/><stop offset="1" stop-color="{'#91622d' if gold else '#497a80'}"/></linearGradient></defs>
      <path fill="url(#base)" d="M0 0h512v826H0z"/>
      <path d="M0 0h512v55H0zM0 771h512v55H0z" fill="{color}"/>
      <g stroke="{ink}" stroke-width="3" opacity=".55">{bands}</g>
      <path d="M28 74h456v678H28z" fill="none" stroke="{edge}" stroke-width="2"/>
      <path d="M39 85h434v656H39z" fill="none" stroke="{edge}" opacity=".3"/>
      <text x="256" y="136" text-anchor="middle" fill="{edge}" font-family="sans-serif" font-weight="bold" font-size="32" letter-spacing="9">ARSENAL</text>
      <text x="256" y="167" text-anchor="middle" fill="{color}" font-family="sans-serif" font-size="12" letter-spacing="5">{'GOLD EDITION' if gold else 'SUPERHEALTH'}</text>
      {art}
      <path d="M211 710h90" stroke="{color}" stroke-width="4"/>
      <path d="M0 202 16 212 0 222" fill="{edge}"/>
    </svg>'''

for kind in ['weapon','chest']:
    for gold in [False, True]:
        slug='duo-'+kind+('-gold' if gold else '')
        for back in [False,True]:
            svg=wrapper(kind,gold,back)
            (root/(slug+f'-{int(back)}.svg')).write_text(svg)
            with TemporaryDirectory() as temporary:
                png=Path(temporary)/'wrapper.png'
                subprocess.run(['inkscape',str(root/(slug+f'-{int(back)}.svg')),f'--export-filename={png}','--export-width=1024'],check=True,stdout=subprocess.DEVNULL)
                subprocess.run(['convert',str(png),'-quality','94',str(root/(slug+f'-{int(back)}.webp'))],check=True)

p=root/'directions.js'
data=json.loads(p.read_text().removeprefix('const packDirections = ').rstrip().removesuffix(';'))
for style in data:
    if style['slot'] not in ['weapon','chest']: continue
    if not style.get('edition') or style.get('key')=='or-solaire':
        style['slug']='duo-'+style['slot']+('-gold' if style.get('edition') else '')
        style['base']='#303a48' if style['slot']=='weapon' else '#20424a'
        style['accent']='#ef9b78' if style['slot']=='weapon' else '#8de0cb'
        style['tints']=[style['base']]*3
p.write_text('const packDirections = '+json.dumps(data,ensure_ascii=False)+';\n')
