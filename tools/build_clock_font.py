#!/usr/bin/env python3
"""Builds the widget's clock-digit fonts from Anton (SIL OFL 1.1).

Two fonts are written to app/src/main/res/font/:
  glass_digits.otf  - COLRv1 colour font: frosted gradient body, bright rim, top-left highlight and a
                      soft inner shade, echoing HyperOS 4's glass lockscreen clock. Android 13+ renders
                      COLRv1 natively, so a TextClock using it keeps ticking without the app running.
                      Body layers use the *foreground* colour (the TextView's text colour), so the
                      widget's tint setting recolours the glass; highlights stay white.
  solid_digits.otf  - the same outlines without colour layers.

The outlines are Anton's digits condensed, emboldened and corner-rounded to approach the HyperOS
lockscreen numerals (very heavy, narrow, slit counters). Digits share one advance so the time
never shifts sideways as it ticks.

Needs: pip install fonttools skia-pathops
Usage: python3 tools/build_clock_font.py [path/to/Anton-Regular.ttf]
"""
import os
import sys
import urllib.request

import pathops
from fontTools.fontBuilder import FontBuilder
from fontTools.pens.t2CharStringPen import T2CharStringPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
from fontTools.colorLib.builder import buildCOLR, buildCPAL
from fontTools.ttLib import TTFont
from fontTools.ttLib.tables import otTables as ot

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/font")
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


def charstring(p, width):
    pen = T2CharStringPen(width, None)
    p.draw(pen)
    return pen.getCharString()


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

    for glass in (True, False):
        build(shapes, layers, advance, height, glass)


def build(shapes, layers, advance, height, glass):
    order = [".notdef", "space"] + list(CHARS)
    extra = []
    if glass:
        for n in CHARS:
            extra += [f"{n}.{k}" for k in ("body", "inner", "rim", "light")]
    fb = FontBuilder(2048, isTTF=False)
    fb.setupGlyphOrder(order + extra)
    cmap = {ord(c): n for n, c in CHARS.items()}
    cmap[0x20] = "space"
    fb.setupCharacterMap(cmap)

    empty = pathops.Path()
    cs, metrics = {}, {}
    cs[".notdef"] = charstring(empty, advance); metrics[".notdef"] = (advance, 0)
    cs["space"] = charstring(empty, advance // 2); metrics["space"] = (advance // 2, 0)
    for n, p in shapes.items():
        w = int(bounds(p)[2] + bounds(p)[0]) if n == "colon" else advance
        cs[n] = charstring(p, w)
        metrics[n] = (w, int(bounds(p)[0]))
        if glass:
            for k, lp in layers[n].items():
                cs[f"{n}.{k}"] = charstring(lp, w)
                metrics[f"{n}.{k}"] = (w, 0)

    family = "Glass Clock Digits" if glass else "Glass Clock Digits Solid"
    ps = family.replace(" ", "")
    fb.setupCFF(ps, {"FullName": family}, cs, {})
    fb.setupHorizontalMetrics(metrics)
    # Line box == digit box, so TextView autosize fills the height with numerals, not padding.
    fb.setupHorizontalHeader(ascent=height, descent=0)
    fb.setupOS2(version=4, sTypoAscender=height, sTypoDescender=0, sTypoLineGap=0,
                usWinAscent=height, usWinDescent=0, fsSelection=0x80)  # USE_TYPO_METRICS
    fb.setupNameTable({
        "familyName": family, "styleName": "Regular",
        "copyright": "Derived from Anton, Copyright 2020 The Anton Project Authors. SIL Open Font License 1.1.",
        "licenseDescription": "This Font Software is licensed under the SIL Open Font License, Version 1.1.",
        "licenseInfoURL": "https://openfontlicense.org",
    })
    fb.setupPost()

    if glass:
        white, black = (1, 1, 1, 1), (0, 0, 0, 1)
        fb.font["CPAL"] = buildCPAL([[white, black]])
        FG, WHITE, BLACK = 0xFFFF, 0, 1

        def stop(offset, index, alpha):
            return {"StopOffset": offset, "PaletteIndex": index, "Alpha": alpha}

        def vertical(stops):  # gradient running top -> bottom of the digit box
            return {"Format": ot.PaintFormat.PaintLinearGradient,
                    "ColorLine": {"Extend": "pad", "ColorStop": stops},
                    "x0": 0, "y0": height, "x1": 0, "y1": 0, "x2": advance, "y2": height}

        def diagonal(stops):  # top-left -> bottom-right
            return {"Format": ot.PaintFormat.PaintLinearGradient,
                    "ColorLine": {"Extend": "pad", "ColorStop": stops},
                    "x0": 0, "y0": height, "x1": advance, "y1": 0, "x2": advance, "y2": height}

        def glyph(name, paint):
            return {"Format": ot.PaintFormat.PaintGlyph, "Glyph": name, "Paint": paint}

        colr = {}
        for n in CHARS:
            colr[n] = {"Format": ot.PaintFormat.PaintColrLayers, "Layers": [
                # frosted body: lighter at the top, like light falling on the glass
                glyph(f"{n}.body", vertical([stop(0, FG, 0.58), stop(0.5, FG, 0.47), stop(1, FG, 0.41)])),
                # thin darker band inside the rim gives the glass its thickness
                glyph(f"{n}.inner", vertical([stop(0, BLACK, 0.04), stop(1, BLACK, 0.12)])),
                # bright rim, strongest top-left
                glyph(f"{n}.rim", diagonal([stop(0, WHITE, 0.62), stop(0.5, WHITE, 0.30), stop(1, WHITE, 0.18)])),
                # specular crescent on the upper-left edges
                glyph(f"{n}.light", vertical([stop(0, WHITE, 0.40), stop(1, WHITE, 0.08)])),
            ]}
        fb.font["COLR"] = buildCOLR(colr, version=1)

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "glass_digits.otf" if glass else "solid_digits.otf")
    fb.save(path)
    print("wrote", path, os.path.getsize(path), "bytes; digit box", advance, "x", height)


if __name__ == "__main__":
    main()
