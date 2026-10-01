#!/usr/bin/env python3
"""Builds the widget's glass clock digits as vector drawables, from Anton (SIL OFL 1.1).

Why drawables and not a font: launchers inflate widgets with a *restricted* context, and TextView ignores
custom font resources there, so a custom-font TextClock silently falls back to the system font on the
home screen. Images are allowed, so each digit is a VectorDrawable with the glass baked in:
frosted gradient body, a soft inner shade, a bright rim and a top-left highlight, echoing HyperOS 4's
glass lockscreen clock. Vectors stay crisp at any size.

Written to app/src/main/res/drawable/:
  clock_light_<0-9|colon>.xml  frosted white glass (Frost, Ink and wallpaper-colour tints)
  clock_dark_<0-9|colon>.xml   smoked dark glass (Smoke and dark-text tints)
  clock_accent_<0-9|colon>.xml glass tinted with the system Material You accent (wallpaper colours)
  crystal_<light|dark|accent>_<0-9|colon>.xml  the "Crystal" style: same shapes, more optical lighting
and the digit and colon aspect ratios are printed for GlassDigits.kt.

The outlines are Anton's digits condensed, emboldened and corner-rounded to approach the HyperOS
lockscreen numerals (very heavy, narrow, slit counters). Digits share one width so the time never
shifts sideways as it ticks.

Needs: pip install fonttools skia-pathops
Usage: python3 tools/build_clock_digits.py [path/to/Anton-Regular.ttf]
"""
import os
import sys
import urllib.request

import pathops
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/drawable")
ANTON_URL = "https://raw.githubusercontent.com/google/fonts/main/ofl/anton/Anton-Regular.ttf"

# ---- shape parameters (Anton units, UPM 2048) ----
CONDENSE = 0.66      # horizontal squeeze before emboldening
EMBOLDEN = 32        # grow every edge outward by this much
ROUND = 70           # corner radius added by the open (shrink-then-grow) pass
GAP = 44             # space between digits
RIM = 16             # width of the bright rim band
INNER = 40           # width of the inner shade band, just inside the rim
LIGHT_SHIFT = 22     # offset used to carve the top-left highlight crescent

CHARS = {"zero": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5",
         "six": "6", "seven": "7", "eight": "8", "nine": "9", "colon": ":"}


def glyph_path(glyphset, name, xform):
    p = pathops.Path()
    glyphset[name].draw(TransformPen(p.getPen(), xform))
    return p


def op(a, b, kind):
    return pathops.op(a, b, kind, fix_winding=True)


def stroked(p, width, join=pathops.LineJoin.ROUND_JOIN):
    s = pathops.Path(p)
    s.stroke(width, pathops.LineCap.ROUND_CAP, join, 4)
    s.convertConicsToQuads()  # boolean ops can't take the conics that round joins produce
    return s


def grow(p, d):
    return op(p, stroked(p, 2 * d), pathops.PathOp.UNION)


def shrink(p, d):
    return op(p, stroked(p, 2 * d), pathops.PathOp.DIFFERENCE)


def translated(p, dx, dy):
    q = pathops.Path()
    p.draw(TransformPen(q.getPen(), (1, 0, 0, 1, dx, dy)))
    return q


