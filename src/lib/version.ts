// The app's version, read from package.json at build time. package.json is the
// single source: android/app/build.gradle reads the same field for the APK's
// versionName. Before this the dashboard showed a hand-written "v1.0.0" while
// the app actually shipping was 0.6.0-beta.x — two numbers, never in sync.
import { version } from "../../package.json";

export const APP_VERSION: string = version;
