# Notice

## The Glyph Matrix SDK is not in this repository

`glyph-matrix-sdk-2.0.aar` is Nothing's, under their EULA, which forbids redistribution and
forbids commercial use without written permission from Nothing. Committing it would be
redistribution, so `tools/fetch_sdk.sh` fetches it from Nothing's own repository instead. See
`libs/README.md`.

That licence applies to you too once you have fetched it. This project's MIT licence covers this
project's code and nothing else.

## Where the numbers come from

Matrix length, the device identifier and the LED allocation are read from Nothing's public
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit),
including its `image/23111_25111_LED_allocation.svg` and `image/25111_spec.svg`. The cell size
used in the icon, 0.8606 of the pitch, is measured from that allocation diagram. The icon's 7.09
per cent inset from its frame is a drawing choice for the icon.

Everything the README describes as measured was measured on a Phone (4a) Pro.

## The dial

Designed and tuned for this project. No Nothing product design is reproduced here.

## The hero image

`docs/hero.webp` shows the toy on a Nothing Phone (4a) Pro product render. The phone render is
Nothing's; it is used here to show the toy in place and is **not** covered by this project's MIT
licence. Reuse the code freely; do not reuse that image as if it were yours.
