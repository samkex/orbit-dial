#!/usr/bin/env bash
# Fetches Nothing's Glyph Matrix SDK into libs/.
#
# It is not in version control: the licence forbids redistribution, so the build fetches it.
#
# Pinned to one commit of the developer kit and checked against the aar's SHA-256, so a clean
# checkout builds against the same SDK the toy was written and tested with. To move to a newer
# kit, change both values here together and re-test on the phone.
set -euo pipefail
cd "$(dirname "$0")/.."
REPO="https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit"
SDK_COMMIT="999b1143a6bff0dd79db11d21b92c15c56a346b1"
AAR="glyph-matrix-sdk-2.0.aar"
AAR_SHA256="be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29"

if [[ -f "libs/$AAR" ]]; then
  echo "libs/$AAR present, leaving it alone"
  exit 0
fi
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
git clone --quiet --filter=blob:none --no-checkout "$REPO" "$tmp/gmdk"
git -C "$tmp/gmdk" checkout --quiet "$SDK_COMMIT" -- "$AAR"

actual="$(shasum -a 256 "$tmp/gmdk/$AAR" | cut -d' ' -f1)"
if [[ "$actual" != "$AAR_SHA256" ]]; then
  echo "$AAR at $SDK_COMMIT has SHA-256 $actual, expected $AAR_SHA256" >&2
  exit 1
fi
cp "$tmp/gmdk/$AAR" libs/
echo "  -> libs/$AAR (kit ${SDK_COMMIT:0:12}, sha256 verified)"
