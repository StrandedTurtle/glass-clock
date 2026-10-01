#!/usr/bin/env python3
"""Builds the widget's glass clock digits as vector drawables, in several typefaces (all SIL OFL 1.1).

Why drawables and not a font: launchers inflate widgets with a *restricted* context, and TextView ignores
custom font resources there, so a custom-font TextClock silently falls back to the system font on the
home screen. Images are allowed, so each digit is a VectorDrawable with the glass baked in.

The glass, studied from the HyperOS 4 lockscreen: light comes from the top-left, so edges facing it get
a soft bevel highlight; edges facing away get a thin dark refraction line with a faint caustic inside
(so counter slits are lit on one side and shaded on the other); a fresnel glow brightens the body toward
its edges; a hairline specular edge; and a fairly transparent frosted body.

Writes, for every face in FACES and every glass colour:
  app/src/main/res/drawable/clock_<face>_<light|dark|accent>_<0-9|colon>.xml
  app/src/main/java/com/dylan/glasswidget/data/ClockFace.kt     (faces + their digit/colon aspect ratios)
  app/src/main/java/com/dylan/glasswidget/widget/GlyphTables.kt (drawable lookup)
light = frosted white glass, dark = smoked glass, accent = the system Material You accent palette.

Needs: pip install fonttools skia-pathops
Usage: python3 tools/build_clock_digits.py
"""
import glob
import os
import urllib.request
from dataclasses import dataclass, field

import pathops
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DRAWABLE = os.path.join(ROOT, "app/src/main/res/drawable")
KOTLIN = os.path.join(ROOT, "app/src/main/java/com/dylan/glasswidget")
CACHE = os.path.join(os.environ.get("TMPDIR", "/tmp"), "glassclock-fonts")
GF = "https://raw.githubusercontent.com/google/fonts/main/ofl/"

HEIGHT = 1800        # every face is scaled so its digits are this tall, so the glass looks the same


@dataclass
class Face:
    key: str
    label: str
    url: str
    axes: dict = field(default_factory=dict)  # variable-font instance
    condense: float = 1.0   # horizontal squeeze
    embolden: float = 0     # grow every edge outward (units at HEIGHT)
    round: float = 0        # corner radius added by an open (shrink-then-grow) pass
    gap: float = 44         # space between digits


FACES = [
    # Heavy, narrow, slit counters: the HyperOS lockscreen numerals.
    Face("condensed", "Condensed", GF + "anton/Anton-Regular.ttf", condense=0.66, embolden=32, round=70),
    Face("rounded", "Rounded", GF + "nunito/Nunito%5Bwght%5D.ttf", {"wght": 1000}, condense=0.92),
    Face("geometric", "Geometric", GF + "outfit/Outfit%5Bwght%5D.ttf", {"wght": 900}, condense=0.9),
    Face("serif", "Serif", GF + "dmserifdisplay/DMSerifDisplay-Regular.ttf", embolden=10),
    Face("mono", "Mono", GF + "jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf", {"wght": 800}),
    Face("tall", "Tall", GF + "bebasneue/BebasNeue-Regular.ttf", embolden=26, round=20),
]

CHARS = {"zero": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5",
         "six": "6", "seven": "7", "eight": "8", "nine": "9", "colon": ":"}

# ---- glass parameters (units at HEIGHT) ----
FRESNEL = [(0, 10, "1A"), (10, 24, "0D"), (24, 48, "06")]  # (from, to, alpha) inward glow bands
BEVEL = 30           # depth of the directional bevel highlight / shade
SPECULAR = 7         # hairline edge width

BODIES = {
    "light": [(0, "#70FFFFFF"), (0.55, "#5EFFFFFF"), (1, "#54FFFFFF")],
    "dark": [(0, "#70141618"), (0.55, "#5E141618"), (1, "#54141618")],
    # Wallpaper colours: resolved by the launcher when it draws, so it follows the wallpaper.
    "accent": [(0, "@android:color/system_accent1_50"), (0.5, "@android:color/system_accent1_100"),
               (1, "@android:color/system_accent1_200")],
}
BODY_ALPHA = {"accent": 0.5}  # translucency for the opaque palette colours