def bounds(p):
    bp = BoundsPen(None)
    p.draw(bp)
    return bp.bounds


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else os.path.join("/tmp", "Anton-Regular.ttf")
    if not os.path.exists(src):
        urllib.request.urlretrieve(ANTON_URL, src)
    anton = TTFont(src)
    gs = anton.getGlyphSet()

    shapes = {}
    for name in CHARS:
        p = glyph_path(gs, name, (CONDENSE, 0, 0, 1, 0, 0))
        p = grow(p, EMBOLDEN)
        p = grow(shrink(p, ROUND), ROUND)  # morphological open = rounded corners
        p.simplify()
        shapes[name] = p

    # Normalise: every glyph sits on the baseline; digits share one advance.
    y_min = min(bounds(p)[1] for p in shapes.values())
    y_max = max(bounds(p)[3] for p in shapes.values())
    digit_w = max(bounds(p)[2] - bounds(p)[0] for n, p in shapes.items() if n != "colon")
    advance = int(digit_w + GAP)
    for name, p in shapes.items():
        x0, _, x1, _ = bounds(p)
        adv = advance if name != "colon" else int(x1 - x0 + GAP)
        shapes[name] = translated(p, (adv - (x1 - x0)) / 2 - x0, -y_min)
    height = int(y_max - y_min)

    layers = {}
    for name, p in shapes.items():
        rim = op(p, shrink(p, RIM), pathops.PathOp.DIFFERENCE)
        inner = op(shrink(p, RIM), shrink(p, RIM + INNER), pathops.PathOp.DIFFERENCE)
        light = op(p, translated(p, LIGHT_SHIFT, -LIGHT_SHIFT * 1.6), pathops.PathOp.DIFFERENCE)
        layers[name] = {"body": p, "inner": inner, "rim": rim, "light": light}

    aspects = {}
    for name in CHARS:
        w = advance if name != "colon" else colon_width(shapes["colon"])
        aspects[name] = round(w / height, 4)
        crystal = crystal_layers(shapes[name])
        for body, colours in BODIES.items():
            write_vector(name, layers[name], w, height, body, colours)
            write_crystal(name, crystal, w, height, body, colours)
    print("aspect ratios (width / height):", aspects)


def colon_width(p):
    x0, _, x1, _ = bounds(p)
    return int(x1 - x0 + GAP)


def svg_path(p, height):
    pen = SVGPathPen(None, ntos=lambda v: ("%.1f" % v).rstrip("0").rstrip("."))
    p.draw(TransformPen(pen, (1, 0, 0, -1, 0, height)))  # font y-up -> drawable y-down
    return pen.getCommands()


# Gradient stops per layer: (offset, ARGB). Body colour differs between the light and dark glass.
def body_stops(rgb):
    return [(0, f"#94{rgb}"), (0.5, f"#78{rgb}"), (1, f"#69{rgb}")]


BODIES = {
    "light": {"body": body_stops("FFFFFF")},
    "dark": {"body": body_stops("141618")},
    # Wallpaper colours: the body is the system Material You accent palette, resolved by the launcher
    # when it draws the widget, so it follows wallpaper changes. Translucency comes from fillAlpha.
    "accent": {
        "body": [(0, "@android:color/system_accent1_50"), (0.5, "@android:color/system_accent1_100"),
                 (1, "@android:color/system_accent1_200")],
        "body_alpha": 0.62,
    },
}
INNER_STOPS = [(0, "#0A000000"), (1, "#1F000000")]
RIM_STOPS = [(0, "#9EFFFFFF"), (0.5, "#4DFFFFFF"), (1, "#2EFFFFFF")]
LIGHT_STOPS = [(0, "#66FFFFFF"), (1, "#14FFFFFF")]


def gradient(stops, x0, y0, x1, y1):
    items = "".join(f'\n                <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return (f'<aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="{x0}" android:startY="{y0}" '
            f'android:endX="{x1}" android:endY="{y1}">{items}\n            </gradient>\n        </aapt:attr>')


def write_vector(name, lay, width, height, body, colours):
    vertical = (0, 0, 0, height)        # top -> bottom
    diagonal = (0, 0, width, height)    # top-left -> bottom-right
    paths = [
        (lay["body"], gradient(colours["body"], *vertical), colours.get("body_alpha")),
        (lay["inner"], gradient(INNER_STOPS, *vertical), None),
        (lay["rim"], gradient(RIM_STOPS, *diagonal), None),
        (lay["light"], gradient(LIGHT_STOPS, *vertical), None),
    ]
    body_xml = "\n".join(
        f'    <path android:pathData="{svg_path(p, height)}"'
        + (f' android:fillAlpha="{a}"' if a is not None else "")
        + f'>\n        {g}\n    </path>'
        for p, g, a in paths
    )
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Generated by tools/build_clock_digits.py; do not edit. -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           f'    android:width="{width / 20:.1f}dp"\n    android:height="{height / 20:.1f}dp"\n'
           f'    android:viewportWidth="{width}"\n    android:viewportHeight="{height}">\n'
           f'{body_xml}\n</vector>\n')
    char = "colon" if name == "colon" else CHARS[name]
    with open(os.path.join(OUT, f"clock_{body}_{char}.xml"), "w") as f:
        f.write(xml)


