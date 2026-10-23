#!/bin/bash
# Icônes du lanceur : lune (moon_*.svg) et sa version pixel-art (pixel_icon.py), pour chaque densité.
# usage : tools/icons/build.sh  (depuis la racine du dépôt ; inkscape et ImageMagick requis)
set -e
cd "$(dirname "$0")"
RES=../../app/src/superhealth/res
python3 pixel_icon.py /tmp/sh_pixel.rgba && magick -size 54x54 -depth 8 rgba:/tmp/sh_pixel.rgba /tmp/sh_pixel54.png
for d in mdpi:1 hdpi:1.5 xhdpi:2 xxhdpi:3 xxxhdpi:4; do
  n=${d%%:*}; k=${d##*:}
  fg=$(python3 -c "print(round(108*$k))"); lg=$(python3 -c "print(round(48*$k))")
  out=$RES/mipmap-$n; mkdir -p $out
  for f in moon_fg:sh_launcher_foreground moon_bg:sh_launcher_background moon_mono:sh_launcher_monochrome; do
    inkscape ${f%%:*}.svg --export-type=png --export-filename=$out/${f##*:}.png -w $fg -h $fg 2>/dev/null
  done
  magick /tmp/sh_pixel54.png -filter point -resize ${fg}x${fg} $out/sh_launcher_pixel.png
  # anciennes icônes (avant Android 8, et aperçus) : carré arrondi et rond
  inkscape moon_bg.svg --export-type=png --export-filename=/tmp/sh_bg.png -w $((fg)) -h $((fg)) 2>/dev/null
  magick /tmp/sh_bg.png $out/sh_launcher_foreground.png -composite -gravity center -crop $(python3 -c "print(round(72*$k))")x$(python3 -c "print(round(72*$k))")+0+0 +repage -resize ${lg}x${lg} /tmp/sh_legacy.png
  r=$(python3 -c "print(round($lg*0.2))")
  magick /tmp/sh_legacy.png \( +clone -alpha extract -fill black -colorize 100 -fill white -draw "roundrectangle 0,0 $((lg-1)),$((lg-1)) $r,$r" \) -alpha off -compose copyopacity -composite $out/ic_launcher.png
  magick /tmp/sh_legacy.png \( +clone -alpha extract -fill black -colorize 100 -fill white -draw "circle $((lg/2)),$((lg/2)) $((lg/2)),0" \) -alpha off -compose copyopacity -composite $out/ic_launcher_round.png
  magick /tmp/sh_pixel54.png -gravity center -crop 36x36+0+0 +repage -filter point -resize ${lg}x${lg} /tmp/sh_plegacy.png
  magick /tmp/sh_plegacy.png \( +clone -alpha extract -fill black -colorize 100 -fill white -draw "roundrectangle 0,0 $((lg-1)),$((lg-1)) $r,$r" \) -alpha off -compose copyopacity -composite $out/ic_launcher_pixel.png
  magick /tmp/sh_plegacy.png \( +clone -alpha extract -fill black -colorize 100 -fill white -draw "circle $((lg/2)),$((lg/2)) $((lg/2)),0" \) -alpha off -compose copyopacity -composite $out/ic_launcher_pixel_round.png
done
