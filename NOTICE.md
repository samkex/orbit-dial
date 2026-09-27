# Notice

## The Glyph Matrix SDK is not in this repository

`glyph-matrix-sdk-2.0.aar` is Nothing's, under their EULA, which forbids redistribution and
forbids commercial use without written permission from Nothing. Committing it would be
redistribution, so `tools/fetch_sdk.sh` fetches it from Nothing's own repository instead. See
`libs/README.md`.

That licence applies to you too once you have fetched it. This project's MIT licence covers this
project's code and nothing else.

## Where the numbers come from

Matrix length, the device identifiers and the LED allocation are read from Nothing's public
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

- Phone (4a) Pro: `image/23111_25111_LED_allocation.svg` and `image/25111_spec.svg`. The icon's
  cell size, 0.8606 of the pitch, is measured from that allocation diagram; its 7.09 per cent inset
  from the frame is a drawing choice for the icon.
- Phone (3): `image/23112_spec.svg`. The icon is drawn to that specification's own geometry: a 272
  circle, squares of 6.93 at a pitch of 9.4671, starting 18.931 in from the frame.

Everything a README page describes as measured was measured on the phone that page is about,
unless it says otherwise.

## The dial

Designed and tuned for this project. No Nothing product design is reproduced here.

## The hero image

`docs/hero.webp` and `docs/hero-phone-3.webp` show the toy on Nothing Phone (4a) Pro and Phone (3)
product renders. The phone renders are Nothing's; they are used here to show the toy in place and
are **not** covered by this project's MIT licence. Reuse the code freely; do not reuse those images
as if they were yours.
