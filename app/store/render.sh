#!/bin/sh
# Renders every store asset in this directory to out/, with headless Chromium — the same
# engine that prints the PDF exports, and the only renderer this repository already
# depends on. Pass CHROME=/path/to/chrome if yours is somewhere else.
#
# Sizes are Play's: 512x512 for the icon, 1024x500 for the feature graphic. The window is
# the asset, so nothing is scaled after the fact.
set -e
cd "$(dirname "$0")"

if [ -z "$CHROME" ]; then
  for c in \
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" \
    "/c/Program Files/Google/Chrome/Application/chrome.exe" \
    "$(command -v google-chrome || true)" \
    "$(command -v chromium || true)"
  do
    [ -x "$c" ] && CHROME="$c" && break
  done
fi
[ -n "$CHROME" ] || { echo "No Chrome found. Set CHROME=/path/to/chrome." >&2; exit 1; }

mkdir -p out

# Chrome resolves --screenshot against its own working directory and refuses a relative
# path on Windows, so both ends are absolute — and the URL is a file:// one, because a
# bare path loads nothing.
abs() { if command -v cygpath >/dev/null 2>&1; then cygpath -w "$PWD/$1"; else echo "$PWD/$1"; fi; }
url() { if command -v cygpath >/dev/null 2>&1; then echo "file:///$(cygpath -m "$PWD/$1")"; else echo "file://$PWD/$1"; fi; }

shot() { # shot <source.html> <out.png> <width> <height>
  "$CHROME" --headless=new --disable-gpu --hide-scrollbars \
            --screenshot="$(abs "$2")" --window-size="$3,$4" "$(url "$1")" >/dev/null 2>&1
  echo "$2"
}

shot icon.html                     out/icon-512.png                        512 512
shot feature-graphic.html          out/feature-graphic-1024x500.png       1024 500
shot feature-graphic-green.html    out/feature-graphic-1024x500-green.png 1024 500

# The Play Console wants the icon as a 32-bit PNG, and Chrome writes 24-bit whenever the
# page it shot is opaque — which this one is, deliberately. So the channel is added back
# afterwards, fully opaque. Skipped with a warning rather than failing the render: the
# feature graphics are 24-bit by spec and do not need it.
# Both names are tried, and tried by running them: on Windows `python3` is a Microsoft
# Store stub that exists, answers `command -v`, and then does nothing.
alpha_done=""
for py in python3 python; do
  if command -v "$py" >/dev/null 2>&1 && "$py" -c "import PIL" >/dev/null 2>&1; then
    "$py" -c 'from PIL import Image; Image.open("out/icon-512.png").convert("RGBA").save("out/icon-512.png")'
    alpha_done=1
    break
  fi
done
[ -n "$alpha_done" ] || echo "warning: no Pillow, out/icon-512.png left 24-bit (the console wants 32-bit)" >&2
