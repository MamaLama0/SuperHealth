#!/usr/bin/env bash
# Icône du lanceur « cœur anneau » : un cœur tracé comme un anneau de progression pas encore bouclé,
# dans chaque couleur d'accent, sur fond sombre (@color/ic_launcher_background).
# Écrit res/mipmap-*/sh_launcher_foreground[_<accent>].png, sh_launcher_monochrome.png, ic_launcher[_round].png
# et res/mipmap-anydpi-v26/ic_launcher_<accent>.xml. Le rose est l'icône par défaut.
# Demande ImageMagick avec le rendu SVG (librsvg). Relancer après un changement du dessin ou d'une couleur.
set -euo pipefail
cd "$(dirname "$0")/../../../app/src/superhealth/res"
BG=0B0B0C   # même valeur que ic_launcher_background (values/sh_colors.xml)
declare -A COLORS=([pink]=FF4B6E [blue]=4285F4 [green]=34A853 [yellow]=F9B500 [purple]=9C5AE6 [orange]=F2803A [gray]=8E9199)
declare -A PX=([mdpi]=1 [hdpi]=1.5 [xhdpi]=2 [xxhdpi]=3 [xxxhdpi]=4)
tmp=$(mktemp -d); trap 'rm -rf "$tmp"' EXIT

# svg <fichier> <taille px> <couleur du trait> <fond : none | square | circle>
# « none » : calque adaptatif complet (108 dp, dont 72 visibles) ; « square » / « circle » : icône d'avant Android 8 (les 72 dp visibles).
svg() {
  python3 - "$1" "$2" "$3" "$4" "$BG" <<'PY'
import sys
out, size, stroke, plate, bg = sys.argv[1:6]
# cœur dans un repère 100 x 95, départ et arrivée à la pointe du bas
segs = [((50, 88), (22, 66), (6, 50), (6, 31)), ((6, 31), (6, 17), (17, 7), (30, 7)), ((30, 7), (38.5, 7), (46, 11.5), (50, 18.5)),
        ((50, 18.5), (54, 11.5), (61.5, 7), (70, 7)), ((70, 7), (83, 7), (94, 17), (94, 31)), ((94, 31), (94, 50), (78, 66), (50, 88))]
def pt(s, t):
    u = 1 - t
    return tuple(u**3 * s[0][i] + 3 * u * u * t * s[1][i] + 3 * u * t * t * s[2][i] + t**3 * s[3][i] for i in (0, 1))
length = 0.0
for s in segs:
    prev = s[0]
    for k in range(1, 401):
        p = pt(s, k / 400); length += ((p[0] - prev[0])**2 + (p[1] - prev[1])**2) ** 0.5; prev = p
d = "M50 88" + "".join("C%s %s %s %s %s %s" % (s[1] + s[2] + s[3]) for s in segs)
# l'anneau s'interrompt en haut à droite : 57 % tracés, 13 % de vide, 30 % tracés
dash = "%.2f %.2f %.2f" % (0.57 * length, 0.13 * length, 0.30 * length)
box = "0 0 108 108" if plate == "none" else "18 18 72 72"
back = {"none": "", "square": '<rect x="18" y="18" width="72" height="72" rx="16" fill="#%s"/>' % bg,
        "circle": '<circle cx="54" cy="54" r="36" fill="#%s"/>' % bg}[plate]
open(out, "w").write(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="%s" width="%s" height="%s">%s'
    '<path d="%s" transform="translate(54 54.6) scale(0.4) translate(-50 -47.5)" fill="none" stroke="#%s" stroke-width="15" '
    'stroke-linecap="round" stroke-linejoin="round" stroke-dasharray="%s"/></svg>' % (box, size, size, back, d, stroke, dash))
PY
}
render() { magick -background none "$1" -depth 8 "PNG32:$2"; }

for d in "${!PX[@]}"; do
  full=$(python3 -c "print(round(108 * ${PX[$d]}))"); legacy=$(python3 -c "print(round(48 * ${PX[$d]}))")
  for name in "${!COLORS[@]}"; do
    out=mipmap-$d/sh_launcher_foreground_$name.png; [ "$name" = pink ] && out=mipmap-$d/sh_launcher_foreground.png
    svg "$tmp/fg.svg" "$full" "${COLORS[$name]}" none; render "$tmp/fg.svg" "$out"
  done
  svg "$tmp/mono.svg" "$full" FFFFFF none; render "$tmp/mono.svg" "mipmap-$d/sh_launcher_monochrome.png"
  svg "$tmp/sq.svg" "$legacy" "${COLORS[pink]}" square; render "$tmp/sq.svg" "mipmap-$d/ic_launcher.png"
  svg "$tmp/ci.svg" "$legacy" "${COLORS[pink]}" circle; render "$tmp/ci.svg" "mipmap-$d/ic_launcher_round.png"
done
for name in "${!COLORS[@]}"; do
  fg=sh_launcher_foreground_$name; [ "$name" = pink ] && fg=sh_launcher_foreground
  cat > mipmap-anydpi-v26/ic_launcher_$name.xml <<XML
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@mipmap/$fg"/>
    <monochrome android:drawable="@mipmap/sh_launcher_monochrome"/>
</adaptive-icon>
XML
done
