// Map Open-Meteo WMO weather codes to our internal icon kinds.
// https://open-meteo.com/en/docs

export type WeatherKind = "sun" | "partly" | "cloud" | "fog" | "rain" | "snow" | "thunder";

export function wmoToKind(code: number): WeatherKind {
  if (code === 0) return "sun";
  if (code === 1 || code === 2) return "partly";
  if (code === 3) return "cloud";
  if (code === 45 || code === 48) return "fog";
  if (code >= 51 && code <= 67) return "rain";
  if (code >= 80 && code <= 82) return "rain";
  if (code >= 71 && code <= 77) return "snow";
  if (code === 85 || code === 86) return "snow";
  if (code >= 95) return "thunder";
  return "cloud";
}

export function wmoLabel(code: number): string {
  const m: Record<number, string> = {
    0: "clear sky",
    1: "mostly clear",
    2: "partly cloudy",
    3: "overcast",
    45: "fog",
    48: "rime fog",
    51: "light drizzle",
    53: "drizzle",
    55: "dense drizzle",
    56: "freezing drizzle",
    57: "freezing drizzle",
    61: "light rain",
    63: "rain",
    65: "heavy rain",
    66: "freezing rain",
    67: "freezing rain",
    71: "light snow",
    73: "snow",
    75: "heavy snow",
    77: "snow grains",
    80: "rain showers",
    81: "rain showers",
    82: "violent showers",
    85: "snow showers",
    86: "snow showers",
    95: "thunderstorm",
    96: "thunderstorm w/ hail",
    99: "thunderstorm w/ hail",
  };
  return m[code] ?? "unknown";
}

const PL_LABELS: Record<number, string> = {
  0: "bezchmurnie",
  1: "prawie bezchmurnie",
  2: "częściowe zachmurzenie",
  3: "pochmurno",
  45: "mgła",
  48: "szadź",
  51: "lekka mżawka",
  53: "mżawka",
  55: "gęsta mżawka",
  56: "marznąca mżawka",
  57: "marznąca mżawka",
  61: "słaby deszcz",
  63: "deszcz",
  65: "ulewa",
  66: "marznący deszcz",
  67: "marznący deszcz",
  71: "słaby śnieg",
  73: "śnieg",
  75: "intensywny śnieg",
  77: "ziarna śniegu",
  80: "przelotny deszcz",
  81: "przelotny deszcz",
  82: "gwałtowne ulewy",
  85: "przelotny śnieg",
  86: "przelotny śnieg",
  95: "burza",
  96: "burza z gradem",
  99: "burza z gradem",
};

/** [wmoLabel] in the app's language (see i18n.ts). */
export function wmoLabelIn(code: number, lang: "pl" | "en"): string {
  return lang === "pl" ? (PL_LABELS[code] ?? "nieznane") : wmoLabel(code);
}
