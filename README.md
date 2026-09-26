# Orbit Dial: a Glyph Matrix clock toy for the Nothing Phone (4a) Pro

An always-on clock for the Glyph Matrix on the back of the Nothing Phone (4a) Pro, written in
Kotlin as a Glyph Toy. Twelve scales around the rim, the current hour lit, and a block orbiting
inside them for the minute.

<p align="center">
  <img src="docs/hero.webp" width="560" alt="Nothing Phone (4a) Pro, back view, showing Orbit Dial on the Glyph Matrix">
</p>

> Built for digital minimalists, this clock provides just enough information to keep you grounded
> in the present, without the noise of seconds and numbers.

The app has no interface. There is no activity in the manifest and no launcher icon: once
installed it exists only inside the Glyph interface.

## Requirements

**To run it:** a Nothing Phone (4a) Pro. The toy registers for `Glyph.DEVICE_25111p`
unconditionally and has been built and tested for that device only. On any other phone it logs a
warning and carries on; what it does there is untested.

**To build it:** JDK 17, Android SDK platform 37, and the Gradle wrapper in the repository
(Gradle 9.7.1, Android Gradle Plugin 9.4.0). A stable Android Studio may ship an older AGP; use
the wrapper from the command line if the IDE refuses the project.

## Install

Download `glyph-orbit-dial-<version>-release.apk` from the
[Releases](../../releases) page, or build it as below. Then:

```bash
adb install glyph-orbit-dial-v0.1-release.apk
```

On the phone: **Settings > Glyph Interface > Flip to Glyph > Always-on Glyph Toy**, choose
**Orbit Dial**, and turn the phone face down.

To remove it: `adb uninstall dev.glyphclock`. The app changes no system settings, so there is
nothing else to undo.

## Build from source

The Glyph Matrix SDK is not in this repository. Nothing's licence forbids redistribution, so the
build fetches it:

```bash
tools/fetch_sdk.sh        # clones the developer kit, copies glyph-matrix-sdk-2.0.aar into libs/
./gradlew assembleDebug   # -> app/build/outputs/apk/debug/glyph-orbit-dial-v0.1-debug.apk
```

Or download the aar yourself from the
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
and place it at `libs/glyph-matrix-sdk-2.0.aar`.

`./gradlew assembleRelease` signs the release APK with the credentials in a `keystore.properties`
at the project root (or `ORBIT_DIAL_STORE_FILE`, `_STORE_PASSWORD`, `_KEY_ALIAS`, `_KEY_PASSWORD`
in the environment). Without them it still builds and emits
`glyph-orbit-dial-v0.1-release-unsigned.apk`, which a phone will not install.

## How it works

### Reading the dial

<p align="center">
  <img src="docs/orbit-dial-hours.svg" width="360" alt="Orbit Dial over two hours, animated: the minute block orbits, then the lit scale moves to the next hour">
</p>

Two hours in fourteen seconds. The twelve scales are the hours, the lit one is now. The block
inside them is the minute, orbiting once an hour, and it moves in steps because the ring it
follows passes through only twelve cells (more on that below). When it comes round, the lit scale
moves on by one.

The animation is generated from the same numbers as the panel by `tools/make_preview.py`, which
also writes it as `docs/orbit-dial.lottie.json` for anywhere with a Lottie player.

### The device decides the shape of the app: AOD only, no Glyph Touch

From the developer kit's device table:

| Device | Identifier | Matrix | Glyph Touch | Toy types |
|---|---|---|---|---|
| Phone (4a) Pro | `Glyph.DEVICE_25111p` | 13 x 13 | No | AOD only |

No touch to react to and no carousel visit to animate for. The toy is chosen once as the
always-on toy, declares `com.nothing.glyph.toy.aod_support` in its manifest, and then shows the
time, redrawing when `EVENT_AOD` arrives. A clock is one of the few things that fits that brief
rather than fighting it.

`Common.is25111p()` compares `Build.MODEL` against `Glyph.DEVICE_25111p`, which is the string
`"A069P"`. `Common.getDeviceMatrixLength()` returns 13.

### The LED mask is a circle of radius `side / 2`

Only 137 of the 13 x 13 grid's 169 positions have an LED behind them. The kit publishes the
allocation as a diagram rather than as data, but the shape is exactly a disc:

```kotlin
fun hasLed(col: Int, row: Int, side: Int): Boolean {
    val c = (side - 1) / 2.0
    return hypot(col - c, row - c) <= side / 2.0
}
```

