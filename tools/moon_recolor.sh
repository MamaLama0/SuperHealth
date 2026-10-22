#!/bin/bash
# Thème de base « Lune » : les sols et planètes orange (Mars) des illustrations deviennent gris-crème lunaires ; le reste
# (ciel, fusées, flammes, bleus) ne change pas. Masque : teintes rouge-orangé saturées ; couleur : luminosité passée dans un
# dégradé lunaire (violet nuit → gris → crème), comme l'icône et la Lune du voyage.
# usage : tools/moon_recolor.sh FICHIER.webp…   (réécrit les fichiers ; ImageMagick requis)
set -e
tmp=$(mktemp -d)
magick -size 1x1 xc:'#2E2B45' xc:'#6F6B86' xc:'#B4AFBE' xc:'#E2DCCF' xc:'#F7F2E6' +append -filter Cubic -resize 256x1! "$tmp/lut.png"
for f in "$@"; do
  magick "$f" -fx "(hue<0.08||hue>0.96)&&saturation>0.3" -blur 0x0.5 "$tmp/mask.png"
  magick "$f" -colorspace Gray -level 18%,82% "$tmp/lut.png" -clut "$tmp/moon.png"
  magick "$f" "$tmp/moon.png" "$tmp/mask.png" -composite -quality 92 "$f"
done
rm -rf "$tmp"
