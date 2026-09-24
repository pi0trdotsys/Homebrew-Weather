# Development

[← back to the README](../README.md)

## Stack

`TanStack Start` `React 19` `Tailwind v4` `Capacitor` `Kotlin`

The web app (dashboard, settings) runs in Capacitor's WebView. The home-screen
widget, its background refresh and the notifications are native Kotlin under
`android/app/src/main/java/dev/pi0trdotsys/homebrewweather/widget/`. The two halves
share settings through Capacitor Preferences (`CapacitorStorage` SharedPreferences).
Weather comes straight from [Open-Meteo](https://open-meteo.com/); there is no backend.

## Run the web app

```bash
bun install
bun run dev
```

## Android

```bash
bun run build:capacitor
bunx cap sync android
cd android && ./gradlew assembleDebug
```

Unit tests (widget content rules, rain window, morning brief):

```bash
cd android && ./gradlew testDebugUnitTest
```

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
screen. The debug app includes a widget preview harness that shows every icon, plus the
widget at a range of sizes, densities and data sets (calm day and eventful day):

```bash
adb shell am start -n dev.pi0trdotsys.homebrewweather.debug/dev.pi0trdotsys.homebrewweather.widget.WidgetPreviewDebugActivity
```

## Widget internals

[`widget-spec.md`](widget-spec.md) covers:

- the size solver;
- the RemoteViews constraints it works around;
- the density rules ("show the exceptions, not the state");
- the real-device bugs that shaped all of the above.
