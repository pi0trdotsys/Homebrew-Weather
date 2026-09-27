import { Preferences } from "@capacitor/preferences";

// Shared settings store. Backed by @capacitor/preferences — on web this is a
// thin wrapper over localStorage (group "CapacitorStorage"), on Android it's
// a real SharedPreferences file of the same name. The native widget/worker
// (android/app/.../WeatherWorker.kt) reads these exact keys directly from
// that SharedPreferences file, so key names and string encodings here are a
// contract with the native side — don't rename without updating both.
export type RefreshInterval = 15 | 30 | 60 | 180 | 360;

export const REFRESH_INTERVAL_KEY = "brew-wx:interval";
export const COORDS_KEY = "brew-wx:coords";

export type Coords = { lat: number; lon: number; name: string };

export async function loadCoords(): Promise<Coords | null> {
  const { value } = await Preferences.get({ key: COORDS_KEY });
  if (!value) return null;
  try {
    return JSON.parse(value) as Coords;
  } catch {
    return null;
  }
}

export async function saveCoords(coords: Coords): Promise<void> {
  await Preferences.set({ key: COORDS_KEY, value: JSON.stringify(coords) });
}

// Saved cities, most recently used first. The app used to remember exactly
// one location, although every widget has its own city — so checking another
// place meant searching for it again each time.
export const CITIES_KEY = "brew-wx:cities";
export const MAX_CITIES = 8;

export async function loadCities(): Promise<Coords[]> {
  const { value } = await Preferences.get({ key: CITIES_KEY });
  if (!value) return [];
  try {
    const list = JSON.parse(value) as Coords[];
    return Array.isArray(list)
      ? list.filter((c) => Number.isFinite(c.lat) && Number.isFinite(c.lon))
      : [];
  } catch {
    return [];
  }
}

export async function saveCities(cities: Coords[]): Promise<void> {
  await Preferences.set({ key: CITIES_KEY, value: JSON.stringify(cities.slice(0, MAX_CITIES)) });
}

/** Two entries are the same place if they are within ~1 km. */
export const sameCity = (a: Coords, b: Coords) =>
  Math.abs(a.lat - b.lat) < 0.01 && Math.abs(a.lon - b.lon) < 0.01;

/** [c] moved (or added) to the front of [list]. */
export function withCity(list: Coords[], c: Coords): Coords[] {
  return [c, ...list.filter((x) => !sameCity(x, c))].slice(0, MAX_CITIES);
}

// A day to open on, written by the Android side when a widget's day column is
// tapped (MainActivity.FOCUS_DAY_KEY). Read once on boot, then cleared.
export const FOCUS_DAY_KEY = "brew-wx:focus-day";

export async function takeFocusDay(): Promise<string | null> {
  const { value } = await Preferences.get({ key: FOCUS_DAY_KEY });
  if (value) await Preferences.remove({ key: FOCUS_DAY_KEY });
  return value && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : null;
}

export type NotificationSettings = {
  rainEnabled: boolean;
  highEnabled: boolean;
  highThreshold: number; // °C
  lowEnabled: boolean;
  lowThreshold: number; // °C
  swingEnabled: boolean;
  swingThreshold: number; // °C, |today.max - yesterday.max| or day-to-day delta
  aqiEnabled: boolean;
  aqiThreshold: number; // US AQI (0-500), notify when crossed
  // One morning summary instead of separate high/low/swing/AQI alerts; the
  // thresholds above then decide what the brief mentions (MorningBrief.kt).
  briefEnabled: boolean;
  briefHour: number; // local hour, 0-23
  // Tomorrow in one line, in the evening (EveningPreview in MorningBrief.kt).
  // Off by default: a new notification nobody has asked for yet.
  eveningEnabled: boolean;
  eveningHour: number; // local hour, 0-23
};

export const DEFAULT_NOTIFICATION_SETTINGS: NotificationSettings = {
  rainEnabled: true,
  highEnabled: true,
  highThreshold: 30,
  lowEnabled: true,
  lowThreshold: 0,
  swingEnabled: true,
  swingThreshold: 8,
  aqiEnabled: true,
  aqiThreshold: 100,
  briefEnabled: true,
  briefHour: 7,
  eveningEnabled: false,
  eveningHour: 20,
};

const NOTIF_KEYS: Record<keyof NotificationSettings, string> = {
  rainEnabled: "settings:notif-rain-enabled",
  highEnabled: "settings:notif-high-enabled",
  highThreshold: "settings:notif-high-threshold",
  lowEnabled: "settings:notif-low-enabled",
  lowThreshold: "settings:notif-low-threshold",
  swingEnabled: "settings:notif-swing-enabled",
  swingThreshold: "settings:notif-swing-threshold",
  aqiEnabled: "settings:notif-aqi-enabled",
  aqiThreshold: "settings:notif-aqi-threshold",
  briefEnabled: "settings:brief-enabled",
  briefHour: "settings:brief-hour",
  eveningEnabled: "settings:evening-enabled",
  eveningHour: "settings:evening-hour",
};

// The app's voice, applied to the widget footer, notifications and the
// dashboard joke (Tone.kt on the native side reads this same key). "rude" is
// what the app did before this setting existed, so it stays the default.
export type Tone = "clean" | "sigma" | "rude";
export const TONE_KEY = "settings:tone";
export const DEFAULT_TONE: Tone = "rude";

export async function loadTone(): Promise<Tone> {
  const { value } = await Preferences.get({ key: TONE_KEY });
  return value === "clean" || value === "sigma" || value === "rude" ? value : DEFAULT_TONE;
}

export async function saveTone(tone: Tone): Promise<void> {
  await Preferences.set({ key: TONE_KEY, value: tone });
}

export async function loadRefreshInterval(): Promise<RefreshInterval> {
  const { value } = await Preferences.get({ key: REFRESH_INTERVAL_KEY });
  const n = Number(value);
  return ([15, 30, 60, 180, 360] as const).includes(n as RefreshInterval)
    ? (n as RefreshInterval)
    : 30;
}

export async function saveRefreshInterval(v: RefreshInterval): Promise<void> {
  await Preferences.set({ key: REFRESH_INTERVAL_KEY, value: String(v) });
}

export async function loadNotificationSettings(): Promise<NotificationSettings> {
  const entries = await Promise.all(
    (Object.keys(NOTIF_KEYS) as Array<keyof NotificationSettings>).map(async (field) => {
      const { value } = await Preferences.get({ key: NOTIF_KEYS[field] });
      return [field, value] as const;
    }),
  );
  const result = { ...DEFAULT_NOTIFICATION_SETTINGS };
  for (const [field, raw] of entries) {
    if (raw == null) continue;
    if (typeof DEFAULT_NOTIFICATION_SETTINGS[field] === "boolean") {
      (result[field] as boolean) = raw === "true";
    } else {
      const n = Number(raw);
      if (!Number.isNaN(n)) (result[field] as number) = n;
    }
  }
  return result;
}

export async function saveNotificationSettings(s: NotificationSettings): Promise<void> {
  await Promise.all(
    (Object.keys(NOTIF_KEYS) as Array<keyof NotificationSettings>).map((field) =>
      Preferences.set({ key: NOTIF_KEYS[field], value: String(s[field]) }),
    ),
  );
}
