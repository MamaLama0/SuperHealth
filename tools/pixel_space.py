# Planètes pixel-art du voyage (48 × 48) dans le style de Terran.png du pack acheté : pas de contour, lumière en haut à gauche,
# ombre tramée en damier vers le bas à droite, liseré sombre commun (#403353, #3B1725). Plus la fusée et l'explorateur (16 × 16).
# La Lune est la nôtre : grise et crème, cratères éclairés du bon côté, et une petite mer en forme de cœur.
# usage : python3 tools/pixel_space.py <dossier de sortie>   (écrit des .rgba bruts puis appelle ImageMagick)
import math, os, subprocess, sys

OUT = sys.argv[1]
os.makedirs(OUT, exist_ok=True)
DEEP, RIM = (64, 51, 83), (59, 23, 37)
LIGHT = (-0.55, -0.62, 0.56)  # direction de la lumière (haut-gauche, vers l'observateur)
L = math.sqrt(sum(c * c for c in LIGHT)); LIGHT = tuple(c / L for c in LIGHT)


def hexc(h): return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def save(name, px, n):
    raw = os.path.join(OUT, name + ".rgba")
    with open(raw, "wb") as f:
        f.write(bytes(v for row in px for p in row for v in ((*p, 255) if p else (0, 0, 0, 0))))
    subprocess.run(["magick", "-size", f"{n}x{n}", "-depth", "8", "rgba:" + raw, os.path.join(OUT, name + ".png")], check=True)
    os.remove(raw)


# ---- bruit de valeur, déterministe -------------------------------------------------------
def rnd(ix, iy, seed):
    h = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535


def noise(x, y, seed, scale):
    x /= scale; y /= scale
    x0, y0 = math.floor(x), math.floor(y)
    fx, fy = x - x0, y - y0
    sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = rnd(x0, y0, seed), rnd(x0 + 1, y0, seed)
    c, d = rnd(x0, y0 + 1, seed), rnd(x0 + 1, y0 + 1, seed)
    return (a + (b - a) * sx) + ((c + (d - c) * sx) - (a + (b - a) * sx)) * sy


def fbm(x, y, seed, scale=10):
    return 0.6 * noise(x, y, seed, scale) + 0.3 * noise(x, y, seed + 7, scale / 2) + 0.1 * noise(x, y, seed + 13, scale / 4)


# ---- planète -------------------------------------------------------------------------------
def planet(name, palette, surface, r=22.6, n=48, ring=None):
    """palette : 4 tons de la surface, du plus clair au plus sombre ; surface(x, y, nx, ny) → décalage de ton (-1..+2)."""
    cx = cy = n / 2
    px = [[None] * n for _ in range(n)]
    for y in range(n):
        for x in range(n):
            dx, dy = (x + .5 - cx) / r, (y + .5 - cy) / r
            d2 = dx * dx + dy * dy
            if d2 > 1: continue
            nz = math.sqrt(1 - d2)
            lit = dx * LIGHT[0] + dy * LIGHT[1] + nz * LIGHT[2]
            # 0 clair … 3 sombre, puis le liseré d'ombre
            t = 0 if lit > 0.78 else 1 if lit > 0.42 else 2 if lit > 0.08 else 3 if lit > -0.22 else 4
            frac = {0: 0, 1: (0.78 - lit) / .36, 2: (0.42 - lit) / .34, 3: (0.08 - lit) / .3, 4: 0}[t]
            # damier à la frontière (moitié inférieure de la bande suivante), comme le pack
            if t in (2, 3) and frac > 0.62 and (x + y) % 2 == 0: t += 1
            t = t + surface(x, y, dx, dy)
            t = max(0, t)
            col = palette[t] if t < len(palette) else (DEEP if t == len(palette) else RIM)
            # tout au bord de l'ombre : le liseré du pack
            if d2 > 0.86 and lit < -0.05: col = RIM
            px[y][x] = col
    if ring: ring(px, cx, cy, r)
    save(name, px, n)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


# Lune : crème et gris, mers sombres, cratères (bord bas-droit éclairé), une mer en cœur
moon_pal = [hexc("#F4F0E3"), hexc("#D8D4CB"), hexc("#ABA8B4"), hexc("#7B7891")]
craters = [(15, 15, 4.2), (31, 12, 2.6), (34, 30, 5.0), (12, 32, 2.4), (24, 38, 3.0), (39, 20, 2.0), (21, 25, 1.6)]


def heart(x, y):
    # petite mer en cœur, centrée vers (27, 21)
    u, v = (x - 27) / 4.2, -(y - 20.5) / 4.2
    return (u * u + v * v - 1) ** 3 - u * u * v ** 3 <= 0


def moon_surface(x, y, dx, dy):
    for (cx, cy, cr) in craters:
        dd = math.hypot(x + .5 - cx, y + .5 - cy)
        if dd <= cr:
            # paroi opposée à la lumière éclairée, l'autre dans l'ombre
            side = ((x + .5 - cx) * LIGHT[0] + (y + .5 - cy) * LIGHT[1]) / max(cr, 1)
            return 1 if side > 0.15 else (-1 if side < -0.45 and dd > cr * 0.55 else 0)
        if cr < dd <= cr + 0.9 and ((x + .5 - cx) * LIGHT[0] + (y + .5 - cy) * LIGHT[1]) < 0:
            return -1
    if heart(x, y): return 2
    return 1 if fbm(x, y, 3, 9) > 0.7 else 0


