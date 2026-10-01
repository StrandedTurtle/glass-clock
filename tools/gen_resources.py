#!/usr/bin/env python3
"""Regenerates the weather/detail icon vectors and glass pill drawables under app/src/main/res/drawable.

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
    # detail icons
    "ic_d_rain": ["M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"],
    "ic_d_wind": ["M12.8 19.6A2 2 0 1 0 14 16H2", "M17.5 8a2.5 2.5 0 1 1 2 4H2", "M9.8 4.4A2 2 0 1 1 11 8H2"],
    "ic_d_humidity": ["M7 16.3c2.2 0 4-1.83 4-4.05 0-1.16-.57-2.26-1.71-3.19S7.29 6.75 7 5.3c-.29 1.45-1.14 2.84-2.29 3.76S3 11.1 3 12.25c0 2.22 1.8 4.05 4 4.05z",
                      "M12.56 6.6A10.97 10.97 0 0 0 14 3.02c.5 2.5 2 4.9 4 6.5s3 3.5 3 5.5a6.98 6.98 0 0 1-11.91 4.97"],
    "ic_d_uv": ["M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8z", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41",
                "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41"],
    "ic_d_air": ["M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.48 19 2c1 2 2 4.18 2 8 0 5.5-4.78 10-10 10Z",
                 "M2 21c0-3 1.85-5.36 5.08-6C9.5 14.52 12 13 13 12"],
    "ic_d_feels": ["M14 4v10.54a4 4 0 1 1-4 0V4a2 2 0 0 1 4 0Z"],
    "ic_d_sunrise": ["M12 2v8", "m4.93 10.93 1.41 1.41", "M2 18h2", "M20 18h2", "m19.07 10.93-1.41 1.41",
                     "M22 22H2", "m8 6 4-4 4 4", "M16 18a4 4 0 0 0-8 0"],
    "ic_d_sunset": ["M12 10V2", "m4.93 10.93 1.41 1.41", "M2 18h2", "M20 18h2", "m19.07 10.93-1.41 1.41",
                    "M22 22H2", "m16 6-4 4-4-4", "M16 18a4 4 0 0 0-8 0"],
    "ic_d_event": ["M8 2v4", "M16 2v4", "M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z", "M3 10h18"],
    "ic_d_pollen": ["M12 7.5a4.5 4.5 0 1 1 4.5 4.5M12 7.5A4.5 4.5 0 1 0 7.5 12M12 7.5V9m-4.5 3a4.5 4.5 0 1 0 4.5 4.5M7.5 12H9m7.5 0a4.5 4.5 0 1 1-4.5 4.5m4.5-4.5H15m-3 4.5V15",
                    "M12 9a3 3 0 1 0 0 6a3 3 0 1 0 0-6z", "m8 16 1.5-1.5", "M14.5 9.5 16 8", "m8 8 1.5 1.5", "M14.5 14.5 16 16"],
}

# Moon phases 0-7 (new, waxing crescent, first quarter, waxing gibbous, full, waning gibbous, last quarter,
# waning crescent) as a disc outline with the lit part filled; northern-hemisphere orientation.
def moon_phase_paths(k):
    import math
    r, cx, cy = 8.5, 12.0, 12.0
    outline = f"M{cx} {cy - r}a{r} {r} 0 1 0 0 {2 * r}a{r} {r} 0 1 0 0 {-2 * r}z"
    if k == 0:
        return [outline], None
    if k == 4:
        return [outline], outline
    # 0 new .. pi full .. 2pi; crescents and gibbous phases exaggerated a little so they read at icon size
    theta = {1: math.pi / 3, 2: math.pi / 2, 3: 2 * math.pi / 3,
             5: 4 * math.pi / 3, 6: 3 * math.pi / 2, 7: 5 * math.pi / 3}[k]
    rx = abs(math.cos(theta)) * r           # terminator ellipse half-width
    waxing = k < 4
    top, bottom = f"M{cx} {cy - r}", f"{cx} {cy + r}"
    # lit limb: right half when waxing, left when waning
    limb = f"A{r} {r} 0 0 {1 if waxing else 0} {bottom}"
    # terminator back up: bulges into the lit half for crescents, into the dark half for gibbous
    crescent = k in (1, 7)
    sweep = (0 if crescent else 1) if waxing else (1 if crescent else 0)
    term = f"A{rx:.2f} {r} 0 0 {sweep} {cx} {cy - r}" if rx > 0.05 else f"L{cx} {cy - r}"
    return [outline], f"{top}{limb}{term}z"


def moon_vector(k):
    strokes, fill = moon_phase_paths(k)
    body = "\n".join(
        f'    <path\n        android:pathData="{d}"\n        android:strokeColor="#FFFFFFFF"\n'
        f'        android:strokeWidth="1.8"\n        android:strokeLineJoin="round" />' for d in strokes)
    if fill:
        body += f'\n    <path\n        android:pathData="{fill}"\n        android:fillColor="#FFFFFFFF" />'
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="24dp"\n    android:height="24dp"\n'
            '    android:viewportWidth="24"\n    android:viewportHeight="24">\n' + body + '\n</vector>\n')


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

# Glass pills: a 999dp radius is clamped to half the height, so one drawable is a perfect pill at any
# size. Top-to-bottom sheen + hairline rim. (top fill, bottom fill, rim) per tint and glass strength.
PILLS = {
    ("frost", "soft"): ("#4DFFFFFF", "#2EFFFFFF", "#6BFFFFFF"),
    ("frost", "clear"): ("#38FFFFFF", "#1FFFFFFF", "#57FFFFFF"),
    ("smoke", "soft"): ("#66101214", "#52101214", "#38FFFFFF"),
    ("smoke", "clear"): ("#4D101214", "#3D101214", "#2EFFFFFF"),
    ("ink", "soft"): ("#9EFFFFFF", "#80FFFFFF", "#B3FFFFFF"),
    ("ink", "clear"): ("#80FFFFFF", "#61FFFFFF", "#99FFFFFF"),
    # Material You: colours come from res/color(-night)/pill_dynamic_*.xml (system accent palette)
    ("dynamic", "soft"): ("@color/pill_dynamic_top_soft", "@color/pill_dynamic_bottom_soft", "#57FFFFFF"),
    ("dynamic", "clear"): ("@color/pill_dynamic_top_clear", "@color/pill_dynamic_bottom_clear", "#47FFFFFF"),
}

def pill(top, bottom, rim):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">\n'
            '    <corners android:radius="999dp" />\n'
            f'    <gradient android:type="linear" android:angle="270" android:startColor="{top}" android:endColor="{bottom}" />\n'
            f'    <stroke android:width="1dp" android:color="{rim}" />\n'
            '</shape>\n')

def main():
    RES.mkdir(parents=True, exist_ok=True)
    for name, paths in ICONS.items():
        (RES / f"{name}.xml").write_text(vector(paths))
    for k in range(8):
        (RES / f"ic_moon_{k}.xml").write_text(moon_vector(k))
    for (tint, variant), colours in PILLS.items():
        (RES / f"pill_{tint}_{variant}.xml").write_text(pill(*colours))
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
