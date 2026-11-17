// Severe-weather warnings, derived. Open-Meteo has no official warnings, so
// this reads the next hours of the forecast for the few things worth
// interrupting someone for: hail, thunderstorm, gale-force gusts, extreme
// heat. Web twin of the native severe-weather rule — the RULE is identical and
// must stay so; only the phrasing differs.
//
// Index 0 is the current hour and reads `current` (the observed values); the
// rest read the hourly forecast. Storm, hail and wind look over indices 0..6,
// heat over 0..12.
//
//   hail  : WMO code 96 or 99
//   storm : WMO code 95..99, unless there is hail
//   wind  : max gust >= 75 km/h   (peak = that max)
//   heat  : max temperature >= 35 °C (peak = that max)
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
  const now = currentHourIndex(data);
  if (now < 0) return [];
  const { weather_code: codes, wind_gusts_10m: gusts, temperature_2m: temps } = data.hourly;
  const c = data.current;

  const code = (k: number) => (k === 0 ? c.weather_code : codes[now + k]);
  const gust = (k: number) => (k === 0 ? c.wind_gusts_10m : gusts?.[now + k]);
  const temp = (k: number) => (k === 0 ? c.temperature_2m : temps[now + k]);
  const inData = (k: number) => now + k < data.hourly.time.length;

  let hail = -1;
  let storm = -1;
  let wind = -1;
  let windPeak = -Infinity;
  for (let k = 0; k <= SEVERE_LOOKAHEAD_HOURS && inData(k); k++) {
    const wmo = code(k);
    if ((wmo === 96 || wmo === 99) && hail < 0) hail = k;
    if (wmo >= 95 && wmo <= 99 && storm < 0) storm = k;
    const g = gust(k);
    if (g != null && Number.isFinite(g) && g >= SEVERE_GUST_KMH) {
      if (wind < 0) wind = k;
      windPeak = Math.max(windPeak, g);
    }
  }

  let heat = -1;
  let heatPeak = -Infinity;
  for (let k = 0; k <= SEVERE_HEAT_LOOKAHEAD_HOURS && inData(k); k++) {
    const v = temp(k);
    if (Number.isFinite(v) && v >= SEVERE_HEAT_C) {
      if (heat < 0) heat = k;
      heatPeak = Math.max(heatPeak, v);
    }
  }

  const out: SevereWarning[] = [];
  if (hail >= 0) out.push({ kind: "hail", startsInHours: hail });
  // Hail already is a thunderstorm; one warning for both.
  else if (storm >= 0) out.push({ kind: "storm", startsInHours: storm });
  if (wind >= 0) out.push({ kind: "wind", startsInHours: wind, peak: windPeak });
  if (heat >= 0) out.push({ kind: "heat", startsInHours: heat, peak: heatPeak });
  return out;
}
