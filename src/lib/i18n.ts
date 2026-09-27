import {
  createContext,
  createElement,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { Preferences } from "@capacitor/preferences";

// One language for the whole app — this dashboard, the widget and the
// notifications. Stored under `settings:lang`, the key the native side reads
// (Lang.kt). Unset, it follows the device: Polish on a Polish phone or
// browser, English anywhere else.
//
// Terminal decoration ("$ weather --forecast 7", "./settings", "grep") stays
// as it is in both languages — it's the app's costume, not its sentences.

export type Lang = "pl" | "en";
export const LANG_KEY = "settings:lang";

export function deviceLang(): Lang {
  if (typeof navigator === "undefined") return "en";
  return navigator.language?.toLowerCase().startsWith("pl") ? "pl" : "en";
}

export async function loadLang(): Promise<Lang> {
  const { value } = await Preferences.get({ key: LANG_KEY });
  return value === "pl" || value === "en" ? value : deviceLang();
}

export async function saveLang(lang: Lang): Promise<void> {
  await Preferences.set({ key: LANG_KEY, value: lang });
}

type RainKind = "rain" | "snow" | "thunder";

const EN = {
  // dashboard
  geoError: (msg: string) => `${msg}. Try [locate()] again or grep a city above.`,
  locating: "polling GPS…",
  locateBtn: "[ locate() ]",
  locatingBtn: "locating…",
  searchPlaceholder: "search city_",
  fetchFailed: (msg: string) => `fetch failed: ${msg}`,
  footer: "weather via open-meteo · no cookies · no tracking · brewed with ♥ in the terminal",
  savedCities: "saved",
  removeCity: (name: string) => `remove ${name}`,
  // first launch
  welcomeTitle: "Where should the forecast be for?",
  welcomeBody:
    "Type a city — or let the app use your location. It asks for permission only if you choose that.",
  useMyLocation: "[ use my location ]",
  // hero / status
  feelsLike: (t: number) => `feels like ${t}°`,
  online: "online",
  offline: "offline",
  servingCache: "serving saved forecast",
  staleRefreshing: "stale, refreshing",
  sync: "sync",
  every: (min: number) => `every ${min} min`,
  // rain
  rainNoun: { rain: "rain", snow: "snow", thunder: "thunderstorm" } as Record<RainKind, string>,
  rainOngoing: { rain: "raining", snow: "snowing", thunder: "storming" } as Record<
    RainKind,
    string
  >,
  rainEasing: (verb: string, end: string) => `${verb}, easing off around ${end}`,
  rainNoEnd: (verb: string) => `${verb}, no end in sight`,
  rainWithinHourUntil: (noun: string, end: string) => `${noun} within the hour, until about ${end}`,
  rainWithinHour: (noun: string) => `${noun} within the hour`,
  rainFromUntil: (noun: string, start: string, end: string) =>
    `${noun} from ${start} until about ${end}`,
  rainFrom: (noun: string, start: string) => `${noun} from ${start}`,
  rainRange: (noun: string, start: string, end: string) => `${noun} ${start}–${end}`,
  againLater: ", again later",
  bandMax: (p: number) => `max ${p}%`,
  // days
  today: "today",
  tomorrow: "tomorrow",
  dow: ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"],
  dayHours: "hours",
  close: "[ close ]",
  noRainThatDay: "no likely rain that day",
  // terminal details
  tFeels: "feels",
  tWind: "wind",
  tHumidity: "humidity",
  tPressure: "pressure",
  tSunrise: "sunrise",
  tSunset: "sunset",
  // settings
  refresh: "REFRESH",
  refreshLabel: "auto-refresh interval (app + home-screen widget)",
  notifications: "NOTIFICATIONS",
  rainSoon: "rain — a heads-up before it starts, and when a long spell is about to stop",
  briefToggle: "morning brief — one summary instead of separate alerts",
  from: "↳ from",
  briefOn: "// the brief is on: the thresholds below decide what it mentions",
  briefOff: "// the brief is off: each threshold below sends its own alert",
  eveningToggle: "evening preview — tomorrow in one line",
  high: "high temperature",
  low: "low temperature",
  swing: "big day-to-day swing",
  air: "air quality",
  threshold: "↳ threshold",
  minDelta: "↳ min. delta",
  aqiThreshold: "↳ AQI threshold",
  testNotifications: "[ send test notifications ]",
  testSending: "sending…",
  testSent: (n: number) => `// sent ${n} — check the notification shade`,
  testNoPermission: "// notifications are blocked for this app — allow them in system settings",
  testFailed: (msg: string) => `// couldn't send: ${msg}`,
  testWebOnly: "// notifications come from the Android app; this is the browser version",
  permissionAsked: "// notifications need permission — allow it in the prompt",
  tone: "TONE",
  toneHints: {
    clean: "wry but polite footer, plain notifications",
    sigma: "sigma footer, plain notifications",
    rude: "sigma footer, deliberately crude notifications",
  } as Record<"clean" | "sigma" | "rude", string>,
  toneApplies: "widget, notifications and this dashboard",
  language: "LANGUAGE",
  languageHint: "// the whole app: dashboard, widget and notifications",
  nativeNote1: "// on Android these are read directly by the home-screen widget's",
  nativeNote2: "// background worker — no need to open the app for alerts to fire.",
  // about
  aboutName: "homebrew-weather — pixel-art weather forecast for developers",
  aboutSynopsis:
    "A tiny weather app styled like a 1980s phosphor CRT terminal: 16×16 hand-drawn pixel icons, a joke for every condition, and a home-screen widget that speaks up only when the sky has something to say.",
  aboutData1: "Weather + city search:",
  aboutData2:
    "— free, no key, no tracking. Your location, if you share it, goes to Open-Meteo as coordinates; the place name comes from your phone (in the browser: OpenStreetMap).",
  aboutBugs: "// TODO: teach it to make coffee",
};

type Strings = typeof EN;

const PL: Strings = {
  geoError: (msg) => `${msg}. Spróbuj [locate()] jeszcze raz albo wyszukaj miasto wyżej.`,
  locating: "szukam GPS…",
  locateBtn: "[ locate() ]",
  locatingBtn: "szukam…",
  searchPlaceholder: "szukaj miasta_",
  fetchFailed: (msg) => `nie udało się pobrać prognozy: ${msg}`,
  footer: "pogoda z open-meteo · bez ciasteczek · bez śledzenia · uwarzone z ♥ w terminalu",
  savedCities: "zapisane",
  removeCity: (name) => `usuń ${name}`,
  welcomeTitle: "Dla jakiego miejsca prognoza?",
  welcomeBody:
    "Wpisz miasto — albo pozwól aplikacji użyć lokalizacji. O zgodę zapyta tylko, jeśli to wybierzesz.",
  useMyLocation: "[ użyj mojej lokalizacji ]",
  feelsLike: (t) => `odczuwalna ${t}°`,
  online: "online",
  offline: "offline",
  servingCache: "zapisana prognoza",
  staleRefreshing: "nieaktualne, odświeżam",
  sync: "sync",
  every: (min) => `co ${min} min`,
  rainNoun: { rain: "deszcz", snow: "śnieg", thunder: "burza" },
  rainOngoing: { rain: "pada", snow: "sypie", thunder: "grzmi" },
  rainEasing: (verb, end) => `${verb}, przestanie ok. ${end}`,
  rainNoEnd: (verb) => `${verb} i nie zanosi się na koniec`,
  rainWithinHourUntil: (noun, end) => `${noun} w ciągu godziny, do ok. ${end}`,
  rainWithinHour: (noun) => `${noun} w ciągu godziny`,
  rainFromUntil: (noun, start, end) => `${noun} od ${start} do ok. ${end}`,
  rainFrom: (noun, start) => `${noun} od ${start}`,
  rainRange: (noun, start, end) => `${noun} ${start}–${end}`,
  againLater: ", potem znowu",
  bandMax: (p) => `maks. ${p}%`,
  today: "dziś",
  tomorrow: "jutro",
  dow: ["nd", "pn", "wt", "śr", "cz", "pt", "sb"],
  dayHours: "godziny",
  close: "[ zamknij ]",
  noRainThatDay: "tego dnia deszcz mało prawdopodobny",
  tFeels: "odczuwalna",
  tWind: "wiatr",
  tHumidity: "wilgotność",
  tPressure: "ciśnienie",
  tSunrise: "wschód",
  tSunset: "zachód",
  refresh: "ODŚWIEŻANIE",
  refreshLabel: "co ile odświeżać (aplikacja + widget)",
  notifications: "POWIADOMIENIA",
  rainSoon: "deszcz — ostrzeżenie przed początkiem i gdy długi deszcz ma przestać",
  briefToggle: "poranny brief — jedno podsumowanie zamiast osobnych alertów",
  from: "↳ od",
  briefOn: "// brief włączony: progi poniżej decydują, o czym wspomni",
  briefOff: "// brief wyłączony: każdy próg poniżej wysyła osobny alert",
  eveningToggle: "zapowiedź jutra — wieczorem, jedna linijka",
  high: "wysoka temperatura",
  low: "niska temperatura",
  swing: "duży skok z dnia na dzień",
  air: "jakość powietrza",
  threshold: "↳ próg",
  minDelta: "↳ min. różnica",
  aqiThreshold: "↳ próg AQI",
  testNotifications: "[ wyślij testowe powiadomienia ]",
  testSending: "wysyłam…",
  testSent: (n) => `// wysłano ${n} — zajrzyj do panelu powiadomień`,
  testNoPermission: "// powiadomienia są zablokowane — włącz je w ustawieniach systemu",
  testFailed: (msg) => `// nie udało się wysłać: ${msg}`,
  testWebOnly: "// powiadomienia wysyła aplikacja na Androida; to wersja w przeglądarce",
  permissionAsked: "// powiadomienia wymagają zgody — potwierdź w okienku",
  tone: "TON",
  toneHints: {
    clean: "dowcipna, ale uprzejma stopka, rzeczowe powiadomienia",
    sigma: "stopka sigma, rzeczowe powiadomienia",
    rude: "stopka sigma, celowo chamskie powiadomienia",
  },
  toneApplies: "widget, powiadomienia i ten ekran",
  language: "JĘZYK",
  languageHint: "// cała aplikacja: ten ekran, widget i powiadomienia",
  nativeNote1: "// na Androidzie ustawienia czyta bezpośrednio widget",
  nativeNote2: "// w tle — aplikacja nie musi być otwarta, żeby alerty działały.",
  aboutName: "homebrew-weather — pikselowa prognoza pogody dla programistów",
  aboutSynopsis:
    "Mała aplikacja pogodowa w stylu fosforowego terminala z lat 80.: ręcznie rysowane ikony 16×16, żart na każdą pogodę i widget, który odzywa się tylko wtedy, gdy niebo ma coś do powiedzenia.",
  aboutData1: "Pogoda i wyszukiwanie miast:",
  aboutData2:
    "— za darmo, bez klucza, bez śledzenia. Twoja lokalizacja, jeśli ją udostępnisz, trafia do Open-Meteo jako współrzędne; nazwę miejsca podaje telefon (w przeglądarce: OpenStreetMap).",
  aboutBugs: "// TODO: nauczyć ją parzyć kawę",
};

export const STRINGS: Record<Lang, Strings> = { en: EN, pl: PL };
export type { Strings };

type I18n = { lang: Lang; t: Strings; setLang: (l: Lang) => void };

const I18nContext = createContext<I18n>({ lang: "en", t: EN, setLang: () => {} });

export function I18nProvider({ children }: { children: ReactNode }) {
  const [lang, setLangState] = useState<Lang>("en");
  useEffect(() => {
    loadLang().then(setLangState);
  }, []);
  useEffect(() => {
    if (typeof document !== "undefined") document.documentElement.lang = lang;
  }, [lang]);
  const setLang = (l: Lang) => {
    setLangState(l);
    void saveLang(l);
  };
  return createElement(
    I18nContext.Provider,
    { value: { lang, t: STRINGS[lang], setLang } },
    children,
  );
}

export function useI18n(): I18n {
  return useContext(I18nContext);
}
