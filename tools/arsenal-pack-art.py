"""Build the four printed Arsenal wrappers and their engraved card backs."""
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1] / "app/src/superhealth/assets/arsenal"
# The same engraving families are printed on both sides of each foil sachet.
FAMILIES = {
    "weapon": ("bra-2", "#893448", "#260d22", "#ffe0a3", "#f7796a", "#f7d5aa",
        "M0-128 22-92 14 44 50 36 44 58 15 54 10 111 0 125-10 111-15 54-44 58-50 36-14 44-22-92Z",
        "M0-104v145m-12 2L0 22l12 21m-8 24-10 23m12-10-10 23",
        "M0 584 512 176M0 633 512 225M0 684 512 276M0 739 512 331",
        ["#342451", "#15132d", "#261738", "#63418c", "#342656", "#e6bffa"]),
    "chest": ("pec-1", "#6c7541", "#242d1b", "#f5e3ad", "#c2c37b", "#e2d8af",
        "M-118-54-83-95-42-71Q0-46 42-71L83-95 118-54 86-7 77 62 37 103H-37L-77 62-86-7Z",
        "M-110-54l30-20 28 27-34 18m196-25L80-74 52-47l34 18M-80-6 0 35 80-6M-73 18 0 58 73 18M-67 42 0 81 67 42",
        "M44 649V313a212 212 0 0 1 424 0v336M62 649V316a194 194 0 0 1 388 0v333M93 649V327a163 163 0 0 1 326 0v322",
        ["#343b26", "#181d17", "#2b3120", "#626e3b", "#353c25", "#dae995"]),
    "guard": ("dos-1", "#236d70", "#10252c", "#b7f7df", "#64cdc2", "#def5cb",
        "M0-122 100-79v61Q96 62 0 120Q-96 62-100-18v-61Z",
        "M0-90 73-59v41Q67 43 0 85Q-67 43-73-18v-41ZM0-65 23-18 65 0 23 18 0 58-23 18-65 0-23-18Z",
        "M256 147 459 264v255L256 637 53 519V264ZM256 176 435 282v220L256 608 77 502V282ZM53 264 0 233m459 31 53-31M53 519 0 550m459-31 53 31",
        ["#164450", "#101e2c", "#112f35", "#287b81", "#1d4650", "#83efe0"]),
    "legs": ("jam-1", "#393f83", "#1a193b", "#d9d5ff", "#808af4", "#c2e0ff",
        "M-90-99h70l-7 123-13 77h-62l14-36-5-40ZM20-99h70l3 124-5 40 14 36H40L27 24Z",
        "M-84-65l50 18m68 0 50-18m-168 26 47 21m74 0 47-21M-79 14l38 22m82 0 38-22M-81 70h44m74 0h44",
        "M0 190l256 151 256-151M0 222l256 151 256-151M0 565l256 121 256-121M0 595l256 121 256-121",
        ["#353567", "#181832", "#272349", "#525a9f", "#39336f", "#9abdf9"]),
}


