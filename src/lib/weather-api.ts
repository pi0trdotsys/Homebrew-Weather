// Open-Meteo API — no key required.
import { isNative, nativePlaceName } from "./native";

export type GeoResult = {
  name: string;
  country: string;
  admin1?: string;
  latitude: number;
  longitude: number;
};

export async function geocode(query: string, lang: "pl" | "en" = "en"): Promise<GeoResult[]> {
  if (!query.trim()) return [];
  const url = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(
    query,
  )}&count=6&language=${lang}&format=json`;
  const res = await fetch(url);
  if (!res.ok) throw new Error("geocode failed");
  const json = (await res.json()) as { results?: GeoResult[] };
  return json.results ?? [];
}

/**
 * A place name for coordinates from GPS.
 *
 * This used Open-Meteo's `/v1/reverse`, which answers 404 for every
 * coordinate, so a located position always showed as "unknown_location".
 * Now: inside the Android app, the phone's own geocoder (nothing leaves the
 * device); in a browser, OpenStreetMap's Nominatim; and if neither answers,
 * the coordinates themselves — at least they say where the forecast is for.
 */
export async function reverseGeocode(
  lat: number,
  lon: number,
  lang: "pl" | "en" = "en",
): Promise<string> {
  const native = await nativePlaceName(lat, lon);
  if (native) return native;
  if (!isNative()) {
    try {
      const url = `https://nominatim.openstreetmap.org/reverse?format=jsonv2&zoom=10&lat=${lat}&lon=${lon}&accept-language=${lang}`;
      const res = await fetch(url);
      if (res.ok) {
        const json = (await res.json()) as {
          address?: Record<string, string | undefined>;
        };
        const a = json.address ?? {};
        const place = a.city ?? a.town ?? a.village ?? a.municipality ?? a.county ?? a.state;
        if (place) return a.country ? `${place}, ${a.country}` : place;
      }
    } catch {
      // fall through to coordinates
    }
  }
  return coordinateLabel(lat, lon);
}

/** "36.43°N 5.15°W" — the same last-resort label the widget uses. */
export function coordinateLabel(lat: number, lon: number): string {
  const ns = lat >= 0 ? "N" : "S";
  const ew = lon >= 0 ? "E" : "W";
  return `${Math.abs(lat).toFixed(2)}°${ns} ${Math.abs(lon).toFixed(2)}°${ew}`;
}

export type WeatherResponse = {
  current: {
    time: string;
    temperature_2m: number;
    apparent_temperature: number;
    relative_humidity_2m: number;
    weather_code: number;
    wind_speed_10m: number;
    is_day: number;
    surface_pressure: number;
  };
  hourly: {
    time: string[];
    temperature_2m: number[];
    weather_code: number[];
    precipitation_probability: number[];
    is_day?: number[];
  };
  daily: {
    time: string[];
    weather_code: number[];
    temperature_2m_max: number[];
    temperature_2m_min: number[];
    precipitation_probability_max: number[];
    sunrise: string[];
    sunset: string[];
  };
  timezone: string;
  /** The location's offset from UTC; used to read a cached forecast against the real clock. */
  utc_offset_seconds?: number;
};

export async function fetchWeather(lat: number, lon: number): Promise<WeatherResponse> {
  const params = new URLSearchParams({
    latitude: String(lat),
    longitude: String(lon),
    current:
      "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day,surface_pressure",
    hourly: "temperature_2m,weather_code,precipitation_probability,is_day",
    daily:
      "weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset",
    timezone: "auto",
    forecast_days: "7",
  });
  const res = await fetch(`https://api.open-meteo.com/v1/forecast?${params}`);
  if (!res.ok) throw new Error("weather fetch failed");
  return res.json();
}
