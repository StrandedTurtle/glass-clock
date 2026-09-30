# Glass Clock

A single Android home-screen widget styled after HyperOS 4's glass lockscreen clock, built to replace
Niagara Launcher's clock (it is an ordinary app widget, so any launcher can use it).

- **Glass digits**: a bundled COLRv1 colour font (heavy condensed numerals derived from Anton) with a
  frosted body, bright rim and top highlight. It's a real `TextClock`, so it ticks without the app
  running, and it auto-sizes to fill whatever space the launcher gives the widget.
- **Floating glass pills**: date | weather icon, temperature, condition and high/low; a details pill
  (feels like, rain chance, wind, humidity, UV, air quality); and your **next calendar event**.
  A lockscreen-style *Sunrise 6:59* line sits above the clock.
- **Live preview in settings**: the settings screen shows the real widget on your actual home-screen
  wallpaper and re-renders it as you change anything. No wallpaper permission is needed; the window
  simply lets the system wallpaper show through.
- **Weather**: Open-Meteo (no API key). Pick a city by search, or use coarse device location.
- **Tap targets**: clock, date and weather each open an app you choose; the event opens in your calendar.
- **Per-widget settings**: glass or solid digits, frosted/clear glass, tint (Frost, Smoke, light glass
  with dark text, wallpaper colours), centre/left alignment, text size, 12/24h, colon, date format, units.
- No Play Services, no storage permission. Location and calendar permissions are only asked for if you
  switch those features on.

## Build

Requires JDK 17 and the Android SDK (Android Studio does both).

```
./gradlew testDebugUnitTest assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Every push also builds in GitHub Actions (`.github/workflows/build.yml`); the debug APK is attached to
the run as an artifact. The debug APK is debug-signed and installs directly — no Play Store needed.

## Use with Niagara

1. Install the APK and open **Glass Clock** once (it schedules the weather refresh).
2. Niagara → Niagara settings → Features → Niagara Widget → **Add custom widget** → Glass Clock.
3. Long-press the clock on the home screen → **Replace clock with widget**.
4. The settings screen appears when a widget is added. To change things later, long-press → reconfigure
   if Niagara offers it, otherwise open the **Glass Clock** app icon (it jumps straight to settings).

### Keep the weather updating (HyperOS)
HyperOS kills background apps aggressively. In the app's settings screen use the **Battery** and
**Autostart** buttons: set battery to *No restrictions* and enable Autostart, or the refresh will starve.

## Design notes

- **Real backdrop blur is not possible** for a widget: the launcher composites it, and apps can't read
  the wallpaper on Android 13+. The glass is layered translucency, as dense as legibility allows. The
  frost strength lives in `tools/build_clock_font.py` (digit layers) and `tools/gen_resources.py` (pills).
- The digit font is generated: `pip install fonttools skia-pathops`, then `python3 tools/build_clock_font.py`.
  COLRv1 renders natively on Android 13+; older versions fall back to plain digits.
- Layout: `SizeMode.Exact`, so the widget lays out for its real size. It picks one of four arrangements by
  height (short, standard, tall, large), and the preview in settings renders the same thing.
- Weather and calendar events share one cache. Workers write it: a 30-minute weather refresh, a calendar
  content-change trigger, and a one-shot redraw at the next event start/end, sunrise/sunset or midnight.
  The widget only reads it.
- The `data/` package logic (API parsing, sun and event selection, formatting) has no Android
  dependencies and is covered by JVM unit tests (`app/src/test`).
- Icons are from [Lucide](https://lucide.dev) (ISC); regenerate with `python3 tools/gen_resources.py`.
  Font licence: `licenses/Anton-OFL.txt`.

## Testing checklist (on a device)

Airplane mode (last cached weather still shows) · force-stop then confirm the refresh still fires ·
reboot · change timezone/time · uninstall a chosen tap-target app (zone goes inert) · resize through
all three sizes · toggle system dark/light and wallpaper colours · leave overnight.
