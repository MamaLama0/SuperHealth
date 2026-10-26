#!/usr/bin/env bash
# Cœur du lanceur dans chaque couleur d'accent (même forme et même dégradé vertical que l'original rose) :
# res/mipmap-*/sh_launcher_foreground_<accent>.png et res/mipmap-anydpi-v26/ic_launcher_<accent>.xml.
# Le rose garde l'icône d'origine. Relancer après un changement de l'icône de base.
set -euo pipefail
cd "$(dirname "$0")/../../app/src/superhealth/res"
declare -A COLORS=([blue]=4285F4 [green]=34A853 [yellow]=F9B500 [purple]=9C5AE6 [orange]=F2803A [gray]=8E9199)
mix() { # mix <hex> <hex2> <part de hex2 0..100>
  python3 - "$1" "$2" "$3" <<'PY'
import sys
a, b, t = sys.argv[1], sys.argv[2], int(sys.argv[3]) / 100
ca = [int(a[i:i+2], 16) for i in (0, 2, 4)]; cb = [int(b[i:i+2], 16) for i in (0, 2, 4)]
print("".join("%02X" % round(x + (y - x) * t) for x, y in zip(ca, cb)))
PY
}
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  src=mipmap-$d/sh_launcher_foreground.png
  # le premier plan contient la plaque blanche et le cœur : le cœur est la partie saturée (masque doux)
  magick "$src" -colorspace HSL -channel G -separate +channel -level 15%,55% /tmp/sh_heart_mask.png
  geo=$(magick /tmp/sh_heart_mask.png -threshold 50% -format "%@" info:)   # WxH+X+Y du cœur
  w=${geo%%x*}; rest=${geo#*x}; h=${rest%%+*}
  size=$(magick identify -format "%wx%h" "$src")
  x=$(echo "$geo" | cut -d+ -f2); y=$(echo "$geo" | cut -d+ -f3)
  for name in "${!COLORS[@]}"; do
    c=${COLORS[$name]}
    top=$(mix "$c" FFFFFF 28); bottom=$(mix "$c" 000000 8)
    # dégradé posé sur le cœur, mêlé à l'original par le masque (la plaque blanche et le bord restent intacts)
    magick "$src" \( -size "$size" xc:none \( -size "${w}x${h}" "gradient:#$top-#$bottom" \) -geometry "+$x+$y" -composite +geometry \) \
      /tmp/sh_heart_mask.png -composite "mipmap-$d/sh_launcher_foreground_$name.png"
  done
done
for name in "${!COLORS[@]}" pink; do
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