def wrapper(key, spec, back):
    _, base, ink, edge, jewel, bright, symbol, detail, frame, _ = spec
    rays = "".join(f'<path d="M256 233v31" transform="rotate({a} 256 413)"/>' for a in range(0, 360, 15))
    filigree = "".join(f'<path d="M{x} 0v52m0 722v52"/>' for x in range(4, 512, 8))
    points = "".join(f'<path d="M{x} {y-8}l6 8-6 8-6-8z"/>' for x,y in
                     [(87,224),(425,224),(68,414),(444,414),(100,606),(412,606),(256,672),(256,192)])
    if key == "weapon":
        setting = '<path d="M256 185 412 412 256 658 100 412Z" fill="none"/><path d="M256 218 385 412 256 618 127 412Z" fill="#491c32"/>'
        pose = "rotate(27) scale(1.34)"
    elif key == "chest":
        setting = '<path d="M105 566V364a151 151 0 0 1 302 0v202Z" fill="#343f28"/><path d="M123 547V365a133 133 0 0 1 266 0v182Z" fill="none"/>'
        pose = "scale(.98)"
    elif key == "guard":
        setting = '<circle cx="256" cy="413" r="167" fill="#174b52"/><circle cx="256" cy="413" r="145" fill="none"/><circle cx="256" cy="413" r="156" fill="none" stroke-dasharray="2 7"/>'
        pose = "scale(1.11)"
    else:
        setting = '<path d="M256 210 396 306v211L256 612 116 517V306Z" fill="#242957"/><path d="M256 227 378 317v188L256 595 134 505V317Z" fill="none"/>'
        pose = "rotate(-8)"
    display = f'''<g stroke="{edge}" stroke-width="2.7" opacity=".79">{setting}</g>
    <g fill="none" stroke="{edge}" opacity=".55" stroke-width="1.2">{rays}</g>
    <g transform="translate(256 414) {pose}" stroke-linejoin="round" stroke-linecap="round">
      <path d="{symbol}" fill="#121321" opacity=".52" transform="translate(4 9)"/>
      <path d="{symbol}" fill="url(#metal)" stroke="{edge}" stroke-width="4"/>
      <path d="{detail}" fill="none" stroke="{ink}" opacity=".8" stroke-width="6"/>
      <path d="{detail}" fill="none" stroke="{bright}" opacity=".84" stroke-width="1.7" transform="translate(0 -2)"/>
      <path d="M0-16 11 0 0 16-11 0Z" fill="{jewel}" stroke="{edge}" stroke-width="2"/>
    </g>'''
    if back:
        display = f'''<path d="M250 103h14v681h-14z" fill="{bright}" opacity=".26"/>
        <path d="M256 272 383 334v164L256 560 129 498V334Z" fill="{ink}" stroke="{edge}" stroke-width="3"/>
        <g transform="translate(256 414) scale(.70)"><path d="{symbol}" fill="url(#metal)" stroke="{edge}" stroke-width="4"/>
        <path d="{detail}" fill="none" stroke="{ink}" stroke-width="3"/></g>'''
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="512" height="826" viewBox="0 0 512 826">
      <defs>
       <linearGradient id="film" x1="0" y1="1" x2="1" y2="0"><stop stop-color="{ink}"/><stop offset=".54" stop-color="{base}"/><stop offset="1" stop-color="{ink}"/></linearGradient>
       <linearGradient id="rim"><stop stop-color="{ink}"/><stop offset=".34" stop-color="{jewel}"/><stop offset=".63" stop-color="{base}"/><stop offset=".90" stop-color="{jewel}"/><stop offset="1" stop-color="{ink}"/></linearGradient>
       <linearGradient id="metal" x1="0" y1="0" x2=".9" y2="1"><stop stop-color="#fff6de"/><stop offset=".33" stop-color="{jewel}"/><stop offset=".55" stop-color="{bright}"/><stop offset=".77" stop-color="{base}"/><stop offset="1" stop-color="{jewel}"/></linearGradient>
       <pattern id="grain" width="18" height="18" patternUnits="userSpaceOnUse"><path d="M9 0l9 9-9 9-9-9Z" fill="none" stroke="{edge}" stroke-width=".6"/></pattern>
      </defs>
      <rect width="512" height="826" fill="url(#film)"/>
      <rect x="19" y="68" width="474" height="690" fill="url(#grain)" opacity=".18"/>
      <path d="{frame}" fill="none" stroke="{edge}" stroke-width="2.5" opacity=".37"/>
      <path d="M30 158v566m452-566v566M41 198v474m430-474v474" fill="none" stroke="{edge}" stroke-width="1.5" opacity=".58"/>
      <rect width="512" height="52" fill="url(#rim)"/><rect y="774" width="512" height="52" fill="url(#rim)"/>
      <g fill="none" stroke="#fff4dc" opacity=".27">{filigree}</g>
      <path d="M18 107h476" stroke="{edge}" opacity=".72" stroke-width="2" stroke-dasharray="9 7"/>
      <g fill="{edge}" opacity=".78">{points}</g>{display}
    </svg>'''


def card_back(spec):
    _, _, ink, edge, jewel, _, symbol, detail, _, palette = spec
    master = (ROOT / "card-back.svg").read_text()
    for original, replacement in zip(["#342451", "#15132d", "#261738", "#63418c", "#342656", "#e6bffa"], palette):
        master = master.replace(original, replacement)
    ornament = f'''<g transform="translate(256 373) scale(.20)" stroke-linecap="round" stroke-linejoin="round">
      <path d="{symbol}" fill="{ink}" stroke="{edge}" stroke-width="8"/>
      <path d="{detail}" fill="none" stroke="{jewel}" stroke-width="4"/>
    </g>'''
    return master.replace('  </g>\n</svg>', ornament + '  </g>\n</svg>')


GOLD = {
    "or-solaire": ("#d49b23", "#684119", "#fff4b4", "#ffc633", "#fff5b6"),
    "or-noir": ("#52433d", "#18161f", "#f4cc77", "#aa7c49", "#f8e2a3"),
    "or-astral": ("#42647b", "#242846", "#fff0be", "#bba45e", "#a0e8ed"),
}

with TemporaryDirectory(prefix="arsenal-art-") as temp:
    for key, spec in FAMILIES.items():
        skins = [spec] + [(f"{edition}-{key}", *colors, *spec[6:]) for edition, colors in GOLD.items()]
        for skin in skins:
            for back in (0, 1):
                svg = Path(temp) / "print.svg"
                png = svg.with_suffix(".png")
                svg.write_text(wrapper(key, skin, back))
                subprocess.run(["inkscape", str(svg), f"--export-filename={png}"], check=True, stdout=subprocess.DEVNULL)
                subprocess.run(["magick", str(png), "-quality", "90", str(ROOT / f"{skin[0]}-{back}.webp")], check=True)
        (ROOT / f"card-back-{key}.svg").write_text(card_back(spec))
