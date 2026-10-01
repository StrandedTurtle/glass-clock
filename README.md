# Glass Clock

A single Android home-screen widget styled after HyperOS 4's glass lockscreen clock, built to replace
Niagara Launcher's clock (it is an ordinary app widget, so any launcher can use it).

- **Glass digits**: numerals drawn as vector images with glass lighting studied from the HyperOS lockscreen
  (lit top-left edges, a refraction line and caustic on the far edges, fresnel edge glow, hairline
  specular), sized to fill whatever space the launcher gives the widget. Six fonts: Condensed (the
  HyperOS look, from Anton), Rounded (Nunito), Geometric (Outfit), Serif (DM Serif Display), Mono
  (JetBrains Mono) and Tall (Bebas Neue). Launchers ignore custom fonts in widgets, so the digits are
  images, redrawn each minute by an exact, non-waking alarm. Solid style is a system-font `TextClock`.
- **One glass card**: date | weather icon and temperature, then condition, high/low, sunrise/sunset,
  feels like, rain chance, wind, humidity, UV and air quality, wrapping onto extra lines inside the same
  glass. Your **next calendar event** sits above the clock as plain text, like the lockscreen top line.
- **Calendar sources**: calendars synced into Android (Google, Xiaomi, Samsung, Etar, DAVx⁵…) and/or
  calendar links (.ics / webcal). Proton Calendar doesn't share its events with Android, so add its
  "Share with anyone" link (calendar.proton.me → Settings → Calendars → Share with anyone → Create link).
  Repeating events, exceptions and moved occurrences are handled. Phone calendars update instantly;
  links are re-read every 10 minutes while the phone is in use (30 otherwise), or on Refresh now.
- **Live preview in settings**: the settings screen shows the real widget on your actual home-screen
  wallpaper and re-renders it as you change anything. No wallpaper permission is needed; the window
  simply lets the system wallpaper show through.
- **Weather**: via Open-Meteo (no API key). Temperatures and conditions from the UK Met Office models
  (UKV 2 km over the UK/Ireland, global 10 km elsewhere), with per-field fallback to Open-Meteo's
  default blend; rain chance is the highest hourly chance over the next 3 hours (ensemble forecast).
  Pick a city by search, or use coarse device location.
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

Every push builds in GitHub Actions (`.github/workflows/build.yml`). Pushes to the default branch also
publish a **GitHub release** (`v1.0.<run number>`) with the signed APK attached.

## Install and update with Obtainium

1. The repo is private, so Obtainium needs a token: on GitHub create a *fine-grained personal access
   token* with read-only **Contents** access to `glass-clock`, then in Obtainium open Settings and paste
   it into the GitHub personal access token field.
2. In Obtainium tap **Add app** and enter `https://github.com/StrandedTurtle/glass-clock`.
3. Install. From then on, every new release shows up as an update in Obtainium.

Releases are all signed with the same key (`app/glassclock-release.jks`), so updates install over each
other. An earlier debug build (from a zip) has a different signature: uninstall it once before the first
Obtainium install, then re-add the widget.

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
  frost strength lives in `tools/build_clock_digits.py` (digit layers) and `tools/gen_resources.py` (pills).
- The digit drawables are generated: `pip install fonttools skia-pathops`, then
  `python3 tools/build_clock_digits.py`.
- Why not a font: launchers inflate widgets with a restricted context, and TextView ignores custom font
  resources there, so a custom-font TextClock quietly falls back to the system font on the home screen.
- Layout: `SizeMode.Exact`, so the widget lays out for its real size. It picks one of four arrangements by
  height (short, standard, tall, large), and the preview in settings renders the same thing.
- Weather and calendar events share one cache. Workers write it: a 30-minute weather refresh, a calendar
  content-change trigger, and a one-shot redraw at the next event start/end, sunrise/sunset or midnight.
  The widget only reads it.
- The `data/` package logic (API parsing, sun and event selection, formatting) has no Android
  dependencies and is covered by JVM unit tests (`app/src/test`).
- Icons are from [Lucide](https://lucide.dev) (ISC); regenerate with `python3 tools/gen_resources.py`.
  Font licences: `licenses/` (all SIL OFL 1.1).

## Testing checklist (on a device)

Airplane mode (last cached weather still shows) · force-stop then confirm the refresh still fires ·
reboot · change timezone/time · uninstall a chosen tap-target app (zone goes inert) · resize through
all three sizes · toggle system dark/light and wallpaper colours · leave overnight.
