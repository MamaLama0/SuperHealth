"""Deterministic vector artwork for the four Prism/Arcade boosters.

The print carries the silhouette and type; pack.js supplies angle-dependent foil.
Regenerate with Python, Inkscape and ImageMagick. No generated bitmap artwork.
"""
from pathlib import Path
import json
import subprocess
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1] / 'app/src/superhealth/assets/arsenal'


def emblem(weapon, gold):
    shade = '#80551d' if gold else '#384d6b'
    bright = '#fff1ae' if gold else '#f7ffff'
    if weapon:
        return f'''<g transform="rotate(24)">
          <path d="M0-174 31-130 22 70 68 88 57 110 19 97 16 153 26 170 0 188-26 170-16 153-19 97-57 110-68 88-22 70-31-130Z" fill="#101e32" stroke="#14233b" stroke-width="13" stroke-linejoin="round"/>
          <path d="M0-174 31-130 22 70 0 93-22 70-31-130Z" fill="url(#metal)" stroke="{bright}" stroke-width="3"/>
          <path d="M0-174V93L22 70 31-130Z" fill="{shade}" opacity=".74"/>
          <path d="M0-174 3-124 0 74-6 55-7-124Z" fill="{bright}"/>
          <path d="M-67 87-23 65 0 78 23 65 67 87 57 107 20 94 0 110-20 94-57 107Z" fill="url(#metal)" stroke="{bright}" stroke-width="3"/>
          <path d="M-15 109H15L12 155H-12Z" fill="#273247" stroke="{bright}" stroke-width="2"/>
          <path d="M-13 119 13 125m-26 7 26 6m-25 7 24 6" stroke="#91a1ba" stroke-width="4"/>
          <path d="M0 151 24 170 0 188-24 170Z" fill="url(#metal)" stroke="{bright}" stroke-width="3"/>
          <path d="M0 79 14 90 0 104-14 90Z" fill="url(#gem)" stroke="{bright}" stroke-width="2"/>
        </g>'''
    return f'''<g stroke-linejoin="round">
      <path d="M-45-137-82-131-121-109-153-55-133-30-100-40-77 28-89 110-50 126 0 157 50 126 89 110 77 28 100-40 133-30 153-55 121-109 82-131 45-137Z" fill="#14233b" stroke="#14233b" stroke-width="14"/>
      <path d="M-44-137Q0-110 44-137L73-109 53-66H-53L-73-109Z" fill="{shade}" stroke="{bright}" stroke-width="3"/>
      <path d="M-79-128-119-108-148-56-123-41-91-51-57-90Z" fill="url(#metal)" stroke="{bright}" stroke-width="3"/>
      <path d="M79-128 119-108 148-56 123-41 91-51 57-90Z" fill="url(#metal)" stroke="{bright}" stroke-width="3"/>
      <path d="M-119-108-137-75-100-69-79-108m198 0 18 33-37 6-21-39" fill="none" stroke="{shade}" stroke-width="7"/>
      <path d="M-70-99Q-40-69 0-66Q40-69 70-99L101-49 76 29 88 110 47 126 0 153-47 126-88 110-76 29-101-49Z" fill="url(#metal)" stroke="{bright}" stroke-width="4"/>
      <path d="M0-66 12-26 0 153 47 126 88 110 76 29 101-49 70-99Q40-69 0-66Z" fill="{shade}" opacity=".58"/>
      <path d="M-88-40-43-18 0-29 43-18 88-40M-74 25-37 44 0 37 37 44 74 25M-77 66-36 85 0 79 36 85 77 66M-80 99-39 116 0 110 39 116 80 99" fill="none" stroke="{shade}" stroke-width="7"/>
      <path d="M0-58V130M-83-49-43-27M43-27 83-49M-69 13-37 28M37 28 69 13" fill="none" stroke="{bright}" stroke-width="3"/>
      <path d="M0-61 21-39 0-15-21-39Z" fill="url(#gem)" stroke="{bright}" stroke-width="3"/>
    </g>'''


def gemstones():
    """Printed faceted stones, aligned with the live shader's gold-only bezels."""
    gems=[]
    for x,y,dark,mid,light in [(82,573,'#790e35','#e84068','#ffc4d0'),(430,573,'#075f47','#30d19a','#c5ffdd'),(256,733,'#452773','#a770e9','#f1d9ff')]:
        gems.append(f'''<g transform="translate({x} {y})" stroke-linejoin="round">
          <path d="M0-30 28 0 0 30-28 0Z" fill="#8e5c1d" stroke="#fff1b6" stroke-width="2"/>
          <path d="M0-24 21 0 0 24-21 0Z" fill="{mid}" stroke="#ffe298" stroke-width="2"/>
          <path d="M0-24 0 0-21 0Z" fill="{light}"/><path d="M21 0 0 24 0 0Z" fill="{dark}"/>
          <path d="M0-13 12 0 0 13-12 0Z" fill="{mid}" stroke="{light}" stroke-width="1.2"/>
          <path d="M-13-8-6-16M-16-3-11-8" stroke="#fff" stroke-width="2"/>
        </g>''')
    return ''.join(gems)