# ---- path helpers ----

def op(a, b, kind):
    return pathops.op(a, b, kind, fix_winding=True)


def stroked(p, width):
    s = pathops.Path(p)
    s.stroke(width, pathops.LineCap.ROUND_CAP, pathops.LineJoin.ROUND_JOIN, 4)
    s.convertConicsToQuads()  # boolean ops can't take the conics that round joins produce
    return s


def grow(p, d):
    return op(p, stroked(p, 2 * d), pathops.PathOp.UNION) if d > 0 else p


def shrink(p, d):
    return op(p, stroked(p, 2 * d), pathops.PathOp.DIFFERENCE) if d > 0 else p


def transformed(p, m):
    q = pathops.Path()
    p.draw(TransformPen(q.getPen(), m))
    return q


def translated(p, dx, dy):
    return transformed(p, (1, 0, 0, 1, dx, dy))


def bounds(p):
    bp = BoundsPen(None)
    p.draw(bp)
    return bp.bounds


# ---- building ----

def load(face):
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, face.key + ".ttf")
    if not os.path.exists(path):
        urllib.request.urlretrieve(face.url, path)
    font = TTFont(path)
    if face.axes:
        font = instancer.instantiateVariableFont(font, face.axes)
    return font


def shapes_for(face):
    font = load(face)
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    raw = {}
    for name, ch in CHARS.items():
        p = pathops.Path()
        gs[cmap[ord(ch)]].draw(p.getPen(glyphSet=gs))  # glyphSet decomposes composite glyphs
        raw[name] = p
    digits = [raw[n] for n in CHARS if n != "colon"]
    tall = max(bounds(p)[3] for p in digits) - min(bounds(p)[1] for p in digits)
    s = HEIGHT / tall
    shapes = {}
    for name, p in raw.items():
        p = transformed(p, (s * face.condense, 0, 0, s, 0, 0))
        p = grow(p, face.embolden)
        if face.round:
            p = grow(shrink(p, face.round), face.round)  # morphological open = rounded corners
        p.simplify()
        shapes[name] = p

    # Normalise: baseline at 0, digits share one advance so the time never shifts sideways.
    y_min = min(bounds(p)[1] for p in shapes.values())
    y_max = max(bounds(p)[3] for p in shapes.values())
    digit_w = max(bounds(p)[2] - bounds(p)[0] for n, p in shapes.items() if n != "colon")
    advance = int(digit_w + face.gap)
    widths = {}
    for name, p in shapes.items():
        x0, _, x1, _ = bounds(p)
        w = advance if name != "colon" else int(x1 - x0 + face.gap)
        shapes[name] = translated(p, (w - (x1 - x0)) / 2 - x0, -y_min)
        widths[name] = w
    return shapes, widths, int(y_max - y_min)


def glass_layers(p):
    def band(a, b):
        outer = p if a == 0 else shrink(p, a)
        return op(outer, shrink(p, b), pathops.PathOp.DIFFERENCE)
    d = BEVEL
    return {
        "body": p,
        "fresnel": [(band(a, b), alpha) for a, b, alpha in FRESNEL],
        # edges facing the top-left light
        "lit_wide": op(p, translated(p, d * 2.2, -d * 2.2), pathops.PathOp.DIFFERENCE),
        "lit": op(p, translated(p, d, -d), pathops.PathOp.DIFFERENCE),
        # edges facing away: dark refraction line at the edge, faint caustic just inside it
        "caustic": op(shrink(p, d * 0.35), translated(p, -d * 1.3, d * 1.3), pathops.PathOp.DIFFERENCE),
        "shade": op(p, translated(p, -d * 0.35, d * 0.35), pathops.PathOp.DIFFERENCE),
        "specular": band(0, SPECULAR),
    }


def svg_path(p, height):
    pen = SVGPathPen(None, ntos=lambda v: str(int(round(v))))
    p.draw(TransformPen(pen, (1, 0, 0, -1, 0, height)))  # font y-up -> drawable y-down
    return pen.getCommands()


