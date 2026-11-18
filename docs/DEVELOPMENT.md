# Development

[← back to the README](../README.md)

## Stack

`TanStack Start` `React 19` `Tailwind v4` `Capacitor` `Kotlin`

The web app (dashboard, settings) runs in Capacitor's WebView. The home-screen
widget, its background refresh and the notifications are native Kotlin under
`android/app/src/main/java/dev/pi0trdotsys/homebrewweather/widget/`. The two halves
share settings through Capacitor Preferences (`CapacitorStorage` SharedPreferences),
and the web app reaches the native side through one small Capacitor plugin,
`HbwPlugin` (`src/lib/native.ts`), which handles:

- reverse geocoding;
- notification permission;
- test notifications;
- refreshing widgets.

Weather comes straight from [Open-Meteo](https://open-meteo.com/); there is no backend.

## Run the web app

```bash
bun install
bun run dev:bun
```

`dev:bun` runs Vite on Bun's own runtime, so Node isn't needed. The plain `dev` and
`build` scripts are the ones Lovable runs; leave them as they are.

## Android

```bash
bun run build:capacitor
bunx cap sync android
cd android && ./gradlew assembleDebug
```

Unit tests:

```bash
cd android && ./gradlew testDebugUnitTest
```

Gradle does not treat `testdata/parity` as an input of the test task, so after editing a fixture
run `./gradlew cleanTestDebugUnitTest testDebugUnitTest` or it may say "up to date".
The dashboard has the same pieces as the widget: a scrubbable 24 h chart (`HourlyChart`, rain
columns, night bands, sun markers, temperature colour) and, under *./settings*, a city-contours
or skyline header (`CityBackdrop`, ports of `Terrain`/`Contours`/`Skyline`).

They cover:

- the widget's content rules;
- the rain windows, and how hard it rains (`RainAmountTest`);
- severe-weather warnings (`SevereWeatherTest`);
- UV and gusts (`UvAndGustTest`);
- the places a widget cycles through (`PlacesTest`) and the app's saved cities (`AppCitiesTest`);
- the sun about to set or rise (`SunEventsTest`);
- high-contrast colours reaching WCAG AA (`HighContrastTest`);
- tips and "on this day" (`WellbeingTipsTest`, `OnThisDayTest`);
- the pixel font;
- the rebase of a stale forecast;
- the brief, the evening preview and rain-stop;
- both languages.

### Rules the web app mirrors

Three rules exist twice, in Kotlin and in TypeScript, and have to stay identical.
Nothing checks it automatically, so change both in the same commit:

| Rule | Native | Web |
|---|---|---|
| rain windows, intensity (drizzle < 0.5 mm/h, downpour ≥ 4), amounts | `RainWindow.kt` | `src/lib/rain-window.ts` |
| severe weather (hail, storm, gusts ≥ 75 km/h, heat ≥ 35 °C) | `SevereWeather.kt` | `src/lib/severe-weather.ts` |
| forecast rebased onto the real clock | `WeatherApi.rebasedTo` | `src/lib/rebase.ts` |
| "on this day": parsing, which event, how it is cut | `OnThisDay.kt` | `src/lib/on-this-day.ts` |
| which well-being tip situation applies | `WellbeingTips.situation` | `src/lib/wellbeing.ts` |
| saved cities: order, one live slot, cap of 8 | `AppCities.with` | `withCity` in `src/lib/settings.ts` |
| rain by the minute (15-minute steps) | `RainNowcast.kt` | `src/lib/rain-nowcast.ts` |

Severe warnings are deliberately plain-voiced whatever the tone setting says, and a
storm warning replaces the rain heads-up for that storm rather than doubling it.

What does check them is `testdata/parity/*.json`: one file per rule (`rain-window`, `severe-weather`,
`on-this-day`, `saved-cities`, `wellbeing-situation`, `rain-nowcast`, `temp-color`, `terrain`,
`contours`, `skyline`), each a list of
`{ name, input, expect }` cases read by **both** `bun test` (`src/lib/parity.test.ts`,
`rain-nowcast.test.ts`) and the JVM tests (`ParityTest.kt`). Changing a rule means changing its
fixture; a case one side fails is a real disagreement between the two implementations. A case only
one side can meaningfully run (air quality and pollen exist on the native side alone) is marked
as such in the fixture. CI runs both suites.

```bash
bun test
cd android && ./gradlew testDebugUnitTest
```

### City map background

Per widget (`WidgetBackdrop`, off by default). `Terrain` fetches a 24 x 12 elevation grid
(14 km wide) from Open-Meteo's elevation endpoint (3 calls of 100 points), once per 0.1°
cell, off the render path, and keeps it in `terrain_prefs`; a failure is not retried for ten
minutes because the free tier answers 429 when asked quickly. `WidgetBackdropArt` draws
contour lines (`Contours`, marching squares) where the relief is at least 40 m, and a skyline
made from the city's name (`Skyline`) otherwise — on flat land contours only trace noise in the
elevation model — and while the grid is still coming. Everything is the theme's colour at low
alpha, lower with high contrast, rendered at half size so RemoteViews carries little memory.

### Temperature colour, paper appearance, weather scene

- `TempColor` is the one cold-to-hot scale (-15 → #7a9bff, 0 → cyan, 22 → amber, 35 → red, linear
  between) used for the hero number, the 24 h curve and its labels. The same stops and the same
  rounding are in the web app, so a number is the same colour on both.
- `WidgetAppearance` (per widget: dark, paper, auto) decides a `Palette` for one render. Code asks
  the palette for a colour (`pal.color(R.color.…)`, `pal.dim(…)`, `pal.adapt(…)`) instead of the
  resource: on paper `HighContrast.darkenToContrast` keeps the hue and darkens it until it reaches
  4.5:1 against the paper (3:1 for the hero number and fine graphics). Auto is paper while
  `weather.isDay` and dark otherwise or when unknown. The palette is built per render and passed
  down, never global; the blink tick builds the same one. Backgrounds are separate drawables
  (`widget_background_paper_*`), icons have a light colour set (`PixelIcons.LIGHT_COLORS`).
- `WidgetScene` (the *weather* background, `WidgetBackdrop.SCENE`) draws rain, snow, a storm
  (rain, a flash on every fourth frame, a bolt) or stars from `Mulberry32` + the shared blink
  frame. Only precipitation animates, so on any other sky the 60 s tick neither renders nor
  sends a bitmap for it. Marks are pure data (`marks`, `bolt`) and tested; the drawing is checked
  in the debug harness.
- The meta line is a bitmap (`MetaLineArt` + `PixelFont`, which covers the Latin alphabet and
  Polish letters; anything else is folded or shown as `?`). `ic_launcher_monochrome` is the themed
  (Android 13+) launcher icon.

### Rain by the minute, pollen, official warnings

All three ride on the refresh the widget already makes; `WeatherRepository` fetches them together,
*before* anything evaluates notifications (they used to be fetched after, so the air-quality
figure never reached the alerts).

- **Nowcast** (`RainNowcast`): Open-Meteo's `minutely_15` precipitation, 12 steps. A step is wet
  from 0.1 mm. Rain within 30 minutes (and at least 0.3 mm in that spell) is the heads-up;
  the hero says "deszcz za ok. 10 min" up to an hour ahead. When rain is expected further out,
  `WeatherWorker.scheduleRecheck` looks again in 12 minutes instead of waiting for the next
  scheduled refresh.
- **Pollen** (`Pollen`): counts from the air-quality call, Europe only. The "high" lines per kind
  are this app's own, not a medical scale.
- **IMGW** (`ImgwWarnings`, `PolandCounty`): the open-data feed lists warnings by county TERYT
  code. A place is matched to a county once per ~5 km cell through OpenStreetMap's Nominatim
  (identifying User-Agent, one request per cell, cached for good), and only inside Poland. An
  official warning for a hazard suppresses the derived notification for the same hazard.

### Alerts for every place

State and notification ids are scoped per (widget, place) (`WeatherNotifier.scopeOf`). The worker
checks the places a widget holds but isn't showing for the time-critical alerts only (rain,
storms, official warnings: `evaluate(..., criticalOnly = true)`); the brief, temperature, air
quality, UV and gust alerts stay with the place on show.

