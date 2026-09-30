#!/usr/bin/env python3
"""Regenerates the weather icon vectors and glass-edge drawables under app/src/main/res/drawable.

Icon geometry is from Lucide (https://lucide.dev, ISC licence), drawn as 24dp stroked vectors so the
widget can tint them with its text colour. Run from the repo root:  python3 tools/gen_resources.py
Pass --sheet out.html to also write an SVG contact sheet for eyeballing the icons.
"""
import sys, pathlib

RES = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"

CLOUD_TOP = "M4 14.899A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 2.5 8.242"
ICONS = {
    "ic_wx_clear_day": [
        "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z",
        "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41",
        "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41",
    ],
    "ic_wx_clear_night": ["M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"],
    "ic_wx_partly_day": [
        "M12 2v2", "m4.93 4.93 1.41 1.41", "M20 12h2", "m19.07 4.93-1.41 1.41",
        "M15.947 12.65a4 4 0 0 0-5.925-4.128",
        "M13 22H7a5 5 0 1 1 4.9-6H13a3 3 0 0 1 0 6Z",
    ],
    "ic_wx_partly_night": [
        "M10.188 8.5A6 6 0 0 1 16 4a1 1 0 0 0 6 6 6 6 0 0 1-3 5.197",
        "M13 16a3 3 0 1 1 0 6H7a5 5 0 1 1 4.9-6Z",
    ],
    "ic_wx_cloudy": ["M17.5 19H9a7 7 0 1 1 6.71-9h1.79a4.5 4.5 0 1 1 0 9Z"],
    "ic_wx_fog": [CLOUD_TOP, "M16 17H7", "M17 21H9"],
    "ic_wx_drizzle": [CLOUD_TOP, "M8 19v1", "M8 14v1", "M16 19v1", "M16 14v1", "M12 21v1", "M12 16v1"],
    "ic_wx_rain": [CLOUD_TOP, "M16 14v6", "M8 14v6", "M12 16v6"],
    "ic_wx_snow": [CLOUD_TOP, "M8 15h.01", "M8 19h.01", "M12 17h.01", "M12 21h.01", "M16 15h.01", "M16 19h.01"],
    "ic_wx_storm": ["M6 16.326A7 7 0 1 1 15.71 8h1.79a4.5 4.5 0 0 1 .5 8.973", "m13 12-3 5h4l-3 5"],
}

def vector(paths):
    body = "\n".join(
        f'    <path\n        android:pathData="{d}"\n        android:strokeColor="#FFFFFFFF"\n'
        f'        android:strokeWidth="1.8"\n        android:strokeLineCap="round"\n'
        f'        android:strokeLineJoin="round" />'
        for d in paths)
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n' + body + '\n</vector>\n')

# Glass edge: a faint top-left sheen plus a hairline stroke, at each supported corner radius.
VARIANTS = {"clear": ("#2EFFFFFF", "#70FFFFFF"), "soft": ("#1AFFFFFF", "#45FFFFFF")}
RADII = range(16, 37, 4)

def ring(radius, sheen, stroke):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
            f'    <corners android:radius="{radius}dp" />\n'
            f'    <gradient android:type="linear" android:angle="315" android:startColor="{sheen}" android:endColor="#00FFFFFF" />\n'
            f'    <stroke android:width="1dp" android:color="{stroke}" />\n'
            '</shape>\n')

def main():
    RES.mkdir(parents=True, exist_ok=True)
    for name, paths in ICONS.items():
        (RES / f"{name}.xml").write_text(vector(paths))
    for v, (sheen, stroke) in VARIANTS.items():
        for r in RADII:
            (RES / f"glass_edge_{v}_{r}.xml").write_text(ring(r, sheen, stroke))
    if "--sheet" in sys.argv:
        out = pathlib.Path(sys.argv[sys.argv.index("--sheet") + 1])
        def svg(paths):
            inner = "".join('<path d="%s"/>' % d for d in paths)
            return ('<svg width="96" height="96" viewBox="0 0 24 24" fill="none" stroke="#fff" '
                    'stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">%s</svg>' % inner)
        cells = "".join(
            '<div style="display:inline-block;margin:10px;text-align:center;font:12px sans-serif;color:#eee">'
            '%s<br>%s</div>' % (svg(p), n[6:]) for n, p in ICONS.items())
        out.write_text(f'<body style="background:#334;margin:0;padding:10px">{cells}</body>')

if __name__ == "__main__":
    main()
