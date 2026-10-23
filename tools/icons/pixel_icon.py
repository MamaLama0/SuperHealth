# Icône pixel-art (module Aventure) : l'icône de base (moon_bg.svg + moon_fg.svg) redessinée pixel par pixel, mêmes formes,
# mêmes positions, mêmes couleurs, en aplats. Toile de 54 × 54 = les 108 dp d'une icône adaptative (1 pixel = 2 dp, donc un
# facteur entier dans toutes les densités : ×2 mdpi … ×8 xxxhdpi).
# usage : python3 pixel_icon.py out.rgba  →  magick -size 54x54 -depth 8 rgba:out.rgba out.png
import sys, math

N, U = 54, 2  # pixels, unités SVG par pixel
SS = 4        # sur-échantillonnage pour la couverture des formes

def hexc(s): return tuple(int(s[i:i + 2], 16) for i in (1, 3, 5))
def mix(a, b, t): return tuple(round(x + (y - x) * t) for x, y in zip(a, b))
def cover(inside, x, y):
    n = sum(inside(U * (x + (i + .5) / SS), U * (y + (j + .5) / SS)) for i in range(SS) for j in range(SS))
    return n / (SS * SS)

# ciel : dégradé (0,0) → (0.4,1) de #1A1F3D à #3B2E6E, en 6 bandes
SKY0, SKY1 = hexc('#1A1F3D'), hexc('#3B2E6E')
def sky(x, y):
    ux, uy = U * (x + .5) / 108, U * (y + .5) / 108
    t = (ux * .4 + uy) / (.4 * .4 + 1)
    return mix(SKY0, SKY1, min(5, int(t * 6)) / 5)

# halo : disque (51,54) r 36, #FFE9B0 à 35 % au centre → 0 ; en deux paliers discrets (en aplat, il paraît plus fort)
GLOW = hexc('#FFE9B0')
def glow(c, x, y):
    d = math.hypot(U * (x + .5) - 51, U * (y + .5) - 54)
    a = .35 * max(0, 1 - d / 36)
    return mix(c, GLOW, .07 if a > .2 else .035 if a > .1 else 0)

# lune : disque (51,54) r 27 moins disque (64,46) r 22.5 ; dégradé radial centré (44.5,44.3) r 43.2
MOON = [hexc('#FFF9EA'), hexc('#F3E7C8'), hexc('#E6D7B2'), hexc('#D9C79E')]
CRATER = hexc('#E2D2AC')
def in_moon(px, py): return (px - 51) ** 2 + (py - 54) ** 2 <= 27 ** 2 and (px - 64) ** 2 + (py - 46) ** 2 > 22.5 ** 2
CRATERS = [(36, 62, 3.6), (43, 74, 2.4), (31, 49, 2)]
def in_crater(px, py): return any((px - cx) ** 2 + (py - cy) ** 2 <= r * r for cx, cy, r in CRATERS)

img = [[glow(sky(x, y), x, y) for x in range(N)] for y in range(N)]

# étoiles du fond (blanc, opacité d'origine) et du premier plan (#FFF6D8, plus brillantes)
for sx, sy, a in [(24, 26, .7), (86, 72, .6), (30, 86, .5), (80, 22, .6), (18, 60, .5)]:
    x, y = int(sx / U), int(sy / U)
    img[y][x] = mix(img[y][x], (255, 255, 255), a)
STAR = hexc('#FFF6D8')
img[int(44 / U)][int(81 / U)] = STAR
sx, sy = int(74 / U), int(33 / U)  # la grande : petite croix
img[sy][sx] = STAR
for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)): img[sy + dy][sx + dx] = mix(img[sy + dy][sx + dx], STAR, .55)

moon = set()
for y in range(N):
    for x in range(N):
        if cover(in_moon, x, y) >= .5: moon.add((x, y))
for (x, y) in moon:
    t = math.hypot(U * (x + .5) - 44.5, U * (y + .5) - 44.3) / 43.2
    img[y][x] = MOON[0] if t < .45 else MOON[1] if t < .68 else MOON[2] if t < .84 else MOON[3]
    if cover(in_crater, x, y) >= .3: img[y][x] = CRATER
# cœur : même place et même taille que le chemin SVG (x 59 → 76.2, y 48.5 → 63.1), dégradé #FF7A8A → #F0384F, reflet blanc
HEART = [".XXX.XXX.",
         "XXXXXXXXX",
         "XXXXXXXXX",
         "XXXXXXXXX",
         ".XXXXXXX.",
         "..XXXXX..",
         "...XXX...",
         "....X...."]
H0, H1 = hexc('#FF7A8A'), hexc('#F0384F')
ox, oy = 29, 24
for j, row in enumerate(HEART):
    for i, c in enumerate(row):
        if c == 'X': img[oy + j][ox + i] = mix(H0, H1, min(1, j / 6))
for (i, j) in [(1, 1), (2, 1), (1, 2)]:  # reflet (le trait blanc en haut à gauche)
    img[oy + j][ox + i] = mix(img[oy + j][ox + i], (255, 255, 255), .6)

open(sys.argv[1], 'wb').write(bytes(v for row in img for p in row for v in (*p, 255)))
