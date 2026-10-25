#!/usr/bin/env bash
# Resize the approved artwork; no generative redraw is performed here.
set -euo pipefail
cd "$(dirname "$0")/.."
source_icon=assets/branding/super-gamehub-lite.webp
icon_resources=patches/new_files/res
icon_temp=$(mktemp -d)
trap 'rm -rf "$icon_temp"' EXIT
# Derive a white alpha silhouette for Android themed icons from the bright art.
convert "$source_icon" -fx 'max(r,max(g,b))' -level 12%,60% -alpha copy -fill white -colorize 100 "$icon_temp/mono.png"
for spec in mdpi:48:108:60 hdpi:72:162:90 xhdpi:96:216:120 xxhdpi:144:324:180 xxxhdpi:192:432:240; do
  IFS=: read -r density legacy adaptive artwork <<< "$spec"
  target="$icon_resources/mipmap-$density"
  convert "$source_icon" -resize "${legacy}x${legacy}" -quality 95 "$target/ic_launcher.webp"
  convert -size "${adaptive}x${adaptive}" xc:'#030a18' "$target/ic_launcher_bg.png"
  # Keep the whole approved square within the central safe zone. Android masks
  # can crop the adaptive canvas without cutting SUPER or the controller.
  convert "$source_icon" -resize "${artwork}x${artwork}" -background none -gravity center -extent "${adaptive}x${adaptive}" "$target/ic_launcher_fg.png"
  convert "$icon_temp/mono.png" -resize "${artwork}x${artwork}" -background none -gravity center -extent "${adaptive}x${adaptive}" "$target/ic_launcher_mono.png"
done
