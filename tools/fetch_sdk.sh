#!/usr/bin/env bash
# Fetches Nothing's Glyph Matrix SDK into libs/.
#
# It is not in version control: the licence forbids redistribution, so the build fetches it.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit"
if [[ -f libs/glyph-matrix-sdk-2.0.aar ]]; then
  echo "libs/glyph-matrix-sdk-2.0.aar present, leaving it alone"
  exit 0
fi
tmp="$(mktemp -d)"
git clone --depth 1 "$REPO" "$tmp/gmdk" >/dev/null 2>&1
cp "$tmp/gmdk/glyph-matrix-sdk-2.0.aar" libs/
rm -rf "$tmp"
echo "  -> libs/glyph-matrix-sdk-2.0.aar"