def gradient(stops, x0, y0, x1, y1):
    items = "".join(f'\n                <item android:offset="{o}" android:color="{c}" />' for o, c in stops)
    return (f'<aapt:attr name="android:fillColor">\n'
            f'            <gradient android:type="linear" android:startX="{x0}" android:startY="{y0}" '
            f'android:endX="{x1}" android:endY="{y1}">{items}\n            </gradient>\n        </aapt:attr>')


def write_glyph(path, lay, width, height, body):
    vertical = (0, 0, 0, height)
    diagonal = (0, 0, width, height)
    solid = lambda c: [(0, c), (1, c)]
    paths = [(lay["body"], gradient(BODIES[body], *vertical), BODY_ALPHA.get(body))]
    paths += [(bp, gradient(solid(f"#{a}FFFFFF"), *vertical), None) for bp, a in lay["fresnel"]]
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
    with open(path, "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<!-- Generated by tools/build_clock_digits.py; do not edit. -->\n'
                '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
                '    xmlns:aapt="http://schemas.android.com/aapt"\n'
                f'    android:width="{width / 20:.1f}dp"\n    android:height="{height / 20:.1f}dp"\n'
                f'    android:viewportWidth="{width}"\n    android:viewportHeight="{height}">\n'
                f'{body_xml}\n</vector>\n')


def char_key(name):
    return "colon" if name == "colon" else CHARS[name]


def main():
    for old in glob.glob(os.path.join(DRAWABLE, "clock_*.xml")) + glob.glob(os.path.join(DRAWABLE, "crystal_*.xml")):
        os.remove(old)
    aspects = {}
    for face in FACES:
        shapes, widths, height = shapes_for(face)
        for name, p in shapes.items():
            lay = glass_layers(p)
            for body in BODIES:
                write_glyph(os.path.join(DRAWABLE, f"clock_{face.key}_{body}_{char_key(name)}.xml"),
                            lay, widths[name], height, body)
        aspects[face.key] = (widths["zero"] / height, widths["colon"] / height)
        print(f"{face.key:10s} digit {aspects[face.key][0]:.4f}  colon {aspects[face.key][1]:.4f}")
    write_kotlin(aspects)


def write_kotlin(aspects):
    entries = ",\n".join(
        f'    {f.label.replace(" ", "")}("{f.key}", "{f.label}", {aspects[f.key][0]:.4f}f, {aspects[f.key][1]:.4f}f)'
        for f in FACES)
    with open(os.path.join(KOTLIN, "data/ClockFace.kt"), "w") as out:
        out.write(f'''package com.dylan.glasswidget.data

// Generated by tools/build_clock_digits.py; do not edit.

/** Typefaces for the glass clock, with each face's digit and colon width/height ratios. */
enum class ClockFace(val key: String, val label: String, val digitAspect: Float, val colonAspect: Float) {{
{entries};

    companion object {{
        fun from(key: String?) = entries.firstOrNull {{ it.key == key }} ?: {FACES[0].label.replace(" ", "")}
    }}
}}
''')
    cases = []
    for f in FACES:
        sets = []
        for body in BODIES:
            ids = ", ".join(f"R.drawable.clock_{f.key}_{body}_{c}" for c in list("0123456789") + ["colon"])
            sets.append(f"        GlyphSet.{body.capitalize()} -> intArrayOf({ids})")
        cases.append(f"    ClockFace.{f.label.replace(' ', '')} -> when (set) {{\n" + "\n".join(sets) + "\n    }")
    with open(os.path.join(KOTLIN, "widget/GlyphTables.kt"), "w") as out:
        out.write('''package com.dylan.glasswidget.widget

// Generated by tools/build_clock_digits.py; do not edit.

import com.dylan.glasswidget.R
import com.dylan.glasswidget.data.ClockFace

/** Digit drawables 0-9 then the colon, for a face and glass colour. */
internal fun glyphTable(face: ClockFace, set: GlyphSet): IntArray = when (face) {
''' + "\n".join(cases) + "\n}\n")


if __name__ == "__main__":
    main()