planet("moon", moon_pal, moon_surface)

# Mars : rouille, calotte polaire, taches sombres
mars_pal = [hexc("#F6B27A"), hexc("#E2703F"), hexc("#B6452B"), hexc("#7E2E2C")]


def mars_surface(x, y, dx, dy):
    if dy < -0.84 and abs(dx) < 0.5: return -9  # calotte : retombe sur le ton le plus clair (bloqué à 0 puis blanchi plus bas)
    f = fbm(x, y, 11, 8)
    return 1 if f > 0.62 else (-1 if f < 0.3 else 0)


def mars_ring(px, cx, cy, r):
    for y in range(48):
        for x in range(48):
            dx, dy = (x + .5 - cx) / r, (y + .5 - cy) / r
            if dx * dx + dy * dy <= 1 and dy < -0.84 and abs(dx) < 0.5: px[y][x] = (240, 238, 248)


planet("mars", mars_pal, mars_surface, ring=mars_ring)

# Jupiter : bandes ondulées, grande tache rouge
jup_pal = [hexc("#F3DDB8"), hexc("#DDB07A"), hexc("#B9804E"), hexc("#875538")]


def jup_surface(x, y, dx, dy):
    wave = math.sin(x * 0.45 + y * 0.1) * 0.9 + (fbm(x, y, 5, 6) - 0.5) * 2.2
    band = int((y + wave) / 4.2) % 4
    off = {0: 0, 1: 1, 2: 0, 3: -1}[band]
    return off


def jup_spot(px, cx, cy, r):
    # grande tache rouge, ombrée comme le reste (tons clairs → rouge clair, sombres → rouge sombre)
    light = {jup_pal[0], jup_pal[1]}
    for y in range(48):
        for x in range(48):
            if px[y][x] and px[y][x] not in (DEEP, RIM) and math.hypot((x + .5 - 30) / 1.7, y + .5 - 30) < 3.0:
                px[y][x] = hexc("#E2805E") if px[y][x] in light else hexc("#B5523E")


planet("jupiter", jup_pal, jup_surface, ring=jup_spot)

# Saturne : globe plus petit et anneau incliné (derrière puis devant)
sat_pal = [hexc("#F7E6B5"), hexc("#E6C47A"), hexc("#C39B55"), hexc("#8B6B3E")]


def sat_surface(x, y, dx, dy):
    return 1 if int((y + math.sin(x * 0.3)) / 3.5) % 3 == 1 else 0


def sat_ring(px, cx, cy, r):
    ring_c = [hexc("#E9D6A2"), hexc("#C9AE73"), hexc("#9C8253")]
    for y in range(48):
        for x in range(48):
            u, v = (x + .5 - cx), (y + .5 - cy)
            # ellipse inclinée
            a = math.radians(-18)
            ru = u * math.cos(a) - v * math.sin(a); rv = u * math.sin(a) + v * math.cos(a)
            e = (ru / 23.0) ** 2 + (rv / 6.4) ** 2
            e2 = (ru / 17.5) ** 2 + (rv / 4.6) ** 2
            if e <= 1 and e2 >= 1:
                behind = rv < 0 and (u * u + v * v) < r * r
                if behind: continue
                k = 0 if e < 0.55 else 1 if e < 0.8 else 2
                if ru > 6: k = min(2, k + 1)
                px[y][x] = ring_c[k]


planet("saturn", sat_pal, sat_surface, r=14.5, ring=sat_ring)


# ---- sprites 16 × 16 -------------------------------------------------------------------------
def sprite(name, rows, colors):
    n = 16
    px = [[None] * n for _ in range(n)]
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".": px[y][x] = colors[ch]
    save(name, px, n)


O = hexc("#3B2232")
sprite("rocket", [
    ".......OO.......",
    "......OWWO......",
    ".....OWWWWO.....",
    ".....OWWWSO.....",
    "....OWWBBWSO....",
    "....OWBLLBSO....",
    "....OWBBBBSO....",
    "....OWWWWWSO....",
    "....OWWWWWSO....",
    "...ORWWWWWSRO...",
    "..ORROWWWWORRO..",
    "..ORO.OOOO.ORO..",
    "..OO..OYYO..OO..",
    "......OYGO......",
    ".......YO.......",
    ".......G........",
], {"O": O, "W": hexc("#F4F6FA"), "S": hexc("#B9C2CF"), "B": hexc("#2559C7"), "L": hexc("#9FD3FF"),
    "R": hexc("#E5574F"), "Y": hexc("#F2A93B"), "G": hexc("#FFF1C2")})

sprite("explorer", [
    "................",
    ".....OOOOO......",
    "....OWWWWWO.....",
    "...OWVVVVWWO....",
    "...OWVLVVWWO....",
    "...OWVVVVWWO....",
    "....OWWWWWO.....",
    "...OYORRRROO....",
    "..OYYORRRRRO....",
    "..OYYORRWRRO....",
    "..OYYORRRRRO....",
    "...OOORRRRO.....",
    ".....ORROROO....",
    ".....OROORRO....",
    "....ORRO.ORRO...",
    "....OOO...OOO...",
], {"O": O, "W": hexc("#F4F6FA"), "V": hexc("#2B3A67"), "L": hexc("#9FD3FF"), "R": hexc("#E5574F"), "Y": hexc("#F2A93B")})
print("ok")
