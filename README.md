# Glass Clock

A single Android home-screen widget — time, date and weather — styled as soft translucent glass, built to
replace Niagara Launcher's clock (it is an ordinary app widget, so any launcher can use it).

- **Responsive**: three layouts (compact ≈ 3×1, standard ≈ 4×2, tall ≈ 4×3) chosen by the launcher.
- **Weather**: Open-Meteo (no API key). Pick a city by search, or use coarse device location.
  The widget only ever draws cached data; a WorkManager job refreshes it every 30 min.
- **Tap targets**: clock, date and weather each open an app you choose (defaults: your alarm/clock app
  and calendar; weather opens settings until you set a city).
- **Per-widget settings**: text size, padding, glass style (Soft/Clear), corner radius, tint
  (Dynamic/Light/Dark), 12/24h, date format, °C/°F.
- Material You colours, bundled Quicksand font (SIL OFL), no Play Services, no storage permissions.

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

- **Real backdrop blur is not possible** for a widget (the launcher composites it; apps can't read the
  wallpaper on Android 13+). The glass look is layered translucency: a theme-tinted scrim, a hairline
  edge and a faint diagonal sheen. Scrim opacity lives in `widget/GlassPalette.kt` if you want it
  more or less see-through.
- All text is a real `TextClock`/`TextView` via Glance's `AndroidRemoteViews`, so the system keeps the
  clock ticking without the app running and the bundled font applies.
- Weather is cached per location, so several widgets on the same city share one fetch.
- Device location is read when you tap *Use my location* (and opportunistically by the worker). Android
  may withhold location from background apps, so the last stored fix is used when that happens.
- The `data/` package has no Android dependencies apart from the cache/worker; the API parsing,
  code→icon mapping and formatting are covered by JVM unit tests (`app/src/test`).
- Icons are from [Lucide](https://lucide.dev) (ISC); regenerate resources with `python3 tools/gen_resources.py`.
  Quicksand licence: `licenses/Quicksand-OFL.txt`.

## Testing checklist (on a device)

Airplane mode (last cached weather still shows) · force-stop then confirm the refresh still fires ·
reboot · change timezone/time · uninstall a chosen tap-target app (zone goes inert) · resize through
all three sizes · toggle system dark/light and wallpaper colours · leave overnight.