# ---- Crystal: a second, more optical take on the glass -------------------------------------------
# Studied from the HyperOS lockscreen: no flat rim band or dark gasket. Instead light comes from the
# top-left, so edges facing it catch a soft bevel highlight and edges facing away (including the left
# side of each counter slit) fall into a thin shade; the body brightens toward its edges (fresnel glow)
# and only a hairline specular edge is drawn, strongest at the top-left.

FRESNEL = [(0, 9, "1A"), (9, 20, "0F"), (20, 34, "08"), (34, 52, "04")]  # (from, to, alpha) inward bands
BEVEL = 30          # depth of the directional bevel highlight / shade
SPECULAR = 7        # hairline edge width


def crystal_layers(p):
    def band(a, b):
        outer = p if a == 0 else shrink(p, a)
        return op(outer, shrink(p, b), pathops.PathOp.DIFFERENCE)
    d = BEVEL
    lit = op(p, translated(p, d, -d), pathops.PathOp.DIFFERENCE)           # edges facing top-left
    lit_wide = op(p, translated(p, d * 2.2, -d * 2.2), pathops.PathOp.DIFFERENCE)
    # Edges facing away from the light: a thin dark refraction line right at the edge, and just inside
    # it a faint bright caustic where light passing through the glass gathers.
    shade = op(p, translated(p, -d * 0.35, d * 0.35), pathops.PathOp.DIFFERENCE)
    caustic = op(shrink(p, d * 0.35), translated(p, -d * 1.3, d * 1.3), pathops.PathOp.DIFFERENCE)
    return {
        "body": p,
        "fresnel": [(band(a, b), alpha) for a, b, alpha in FRESNEL],
        "lit_wide": lit_wide,
        "lit": lit,
        "shade": shade,
        "caustic": caustic,
        "specular": band(0, SPECULAR),
    }


def crystal_body_stops(colours):
    """A touch milkier and more even than Glass: real frosted glass reads as one surface."""
    stops = colours["body"]
    if stops[0][1].startswith("@"):
        return stops  # accent palette: translucency via fillAlpha
    rgb = stops[0][1][3:]
    return [(0, f"#70{rgb}"), (0.55, f"#5E{rgb}"), (1, f"#54{rgb}")]


def write_crystal(name, lay, width, height, body, colours):
    vertical = (0, 0, 0, height)
    diagonal = (0, 0, width, height)
    solid = lambda c: [(0, c), (1, c)]
    paths = [(lay["body"], gradient(crystal_body_stops(colours), *vertical), colours.get("body_alpha"))]
    for band_path, alpha in lay["fresnel"]:
        paths.append((band_path, gradient(solid(f"#{alpha}FFFFFF"), *vertical), None))
    paths += [
        (lay["lit_wide"], gradient([(0, "#1AFFFFFF"), (1, "#05FFFFFF")], *diagonal), None),
        (lay["lit"], gradient([(0, "#59FFFFFF"), (0.6, "#24FFFFFF"), (1, "#0DFFFFFF")], *diagonal), None),
        (lay["caustic"], gradient([(0, "#05FFFFFF"), (1, "#2EFFFFFF")], *diagonal), None),
        (lay["shade"], gradient([(0, "#0D000000"), (1, "#38000000")], *diagonal), None),
        (lay["specular"], gradient([(0, "#E6FFFFFF"), (0.45, "#59FFFFFF"), (1, "#1FFFFFFF")], *diagonal), None),
    ]
    body_xml = "\n".join(
        f'    <path android:pathData="{svg_path(p, height)}"'
        + (f' android:fillAlpha="{a}"' if a is not None else "")
        + f'>\n        {g}\n    </path>'
        for p, g, a in paths
    )
    xml = ('<?xml version="1.0" encoding="utf-8"?>\n'
           '<!-- Generated by tools/build_clock_digits.py (Crystal style); do not edit. -->\n'
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           '    xmlns:aapt="http://schemas.android.com/aapt"\n'
           f'    android:width="{width / 20:.1f}dp"\n    android:height="{height / 20:.1f}dp"\n'
           f'    android:viewportWidth="{width}"\n    android:viewportHeight="{height}">\n'
           f'{body_xml}\n</vector>\n')
    char = "colon" if name == "colon" else CHARS[name]
    with open(os.path.join(OUT, f"crystal_{body}_{char}.xml"), "w") as f:
        f.write(xml)


if __name__ == "__main__":
    main()
