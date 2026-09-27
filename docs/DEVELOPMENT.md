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

They cover:

- the widget's content rules;
- the rain windows;
- the pixel font;
- the rebase of a stale forecast;
- the brief, the evening preview and rain-stop;
- both languages.

### Language

Every native string lives in `Lang.kt` (`PlTexts` / `EnTexts`); every web string in
`src/lib/i18n.ts`. The setting is shared (`settings:lang`), and unset it follows the
device.

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

5. Commit, push, check CI, then create the GitHub release with the APK.

## Widget internals

[`widget-spec.md`](widget-spec.md) covers:

- the size solver;
- the RemoteViews constraints it works around;
- the density rules ("show the exceptions, not the state");
- the real-device bugs that shaped all of the above.