def wrapper(kind, gold, back, english=False):
    weapon = kind == 'weapon'
    accent = '#ef776f' if weapon else '#58d9ca'
    pale = '#ffd7c2' if weapon else '#c7fff3'
    ink = '#172338'
    body = ['#fff1b3', '#edc55c', '#b67b25', '#ffe8a0'] if gold else ['#f4f7ff', '#d3dcee', '#93a7c2', '#f6fbff']
    metal = ['#ffedaa', '#eab449', '#fff7ce', '#ac6d19'] if gold else ['#fcffff', '#8aa3be', '#faffff', '#4b688c']
    label = ('WEAPONS' if weapon else 'ARMOR') if english else ('ARMES' if weapon else 'ARMURES')
    title = f'''<text x="256" y="151" text-anchor="middle" fill="{ink}" font-size="14" font-weight="700" letter-spacing="5">SUPERHEALTH</text>
      <text x="256" y="218" text-anchor="middle" fill="{ink}" font-size="67" font-weight="900" letter-spacing="-3">ARSENAL</text>
      <path d="M48 239H464" stroke="{ink}" stroke-width="3"/>'''
    art = f'''<path d="M41 296 471 249 471 579 41 627Z" fill="{accent}" opacity="{'.20' if gold else '.72'}"/>
      <path d="M41 296 210 277 41 498Z" fill="{pale}" opacity=".70"/>
      <path d="M471 249 254 603 471 579Z" fill="{ink}" opacity=".12"/>
      <path d="M41 627 471 249M41 498 471 579M210 277 254 603" fill="none" stroke="#fff" stroke-width="1.6" opacity=".7"/>
      <g transform="translate(256 432) scale(.89)">{emblem(weapon,gold)}</g>
      <path d="M41 651 471 607V708H41Z" fill="{ink}"/>
      <path d="M41 651 471 607" stroke="{accent}" stroke-width="7"/>
      <text x="256" y="685" text-anchor="middle" fill="#fff" font-size="{'38' if gold else '45'}" font-weight="900" letter-spacing="1">{label}{' GOLD' if gold else ''}</text>'''
    if back:
        art = f'''<g transform="translate(256 426) scale(.56)">{emblem(weapon,gold)}</g>
          <text x="256" y="631" text-anchor="middle" fill="{ink}" font-size="31" font-weight="900">{label}{' GOLD' if gold else ''}</text>
          <path d="M171 661H341M206 675H306" stroke="{ink}" stroke-width="2"/>'''
    ribs = ''.join(f'<path d="M{x} 13V52m0 722v39"/>' for x in range(10,510,8))
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="826" viewBox="0 0 512 826">
      <defs>
        <linearGradient id="foil" x1="0" y1="0" x2="1" y2=".65">{''.join(f'<stop offset="{i/3}" stop-color="{c}"/>' for i,c in enumerate(body))}</linearGradient>
        <linearGradient id="metal" x1="0" y1=".2" x2="1" y2=".7">{''.join(f'<stop offset="{i/3}" stop-color="{c}"/>' for i,c in enumerate(metal))}</linearGradient>
        <linearGradient id="gem"><stop stop-color="{accent}"/><stop offset=".45" stop-color="{pale}"/><stop offset=".5" stop-color="{accent}"/><stop offset="1" stop-color="{ink}"/></linearGradient>
        <linearGradient id="spectrum" x1="0" y1="1" x2="1" y2="0"><stop stop-color="#62e8f4"/><stop offset=".25" stop-color="#ca9bfc"/><stop offset=".49" stop-color="#ffc6d8"/><stop offset=".7" stop-color="#f5efb2"/><stop offset="1" stop-color="#75e4c5"/></linearGradient>
      </defs>
      <path fill="url(#foil)" d="M0 0H512V826H0Z"/>
      <path fill="url(#spectrum)" opacity="{'.12' if gold else '.38'}" d="M0 0H512V826H0Z"/>
      <path d="M24 66H488V760H24Z" fill="none" stroke="{'#fff0a6' if gold else '#fff'}" stroke-width="2"/>
      <path d="M0 62H512M0 766H512" stroke="{ink}" stroke-opacity=".24" stroke-width="5"/>
      <g stroke="{ink}" stroke-opacity=".24" stroke-width="2">{ribs}</g>
      <path d="M0 108H512" stroke="{ink}" stroke-opacity=".30" stroke-dasharray="4 6"/>
      <g font-family="DejaVu Sans, sans-serif">{title}{art}</g>
      <path d="M44 731H112M400 731H468" stroke="{ink}" stroke-width="2"/>
      {gemstones() if gold else f'<path d="M248 723H264V739H248Z" fill="{accent}" transform="rotate(45 256 731)"/>'}
    </svg>'''


def build():
    for kind in ('weapon', 'chest'):
        for gold in (False, True):
            slug = 'prism-' + kind + ('-gold' if gold else '')
            for english in (False, True):
                localized = slug + ('-en' if english else '')
                for back in (False, True):
                    path = ROOT / f'{localized}-{int(back)}.svg'
                    path.write_text(wrapper(kind, gold, back, english))
                    with TemporaryDirectory() as tmp:
                        png = Path(tmp) / 'print.png'
                        subprocess.run(['inkscape',str(path),f'--export-filename={png}','--export-width=1024'],check=True,stdout=subprocess.DEVNULL)
                        subprocess.run(['convert',str(png),'-quality','94',str(path.with_suffix('.webp'))],check=True)
    path = ROOT / 'directions.js'
    data = json.loads(path.read_text().removeprefix('const packDirections = ').rstrip().removesuffix(';'))
    for style in data:
        if style['slot'] in ('weapon','chest') and (not style.get('edition') or style.get('key') == 'or-solaire'):
            style['slug'] = 'prism-' + style['slot'] + ('-gold' if style.get('edition') else '')
            style['material'] = 'prism'
            style['accent'] = '#ef776f' if style['slot'] == 'weapon' else '#58d9ca'
    path.write_text('const packDirections = ' + json.dumps(data,ensure_ascii=False) + ';\n')


if __name__ == '__main__':
    build()