### Backup

`allowBackup` is off: saved cities and settings (a location history, in effect) stay on the
phone. The cost is that a new phone starts without them.

### Places

A widget holds up to five places (`Places.kt`, stored per widget by `WidgetPrefs`).
The header cycles them on tap; the setup screen adds and removes them. Cached
forecasts are keyed per place, so a widget never falls back to another place's
forecast offline, and switching places renders without firing notifications
(`WeatherNotifier.primeState` brings the edge-triggered state up to date silently).

### Language

Every native string lives in `Lang.kt` (`PlTexts` / `EnTexts`); every web string in
`src/lib/i18n.ts`. The setting is shared (`settings:lang`), and unset it follows the
device.

### Footer: joke, tip or this day

`settings:footer-source` (`joke` / `wellbeing` / `history`) picks what the widget footer and
the dashboard line say. Tips are `WellbeingTips.kt` (one pool per situation, same 50-character
ceiling as the jokes, checked by `WellbeingTipsTest`) and, like the jokes, are generated into
`src/lib/wellbeing.generated.ts` by `bun scripts/gen-jokes.ts`, which CI diffs. "On this day"
is a real Wikipedia event: English from Wikimedia's curated feed, Polish from the Polish
Wikipedia's day page (the feed has no Polish). It is fetched once a day and cached
(`on_this_day_prefs`), a failure is not retried for 30 minutes, and a miss falls back to a joke.

