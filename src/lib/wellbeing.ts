import type { WeatherResponse } from "./weather-api";
import { wmoToKind } from "./wmo";
import { currentHour, EVENING_HOUR } from "./rain-window";

/** What the footer tip should speak to, most pressing first. */
export type WellbeingSituation =
  | "air"
  | "sun"
  | "hot"
  | "cold"
  | "rain"
  | "wind"
  | "night"
  | "calm";

// Thresholds mirror WellbeingTips.kt (the source of truth for the wording and
// for this rule): SUN_FROM_UV, HOT_FROM_C, COLD_UP_TO_C, WIND_FROM_GUST_KMH.
export const SUN_FROM_UV = 8;
export const HOT_FROM_C = 30;
export const COLD_UP_TO_C = 0;
export const WIND_FROM_GUST_KMH = 60;

/**
 * The situation the tip should speak to, first match wins — the web twin of
 * `WellbeingTips.situation`:
 *
 *  1. air   — US AQI >= 101. The web app has no air-quality reading, so never.
 *  2. sun   — daytime (is_day and local hour before the evening cut-off) and
 *             today's UV maximum >= 8
 *  3. hot   — temperature >= 30
 *  4. cold  — temperature <= 0
 *  5. rain  — raining or storming now
 *  6. wind  — gusts >= 60 km/h
 *  7. night — after dark
 *  8. calm  — anything else
 *
 * A missing or NaN reading (a forecast cached before it was fetched) never
 * triggers a situation.
 */
export function wellbeingSituation(data: WeatherResponse): WellbeingSituation {
  const c = data.current;
  const daytime = c.is_day === 1 && currentHour(data) < EVENING_HOUR;
  const uv = data.daily.uv_index_max?.[0];
  const temp = c.temperature_2m;
  const kind = wmoToKind(c.weather_code);
  const gust = c.wind_gusts_10m;
  const known = (n: number | undefined | null): n is number =>
    typeof n === "number" && !Number.isNaN(n);

  if (daytime && known(uv) && uv >= SUN_FROM_UV) return "sun";
  if (known(temp) && temp >= HOT_FROM_C) return "hot";
  if (known(temp) && temp <= COLD_UP_TO_C) return "cold";
  if (kind === "rain" || kind === "thunder") return "rain";
  if (known(gust) && gust >= WIND_FROM_GUST_KMH) return "wind";
  if (c.is_day === 0) return "night";
  return "calm";
}
