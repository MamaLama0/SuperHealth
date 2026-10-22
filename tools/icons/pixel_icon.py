# Icône pixel-art (module Aventure) : la même lune et le même cœur, dessinés pixel par pixel. Toile de 36 × 36 = les 108 dp d'une
# icône adaptative (12 px de sortie par pixel en xxxhdpi) ; le motif tient dans les 24 px du centre (72 dp visibles).
# usage : python3 pixel_icon.py out.rgba  →  magick -size 36x36 -depth 8 rgba:out.rgba out.png
import sys, math
N = 36
O = 6  # marge : le motif est dessiné sur 24 × 24 au centre
SKY_T, SKY_B = (26, 31, 61), (59, 46, 110)
OUT = (43, 30, 58)
MOON = [(255, 249, 234), (243, 231, 200), (217, 199, 158)]
CRATER = (214, 196, 152)
HEART, HEART_D, HEART_L = (255, 90, 110), (210, 40, 70), (255, 190, 200)
STAR = (255, 246, 216)
px = [[None] * N for _ in range(N)]
for y in range(N):
    t = y / (N - 1)
    # ciel en 4 bandes (pas de dégradé lisse en pixel-art)
    k = min(3, int(t * 4)) / 3
    px[y] = [tuple(round(a + (b - a) * k) for a, b in zip(SKY_T, SKY_B)) for _ in range(N)]
def inside_moon(x, y):
    cx, cy, r = 10.8 + O, 12.6 + O, 8.4
    hx, hy, hr = 15.6 + O, 9.0 + O, 6.5
    return (x - cx) ** 2 + (y - cy) ** 2 <= r * r and (x - hx) ** 2 + (y - hy) ** 2 > hr * hr
moon = {(x, y) for y in range(N) for x in range(N) if inside_moon(x + .5, y + .5)}
for (x, y) in moon:
    d = math.hypot(x + .5 - 7.6 - O, y + .5 - 11.0 - O)
    px[y][x] = MOON[0] if d < 4.2 else MOON[1] if d < 7.4 else MOON[2]
for (x, y) in [(6 + O, 14 + O), (7 + O, 14 + O), (6 + O, 15 + O), (7 + O, 15 + O), (10 + O, 18 + O), (5 + O, 10 + O), (11 + O, 19 + O)]:
    if (x, y) in moon: px[y][x] = CRATER
# contour sombre autour de la lune
for (x, y) in list(moon):
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (x + dx, y + dy)
        if q not in moon and 0 <= q[0] < N and 0 <= q[1] < N: px[q[1]][q[0]] = OUT
heart = [".XX.XX.",
         "XLXXXXX",
         "XXXXXXX",
         ".XXXXD.",
         "..XXD..",
         "...D..."]
ox, oy = 14 + O, 9 + O
cells = {(ox + i, oy + j) for j, row in enumerate(heart) for i, c in enumerate(row) if c != '.'}
for (x, y) in cells:
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        q = (x + dx, y + dy)
        if q not in cells: px[q[1]][q[0]] = OUT
for j, row in enumerate(heart):
    for i, c in enumerate(row):
        if c == 'X': px[oy + j][ox + i] = HEART
        elif c == 'L': px[oy + j][ox + i] = HEART_L
        elif c == 'D': px[oy + j][ox + i] = HEART_D
for (x, y) in [(17 + O, 4 + O), (22 + O, 7 + O), (3 + O, 3 + O), (2 + O, 21 + O), (21 + O, 19 + O), (28, 30), (4, 12)]:
    px[y][x] = STAR
open(sys.argv[1], 'wb').write(bytes(v for row in px for p in row for v in (*p, 255)))
