#!/usr/bin/env python3
"""Regenerates the Glyph Toy preview icon from ClockFace's own constants.

    python3 tools/make_preview.py

**It reads the defaults out of `Dial.kt` rather than holding its own copies.** The first
version kept a second set and they drifted within a day: the face moved to a 0..2047 brightness
range, a 2x2 minute mark and a 1.5 orbit while the icon was still drawing a single dot at 255 on
a 2.0 orbit. An icon that shows a face the toy no longer has is worse than no icon.

The drawing follows the kit's own convention, read off `image/25111_spec.svg`, which draws its
example panels with every LED position present:

    black     the canvas, a 271 circle
    #1C1C1C   an LED that exists and is off
    white     an LED that is on, scaled by its brightness

That last part is what the first version missed: it drew only the lit cells on plain black, so
the dial floated with no panel behind it, where the spec draws all 137 positions.

The cell's size as a fraction of the pitch is the kit's too. Measuring
`image/23111_25111_LED_allocation.svg` gives a 9.15 cell on a 10.63 pitch, so 0.861. The 7.09 per
cent inset from the frame is a drawing choice for this icon, chosen to leave the disc's edge
visible.
"""
import math
import sys
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
FACE = ROOT / "app/src/main/kotlin/com/kexsam/orbitdial/Dial.kt"
OUT = ROOT / "app/src/phone4aPro/res/drawable/ic_toy_preview.xml"
OUT_PHONE_3 = ROOT / "app/src/phone3/res/drawable/ic_toy_preview.xml"
DOC = ROOT / "docs/orbit-dial.svg"       # the README image, from the same numbers
ANIM = ROOT / "docs/orbit-dial-hours.svg" # the same dial over two hours, animated, for the README
LOTTIE = ROOT / "docs/orbit-dial.lottie.json"  # the same animation as Lottie, for players elsewhere

ANIM_START_HOUR = 10       # two hours from here, so the lit scale is seen to move once
ANIM_MINUTES = 120
ANIM_SECONDS_PER_MINUTE = 0.12

SIDE = 13                     # Phone (4a) Pro; the toy's preview is for that panel
W = 271.0                     # the spec's icon canvas
INSET, CELL_RATIO = 0.0709, 0.8606
UNLIT = "#1C1C1C"
HOUR, MINUTE = 10, 8          # a time where the active hour and the minute are both visible


def default(name, cast=float):
    """One of Dial's constructor defaults, so the icon and the toy cannot hold different values.

    This raised rather than guessed when the constants moved out of ClockFace into Dial, which is
    the behaviour worth keeping: an icon drawn from stale numbers still looks like an icon.
    """
    m = re.search(rf"val {name}:\s*\w+\s*=\s*([0-9.]+)", FACE.read_text())
    if not m:
        raise SystemExit(f"{name} not found in {FACE}. Has Dial changed shape?")
    return cast(m.group(1))


FULL = default("full", int)
DIM = default("dim", int)
SCALE_LENGTH = default("scaleLength", int)
MINUTE_ORBIT = default("minuteOrbit")
MINUTE_SIZE = default("minuteSize", int)

CENTRE = (SIDE - 1) / 2.0
RADIUS = SIDE / 2.0           # reproduces the 137-LED allocation exactly


def has_led(col, row):
    return math.hypot(col - CENTRE, row - CENTRE) <= RADIUS


def polar(radius, deg):
    a = math.radians(deg)
    return CENTRE + radius * math.sin(a), CENTRE - radius * math.cos(a)


def round_half_up(x):
    """ClockFace.toCell: nearest cell, halves up, with the same 1e-9 tolerance so that a corner
    exactly on a half cell lands the same way here as on the phone (see its KDoc)."""
    return math.floor(x + 0.5 + 1e-9)