Checked cell by cell against the kit's own `image/23111_25111_LED_allocation.svg`, which marks
absent positions with `fill-opacity="0.1"`. No table is needed.

### `setMatrixFrame` brightness is 0 to 2047, not 0 to 255

This is the one worth knowing before writing a toy.

The kit documents `GlyphMatrixObject.getBrightness()` as `(0-255, default: 255)`, and that is
accurate for that class. It is not the range of the raw `int[]` handed to
`GlyphMatrixManager.setMatrixFrame(int[])`, which reaches further. The stock toys use the wider
range: reading `GlyphService: finalColors` in logcat while `com.nothing.hearthstone` is on the
panel shows values of `2047`, which is 2^11 - 1.

A toy written to the documented 0-255 therefore sends about an eighth of the value the stock ones
do, and looked washed out beside them on the panel. This app's frames read
`levels={614: 22, 2047: 6}` in the same log.

Whether 2047 is the hardware ceiling or simply the value the stock toys chose is untested.

### A brightness ratio tuned on a screen does not transfer to the LEDs

The dial's quiet scales were designed at 20 per cent of full in a browser preview. On the panel
that read as off: the eleven inactive scales disappeared and the dial became one lit mark alone
on a dark disc. The value was settled by looking at the phone.

| | of 2047 | on the panel |
|---|---|---|
| 20 per cent | 409 | scales read as off |
| 30 per cent | 614 | shipped |
| 39 per cent | 800 | clearly visible, brighter than wanted |

The LEDs' response near the bottom of their range is not a display's. Treat any brightness
fraction taken from a mock as a starting point, not a value. `tools/tuner.py` exists so that
starting point can be adjusted on the panel without a rebuild.

### `EVENT_AOD` lands on the wall-clock minute

The kit says an AOD toy receives `EVENT_AOD` "every minute". Measured on the (4a) Pro, it lands on
the minute boundary:

```
13:21:00.011   13:22:00.010   13:23:00.021
```

Within about 20 ms. A clock therefore needs no timer of its own, and the first interval after a
bind is short rather than a full minute. An unchanged frame is not pushed.

### Hour scales are drawn as rays stepping inward, not at two radii

Each scale is the rim cell for its hour, then a walk inward one grid step at a time, in whichever
of the eight directions points most nearly at the centre:

```kotlin
var (c, r) = polarCell(outer, deg, side)
for (k in 1 until length) {
    val dx = centre - c
    val dy = centre - r
    val m = hypot(dx, dy)
    if (m < 0.5) break
    c += (dx / m).roundToInt()
    r += (dy / m).roundToInt()
}
```

Re-solving the polar position at a smaller radius is the obvious alternative. At one o'clock,
radius 6 gives `(9,1)` and radius 5 gives `(9,2)`: the same column, so the mark would read as a
vertical pair rather than a stroke aimed at the middle. Stepping gives `(8,2)`, a diagonal, and
on the panel the dial reads as twelve ticks.

### A 13 x 13 grid cannot show sixty minute positions

The ring the minute mark travels passes through far fewer cells than sixty, so the mark stands
still for whole minutes at a time. For the shipped 2 x 2 mark:

| orbit (cells) | distinct positions | LEDs changing per minute, on average | longest stall |
|---|---|---|---|
| 1.0 | 6 | 0.47 | 16 minutes |
| 1.5 | 12 | 0.80 | 7 minutes |
| 2.0 | 14 | 1.00 | 6 minutes |
| 3.0 | 22 | 1.53 | 4 minutes |

`ClockFace.minutePositions` computes the second column for whatever dial is loaded, and the
service logs it at bind.

Orbit 1.5 is shipped, which keeps the mark two cells clear of the hour scales. The mark is a
2 x 2 block rather than one LED because one LED was too faint to find. A block does not travel
further than a dot: at orbit 1.5 a single LED's centre shifts 0.27 cells a minute on average and
the block's 0.20, since both follow the same rounded path and the block repeats a position
slightly more often. What changes is that 0.80 LEDs switch on or off per minute rather than 0.53,
which makes each step easier to notice.

Size and orbit are coupled. A 2 x 2 clears the hour scales at orbit 1.5 and collides with them on
24 minutes out of 60 at orbit 3.5; a 4 x 4 fills the middle of the disc and takes 16 of the 137
LEDs.

### A toy service with no launcher activity is still listed

An Android package that has never been launched sits in the stopped state, and components of a
stopped package are normally filtered out of intent resolution. An app with no activity can never
be launched, so its `com.nothing.glyph.TOY` service might never be listed.

