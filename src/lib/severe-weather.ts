// Severe-weather warnings, derived. Open-Meteo has no official warnings, so
// this reads the next hours of the forecast for the few things worth
// interrupting someone for: hail, thunderstorm, gale-force gusts, extreme
// heat. Web twin of the native severe-weather rule — the RULE is identical and
// must stay so; only the phrasing differs.
//
// Index 0 is the current hour: its code is `current`'s, and its gust and
// temperature are the larger of the observed value and the forecast for that
// hour (either may be missing); the rest read the hourly forecast. Storm, hail
// and wind look over indices 0..6, heat over 0..12. With no hourly data at all
// index 0 still reads `current`.
//
//   hail  : WMO code 96 or 99
//   storm : WMO code 95..99, unless there is hail
//   wind  : max gust >= 75 km/h   (peak = that max, rounded to whole km/h)
//   heat  : max temperature >= 35 °C (peak = that max, rounded to whole °C)
//
// The result is ordered hail, storm, wind, heat. These warnings are always
// written in a plain, serious voice, whatever the tone setting says.
import type { WeatherResponse } from "./weather-api";
import { currentHourIndex } from "./rain-window";

export type SevereKind = "hail" | "storm" | "wind" | "heat";

export type SevereWarning = {
  kind: SevereKind;
  /** Hours from now to the first qualifying hour; 0 = the current hour. */
  startsInHours: number;
  /** Highest gust (km/h) for wind, highest temperature (°C) for heat. */
  peak?: number;
};

export const SEVERE_LOOKAHEAD_HOURS = 6;
export const SEVERE_HEAT_LOOKAHEAD_HOURS = 12;
export const SEVERE_GUST_KMH = 75;
export const SEVERE_HEAT_C = 35;

export function findSevereWeather(data: WeatherResponse): SevereWarning[] {
  const found = currentHourIndex(data);
  const hourlyEmpty = data.hourly.time.length === 0;
  // A forecast that starts after "now" has nothing to anchor to; one with no
  // hourly data at all can still speak about the current hour.
  if (found < 0 && !hourlyEmpty) return [];
  const now = Math.max(found, 0);
  const { weather_code: codes, wind_gusts_10m: gusts, temperature_2m: temps } = data.hourly;
  const c = data.current;

  const known = (n: number | null | undefined): n is number =>
    typeof n === "number" && Number.isFinite(n);
  /** The larger of two readings, ignoring a missing one; undefined if both are. */
  const larger = (a: number | null | undefined, b: number | null | undefined) =>
    known(a) ? (known(b) ? Math.max(a, b) : a) : known(b) ? b : undefined;

  const code = (k: number) => (k === 0 ? c.weather_code : codes[now + k]);
  const gust = (k: number) => (k === 0 ? larger(c.wind_gusts_10m, gusts?.[now]) : gusts?.[now + k]);
  const temp = (k: number) => (k === 0 ? larger(c.temperature_2m, temps[now]) : temps[now + k]);
  const inData = (k: number) => k === 0 || now + k < data.hourly.time.length;

  let hail = -1;
  let storm = -1;
  let wind = -1;
  let windPeak = -Infinity;
  for (let k = 0; k <= SEVERE_LOOKAHEAD_HOURS && inData(k); k++) {
    const wmo = code(k);
    if ((wmo === 96 || wmo === 99) && hail < 0) hail = k;
    if (wmo >= 95 && wmo <= 99 && storm < 0) storm = k;
    const g = gust(k);
    if (known(g) && g >= SEVERE_GUST_KMH) {
      if (wind < 0) wind = k;
      windPeak = Math.max(windPeak, g);
    }
  }

  let heat = -1;
  let heatPeak = -Infinity;
  for (let k = 0; k <= SEVERE_HEAT_LOOKAHEAD_HOURS && inData(k); k++) {
    const v = temp(k);
    if (known(v) && v >= SEVERE_HEAT_C) {
      if (heat < 0) heat = k;
      heatPeak = Math.max(heatPeak, v);
    }
  }

  const out: SevereWarning[] = [];
  if (hail >= 0) out.push({ kind: "hail", startsInHours: hail });
  // Hail already is a thunderstorm; one warning for both.
  else if (storm >= 0) out.push({ kind: "storm", startsInHours: storm });
  if (wind >= 0) out.push({ kind: "wind", startsInHours: wind, peak: Math.round(windPeak) });
  if (heat >= 0) out.push({ kind: "heat", startsInHours: heat, peak: Math.round(heatPeak) });
  return out;
}