DIRECTIONS = [(1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]


def scale_ray(deg, length, side=None):
    """ClockFace.scaleRay: a straight line from the rim, along the grid direction nearest the
    centre, placed where its cells sit closest to the hour's angle on average."""
    side = side or SIDE
    centre = (side - 1) / 2.0
    has = lambda c, r: math.hypot(c - centre, r - centre) <= side / 2.0
    a = math.radians(deg)
    inx, iny = -math.sin(a), math.cos(a)
    dx, dy = max(DIRECTIONS, key=lambda v: (v[0] * inx + v[1] * iny) / math.hypot(*v))

    def off(c, r):
        at = (math.degrees(math.atan2(c - centre, centre - r)) + 360) % 360
        d = abs(at - deg) % 360
        return min(d, 360 - d)

    best, best_err = [], float("inf")
    for c in range(side):
        for r in range(side):
            if not has(c, r) or has(c - dx, r - dy):
                continue
            cells = [(c + dx * k, r + dy * k) for k in range(length)]
            if not all(0 <= x < side and 0 <= y < side and has(x, y) for x, y in cells):
                continue
            err = sum(off(x, y) for x, y in cells) / length
            if err < best_err:
                best, best_err = cells, err
    return best


def frame(hour=HOUR, minute=MINUTE):
    """The same face ClockFace.render would produce for hour:minute."""
    grid = [[0] * SIDE for _ in range(SIDE)]

    def put(col, row, value):
        if 0 <= col < SIDE and 0 <= row < SIDE and has_led(col, row):
            grid[row][col] = max(grid[row][col], value)

    for h in range(12):
        for col, row in scale_ray(h * 30, SCALE_LENGTH):
            put(col, row, FULL if h == hour % 12 else DIM)

    fx, fy = polar(MINUTE_ORBIT, minute * 6)
    left = round_half_up(fx - (MINUTE_SIZE - 1) / 2.0)
    top = round_half_up(fy - (MINUTE_SIZE - 1) / 2.0)
    for dy in range(MINUTE_SIZE):
        for dx in range(MINUTE_SIZE):
            put(left + dx, top + dy, FULL)
    return grid


def main():
    grid = frame()
    pitch = W * (1 - INSET * 2) / SIDE
    cell = pitch * CELL_RATIO

    paths = []
    lit = 0
    for row in range(SIDE):
        for col in range(SIDE):
            if not has_led(col, row):
                continue
            v = grid[row][col]
            if v:
                lit += 1
                alpha = round(v / FULL * 255)
                colour = f"#{alpha:02X}FFFFFF"
            else:
                colour = f"#FF{UNLIT[1:]}"
            x = W * INSET + pitch * col + (pitch - cell) / 2
            y = W * INSET + pitch * row + (pitch - cell) / 2
            paths.append(
                f'    <path android:fillColor="{colour}"\n'
                f'        android:pathData="M{x:.2f},{y:.2f} h{cell:.2f} v{cell:.2f} '
                f'h-{cell:.2f} z" />'
            )

    OUT.write_text(
        "<!-- The Glyph Toy preview, for the Glyph Toys list. Generated by tools/make_preview.py\n"
        "     from Dial's own defaults; regenerate rather than hand-editing.\n"
        "\n"
        "     Drawn the way the kit's icon spec (image/25111_spec.svg) draws its panels: a black\n"
        "     271 circle, every LED position present, an LED that is off at #1C1C1C and one that\n"
        "     is on in white scaled by its brightness. The cell is 0.8606 of the pitch, which is\n"
        "     the ratio in the kit's own allocation diagram.\n"
        f"\n     Showing {HOUR:02d}:{MINUTE:02d}. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{int(W)}dp"\n    android:height="{int(W)}dp"\n'
        f'    android:viewportWidth="{int(W)}"\n    android:viewportHeight="{int(W)}">\n'
        '    <path android:fillColor="#FF000000"\n'
        f'        android:pathData="M{W / 2},0 A{W / 2},{W / 2} 0 1,1 {W / 2 - 0.01},0 z" />\n'
        + "\n".join(paths) + "\n</vector>\n"
    )
    total = sum(1 for r in range(SIDE) for c in range(SIDE) if has_led(c, r))
    print(f"ic_toy_preview.xml: {total} LED positions, {lit} lit "
          f"(FULL={FULL} DIM={DIM} orbit={MINUTE_ORBIT} minute={MINUTE_SIZE}x{MINUTE_SIZE})")

    total3, lit3 = write_phone3_icon()
    print(f"{OUT_PHONE_3.relative_to(ROOT)}: {total3} LED positions, {lit3} lit (Phone (3))")
    write_doc_image(grid)
    print(f"{DOC.relative_to(ROOT)}: same frame, as SVG")

    frames = [frame(ANIM_START_HOUR + m // 60, m % 60) for m in range(ANIM_MINUTES)]
    write_animated_svg(frames)
    print(f"{ANIM.relative_to(ROOT)}: {ANIM_MINUTES} minutes, "
          f"{ANIM_MINUTES * ANIM_SECONDS_PER_MINUTE:.0f} s loop, SMIL")
    write_lottie(frames)
    print(f"{LOTTIE.relative_to(ROOT)}: same animation as Lottie")


# The Phone (3) face, read from Dial.PHONE_3 the way the defaults above are read from Dial.
def phone3_value(name, cast=float):
    m = re.search(r"val PHONE_3 = Dial\(([^)]*)\)", FACE.read_text())
    if not m:
        sys.exit("Dial.PHONE_3 not found in Dial.kt")
    got = re.search(rf"{name}\s*=\s*([0-9.]+)", m.group(1))
    return cast(got.group(1)) if got else default(name, cast)


def write_phone3_icon():
    """The Phone (3) preview, drawn to the kit's own Phone (3) icon spec (image/23112_spec.svg):
    a 272 frame holding a black 272 circle; 25 by 25 LED squares of 6.93 at a pitch of 9.4671,
    the first starting 18.931 in from the frame; an LED that is off at #1C1C1C."""
    side, w = 25, 272.0
    first, pitch, square = 18.931, 9.4671, 6.93
    scale_len, orbit = phone3_value("scaleLength", int), phone3_value("minuteOrbit")
    full, dim, size = phone3_value("full", int), phone3_value("dim", int), phone3_value("minuteSize", int)
    c0 = (side - 1) / 2.0
    has = lambda c, r: math.hypot(c - c0, r - c0) <= side / 2.0
    grid = {}
    for h in range(12):
        for p in scale_ray(h * 30, scale_len, side):
            grid[p] = max(grid.get(p, 0), full if h == HOUR % 12 else dim)
    a = math.radians(MINUTE * 6)
    left = round_half_up(c0 + orbit * math.sin(a) - (size - 1) / 2.0)
    top = round_half_up(c0 - orbit * math.cos(a) - (size - 1) / 2.0)
    for dy in range(size):
        for dx in range(size):
            if has(left + dx, top + dy):
                grid[(left + dx, top + dy)] = full
    paths, lit = [], 0
    for r in range(side):
        for c in range(side):
            if not has(c, r):
                continue
            v = grid.get((c, r), 0)
            if v:
                lit += 1
                colour = f"#{round(v / full * 255):02X}FFFFFF"
            else:
                colour = f"#FF{UNLIT[1:]}"
            x, y = first + pitch * c, first + pitch * r
            paths.append(f'    <path android:fillColor="{colour}"\n'
                         f'        android:pathData="M{x:.2f},{y:.2f} h{square:.2f} v{square:.2f} h-{square:.2f} z" />')
    OUT_PHONE_3.write_text(
        "<!-- The Glyph Toy preview on the Phone (3), for the Glyph Toys list. Generated by\n"
        "     tools/make_preview.py from Dial.PHONE_3; regenerate rather than hand-editing.\n"
        "\n"
        "     Drawn to the kit's Phone (3) icon spec (image/23112_spec.svg): a black 272 circle, all\n"
        "     489 LED positions, squares of 6.93 at a pitch of 9.4671 starting 18.931 in, an LED that\n"
        "     is off at #1C1C1C and one that is on in white scaled by its brightness.\n"
        f"\n     Showing {HOUR:02d}:{MINUTE:02d}. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{int(w)}dp"\n    android:height="{int(w)}dp"\n'
        f'    android:viewportWidth="{int(w)}"\n    android:viewportHeight="{int(w)}">\n'
        '    <path android:fillColor="#FF000000"\n'
        f'        android:pathData="M{w / 2},0 A{w / 2},{w / 2} 0 1,1 {w / 2 - 0.01},0 z" />\n'
        + "\n".join(paths) + "\n</vector>\n")
    return sum(1 for r in range(side) for c in range(side) if has(c, r)), lit


def write_doc_image(grid):
    """The README image. Plain SVG so it needs no library and GitHub renders it inline.

    Same geometry and colours as the icon, drawn a little larger, on a dark page so the black
    disc has an edge to be seen against.
    """
    size = 520.0
    pitch = size * (1 - INSET * 2) / SIDE
    cell = pitch * CELL_RATIO
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{int(size)}" height="{int(size)}" '
        f'viewBox="0 0 {int(size)} {int(size)}" role="img" '
        f'aria-label="Orbit Dial showing {HOUR:02d}:{MINUTE:02d} on a 13 by 13 Glyph Matrix">',
        f'  <rect width="{int(size)}" height="{int(size)}" fill="#0a0a0a"/>',
        f'  <circle cx="{size / 2}" cy="{size / 2}" r="{size / 2}" fill="#000"/>',
    ]
    for row in range(SIDE):
        for col in range(SIDE):
            if not has_led(col, row):
                continue
            v = grid[row][col]
            x = size * INSET + pitch * col + (pitch - cell) / 2
            y = size * INSET + pitch * row + (pitch - cell) / 2
            if v:
                fill = f'fill="#fff" fill-opacity="{v / FULL:.3f}"'
            else:
                fill = f'fill="{UNLIT}"'
            parts.append(f'  <rect x="{x:.1f}" y="{y:.1f}" width="{cell:.1f}" height="{cell:.1f}" {fill}/>')
    parts.append("</svg>")
    DOC.parent.mkdir(exist_ok=True)
    DOC.write_text("\n".join(parts) + "\n")

def cell_geometry(size):
    pitch = size * (1 - INSET * 2) / SIDE
    cell = pitch * CELL_RATIO
    return pitch, cell


def write_animated_svg(frames):
    """Two hours of the dial as an animated SVG.

    SMIL rather than script, because GitHub shows README images through an <img> tag, where
    scripts never run but SMIL animation does. Only cells whose brightness changes over the loop
    get an <animate>; a cell that stays dim or stays off is a plain rect, which keeps the file
    to a few tens of kilobytes rather than a few hundred.
    """
    size = 520.0
    pitch, cell = cell_geometry(size)
    n = len(frames)
    dur = n * ANIM_SECONDS_PER_MINUTE
    key_times = ";".join(f"{i / n:.4f}" for i in range(n))
    parts = [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{int(size)}" height="{int(size)}" '
        f'viewBox="0 0 {int(size)} {int(size)}" role="img" '
        f'aria-label="Orbit Dial over two hours: the minute block orbits, then the lit scale '
        f'moves to the next hour">',
        f'  <rect width="{int(size)}" height="{int(size)}" fill="#0a0a0a"/>',
        f'  <circle cx="{size / 2}" cy="{size / 2}" r="{size / 2}" fill="#000"/>',
    ]
    animated = 0
    for row in range(SIDE):
        for col in range(SIDE):
            if not has_led(col, row):
                continue
            x = size * INSET + pitch * col + (pitch - cell) / 2
            y = size * INSET + pitch * row + (pitch - cell) / 2
            series = [f[row][col] for f in frames]
            if len(set(series)) == 1:
                v = series[0]
                fill = f'fill="#fff" fill-opacity="{v / FULL:.3f}"' if v else f'fill="{UNLIT}"'
                parts.append(f'  <rect x="{x:.1f}" y="{y:.1f}" width="{cell:.1f}" '
                             f'height="{cell:.1f}" {fill}/>')
                continue
            # An LED that is off is drawn in the unlit grey, not left black, so the animated cells
            # carry their own grey underlay and fade the white above it.
            animated += 1
            values = ";".join(f"{v / FULL:.3f}" for v in series)
            parts.append(f'  <rect x="{x:.1f}" y="{y:.1f}" width="{cell:.1f}" height="{cell:.1f}" '
                         f'fill="{UNLIT}"/>')
            parts.append(f'  <rect x="{x:.1f}" y="{y:.1f}" width="{cell:.1f}" height="{cell:.1f}" '
                         f'fill="#fff" fill-opacity="{series[0] / FULL:.3f}">')
            parts.append(f'    <animate attributeName="fill-opacity" calcMode="discrete" '
                         f'dur="{dur:.2f}s" repeatCount="indefinite" '
                         f'keyTimes="{key_times}" values="{values}"/>')
            parts.append('  </rect>')
    parts.append("</svg>")
    ANIM.write_text("\n".join(parts) + "\n")
    return animated


def write_lottie(frames, path=LOTTIE, fps=None, frames_per_minute=1):
    """The same two hours as a Lottie file, one shape layer, one group per LED.

    For anywhere with a Lottie player: a web page, an app, LottieFiles, a design tool. GitHub does
    not play Lottie in a README, which is why the SVG above exists as well. Opacity keyframes are
    hold keyframes, so the dial steps once a minute the way the panel does rather than fading.
    """
    import json
    size = 520
    pitch, cell = cell_geometry(size)
    fps = fps or 1 / ANIM_SECONDS_PER_MINUTE   # by default one frame per minute
    n = len(frames) * frames_per_minute
    groups = []
    for row in range(SIDE):
        for col in range(SIDE):
            if not has_led(col, row):
                continue
            cx = size * INSET + pitch * col + pitch / 2
            cy = size * INSET + pitch * row + pitch / 2
            series = [f[row][col] for f in frames]
            rect = {"ty": "rc", "d": 1, "s": {"a": 0, "k": [cell, cell]},
                    "p": {"a": 0, "k": [0, 0]}, "r": {"a": 0, "k": 0}}
            base = {"ty": "fl", "c": {"a": 0, "k": [0.11, 0.11, 0.11, 1]}, "o": {"a": 0, "k": 100}}
            if len(set(series)) == 1 and series[0] == 0:
                items = [rect, base]
            else:
                if len(set(series)) == 1:
                    o = {"a": 0, "k": round(100 * series[0] / FULL, 1)}
                else:
                    keys = []
                    for i, v in enumerate(series):
                        if i == 0 or v != series[i - 1]:
                            keys.append({"t": i * frames_per_minute,
                                         "s": [round(100 * v / FULL, 1)], "h": 1})
                    o = {"a": 1, "k": keys}
                white = {"ty": "fl", "c": {"a": 0, "k": [1, 1, 1, 1]}, "o": o}
                # Both fills apply to the one rect. Lottie draws a group's items top-down, so
                # the white fill is listed first to sit above the grey underlay.
                items = [rect, white, base]
            groups.append({"ty": "gr", "it": items + [
                {"ty": "tr", "p": {"a": 0, "k": [cx, cy]}, "a": {"a": 0, "k": [0, 0]},
                 "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}}
            ]})
    doc = {
        "v": "5.7.4", "fr": fps, "ip": 0, "op": n, "w": size, "h": size, "nm": "Orbit Dial",
        "layers": [
            {"ddd": 0, "ind": 1, "ty": 4, "nm": "dial", "sr": 1, "ip": 0, "op": n, "st": 0,
             "ks": {"o": {"a": 0, "k": 100}, "p": {"a": 0, "k": [0, 0, 0]},
                    "a": {"a": 0, "k": [0, 0, 0]}, "s": {"a": 0, "k": [100, 100, 100]},
                    "r": {"a": 0, "k": 0}},
             "shapes": groups},
            # The panel as a black disc, transparent outside it, so the file drops onto any
            # background (Nothing Playground draws it on a line-drawn phone).
            {"ddd": 0, "ind": 2, "ty": 4, "nm": "disc", "sr": 1, "ip": 0, "op": n, "st": 0,
             "ks": {"o": {"a": 0, "k": 100}, "p": {"a": 0, "k": [0, 0, 0]},
                    "a": {"a": 0, "k": [0, 0, 0]}, "s": {"a": 0, "k": [100, 100, 100]},
                    "r": {"a": 0, "k": 0}},
             "shapes": [{"ty": "gr", "it": [
                 {"ty": "el", "d": 1, "s": {"a": 0, "k": [size, size]}, "p": {"a": 0, "k": [0, 0]}},
                 {"ty": "fl", "c": {"a": 0, "k": [0, 0, 0, 1]}, "o": {"a": 0, "k": 100}},
                 {"ty": "tr", "p": {"a": 0, "k": [size / 2, size / 2]}, "a": {"a": 0, "k": [0, 0]},
                  "s": {"a": 0, "k": [100, 100]}, "r": {"a": 0, "k": 0}, "o": {"a": 0, "k": 100}}]}]},
        ],
    }
    path.write_text(json.dumps(doc, separators=(",", ":")))


if __name__ == "__main__":
    main()