Measured: it is listed anyway. The package reports `stopped=true notLaunched=true`, and
`cmd package query-services -a com.nothing.glyph.TOY` returns the service alongside the stock
ones. An interface-free Glyph Toy is viable.

## Project layout

```
app/src/main/AndroidManifest.xml            the toy service, its metadata, no activity
app/src/main/kotlin/dev/glyphclock/
  ClockFace.kt                              the dial as one frame of brightness; no Android imports
  Dial.kt                                   the five numbers that decide what it looks like
  ClockToyService.kt                        the bound Service the system talks to
app/src/main/res/
  drawable/ic_toy_preview.xml               the Glyph Toys list icon, generated
  values/strings.xml                        the toy's name and summary
app/src/debug/AndroidManifest.xml           adds TuneReceiver to debug builds only
app/src/debug/kotlin/dev/glyphclock/
  TuneReceiver.kt                           changes the dial over adb
tools/
  fetch_sdk.sh                              fetches the SDK aar, which is not committed
  make_preview.py                           regenerates the icon and everything in docs/ from Dial
  tuner.py                                  an HTTP front end for the adb tuning commands
docs/
  hero.webp                                 the toy on the phone, at the top of this page
  orbit-dial-hours.svg                      the animation above, two hours of the dial
  orbit-dial.lottie.json                    the same animation as Lottie
  orbit-dial.svg                            one frame of the dial, still
```

`ClockFace` has no Android imports, so the dial can be exercised without a device. The icon and
the images in `docs/` are generated from `Dial`'s defaults rather than drawn, which is what keeps
them showing the face the toy actually has.

## Tuning

Every dimension of the dial is a field on `Dial`.

| | shipped | what it does |
|---|---|---|
| `full` | 2047 | the current hour and the minute mark |
| `dim` | 614 | the other eleven scales |
| `scaleLength` | 2 | cells per scale, counted inward from the rim |
| `minuteOrbit` | 1.5 | the minute mark's ring, in cells from the centre |
| `minuteSize` | 2 | the minute mark's side, in cells |

A **debug** build can be changed over adb, without a rebuild, while the dial is showing:

```bash
adb shell am broadcast -n dev.glyphclock/.TuneReceiver -a dev.glyphclock.TUNE --ei dim 800
adb shell am broadcast -n dev.glyphclock/.TuneReceiver -a dev.glyphclock.TUNE --ef minute_orbit 2.0
adb shell am broadcast -n dev.glyphclock/.TuneReceiver -a dev.glyphclock.TUNE --ez reset true
```

The component must be named. A manifest-declared receiver does not get implicit broadcasts on
current Android, so `am broadcast -a dev.glyphclock.TUNE` reports `Broadcast completed: result=0`
and does nothing.

`TuneReceiver` and its manifest entry are both in `src/debug`, so a release build has neither and
its dial cannot be moved.

`tools/tuner.py` puts an HTTP endpoint in front of those commands, for a slider or any other
client:

```bash
python3 tools/tuner.py          # finds the (4a) Pro, listens on 127.0.0.1:8732
```

```
POST /set   {"full": 2047, "dim": 800, "scale_length": 2, "minute_orbit": 1.5, "minute_size": 2}
GET  /      {"ok": true, "serial": "…", "fields": [...]}
```

Send only the fields you want to change.

## Licence and notices

This project's code is MIT licensed; see `LICENSE`. Copyright remains with Keith Chan. Use it,
remix it, republish it, keep the notice.

The Glyph Matrix SDK is Nothing's, under their EULA, which forbids redistribution and commercial
use without written permission. The aar is not in this repository; `tools/fetch_sdk.sh` fetches
it, and that licence binds whoever does. See `NOTICE.md`.

The phone render in `docs/hero.webp` is Nothing's and is not covered by the MIT licence.

Device geometry, identifiers and matrix lengths come from Nothing's public
[GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit).

## Acknowledgements

The [GlyphMatrix Developer Kit](https://github.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit)
for the SDK, the device table and the LED allocation diagram.

Other open-source Glyph Matrix toys worth reading, several of which target the same device:
[glyph-life](https://github.com/Yuma-Eimymk2/glyph-life),
[Toyph](https://github.com/antonvidishchev/toyph),
[GlyphStopwatch](https://github.com/Sturdy7435/GlyphStopwatch),
[GlyphMarquee](https://github.com/bluehomewu/GlyphMarquee),
[GlyphMatrix-AODGeekBox](https://github.com/danissomo/GlyphMatrix-AODGeekBox),
[GlyphMatrixEditor](https://github.com/pauwma/GlyphMatrixEditor).