### Joke pools

The Kotlin pools (`CleanJokes.kt`, `SigmaJokes.kt`, `EnJokes.kt`) are the source of
truth, because the widget footer has a hard 50-character limit that the tests enforce.
After editing any of them, regenerate the web copy:

```bash
bun scripts/gen-jokes.ts
```

CI fails if `src/lib/jokes.generated.ts` is out of date.

### Versioning

`versionName` is read from `package.json`, the same field the dashboard displays
(`src/lib/version.ts`). `versionCode` lives in `android/app/build.gradle` and must go up
with every release.

### Release signing

Release builds are signed with a dedicated key rather than the SDK's debug
keystore. The debug keystore gets regenerated whenever it goes missing, and when it
does the signature changes silently, so Android refuses to update an installed build.
Copy [`android/keystore.properties.example`](../android/keystore.properties.example)
to `android/keystore.properties`, point it at your own keystore, then:

```bash
cd android && ./gradlew assembleRelease
```

Without that file the build still works, but it produces an unsigned APK. Both the
keystore and `keystore.properties` are gitignored. Never commit them.

The key in use since beta.12 was generated once (`homebrew-weather-release.jks`, alias
`homebrew-weather`, RSA 4096, valid 10 000 days) and a copy lives in the repository's
Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`. GitHub secrets can't be read back, so
**the secrets are not a backup**: keep the `.jks` and its password somewhere that
survives this machine. Older beta.8–beta.11 builds were signed with a different key;
that is why updating from them needs one uninstall. (Secrets named `RELEASE_*` also
exist in the repo; nothing uses them.)

### Debug build and widget harness

Debug builds carry a `.debug` application id. They install *next to* a release build
as "Homebrew Weather (debug)", so development never disturbs the widget on your home
screen. The debug app includes a widget preview harness showing:

- every icon;
- the widget at a range of sizes and densities;
- three data sets: a calm day, an eventful day, and an evening with rain and a storm
  pinned to later days.

```bash
adb shell am start -n dev.pi0trdotsys.homebrewweather.debug/dev.pi0trdotsys.homebrewweather.widget.WidgetPreviewDebugActivity
```

## CI

`.github/workflows/ci.yml` runs on every push to `main` and every pull request. It
checks three things:

- the web app typechecks;
- the joke pools match their Kotlin source;
- the widget's JVM tests pass (after `build:capacitor` and `cap sync`, since the
  Capacitor plugin module and the web bundle aren't committed).

`main` syncs straight into Lovable, so this is the first place a broken build shows up.

## Release checklist

1. Bump `version` in `package.json` and `versionCode` in `android/app/build.gradle`.
2. Build and test:

   ```bash
   bun run build:capacitor
   bunx cap sync android
   cd android && ./gradlew testDebugUnitTest assembleRelease
   ```

3. Install the APK on a phone over the previous release. It should update in place.
4. Refresh the README's widget screenshot from that phone (it needs the release
   widget on the current home screen page):

   ```bash
   powershell -ExecutionPolicy Bypass -File scripts/readme-widget-shot.ps1
   ```

5. Commit, push, and wait for CI.
6. Tag it and push the tag:

   ```bash
   git tag v0.6.0-beta.15 && git push origin v0.6.0-beta.15
   ```

   `.github/workflows/release.yml` then runs the tests, builds and signs the APK with the key
   in the Actions secrets, refuses to publish unless it is signed with the release key
   (`EXPECTED_CERT_SHA256` in the workflow) and is the version the tag names, and attaches it
   to the release, creating the release with generated notes if there isn't one. Edit the
   notes afterwards, or write the release yourself first and the workflow will only add the
   APK. The tag must equal `v` + the `package.json` version.

   Steps 2 and 3 on your own machine are still worth doing before tagging: the workflow
   proves the build and the signature, not that the update works on a real phone.

   **Run workflow** on the Actions tab (no tag) does everything except publish and leaves
   the APK as an artifact — the way to test the pipeline or the secrets. Without the
   workflow, `gh release create` with the APK you built locally does the same job.

## Widget internals

[`widget-spec.md`](widget-spec.md) covers:

- the size solver;
- the RemoteViews constraints it works around;
- the density rules ("show the exceptions, not the state");
- the real-device bugs that shaped all of the above.
