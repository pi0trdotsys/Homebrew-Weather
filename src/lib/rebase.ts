import type { WeatherResponse } from "./weather-api";

/**
 * A forecast as it should read *now*, however long ago it was fetched — the
 * web twin of WeatherData.rebasedTo on the native side.
 *
 * Offline, the dashboard shows its last saved forecast, and everything
 * relative in it was frozen at fetch time: "now" was last night's
 * temperature, "today" was yesterday, and the rain sentence counted from an
 * hour long gone. Here "now" moves to the hourly forecast for the real
 * current hour and finished days are dropped. Readings with no hourly
 * forecast (feels-like) fall back to the temperature rather than stay stale.
 * A forecast fetched this hour comes back unchanged.
 */
export function rebaseWeather(data: WeatherResponse, nowMs: number): WeatherResponse {
  const offset = data.utc_offset_seconds;
  if (offset == null) return data;
  const nowLocal = new Date(nowMs + offset * 1000).toISOString().slice(0, 13) + ":00";
  if (data.current.time.slice(0, 13) >= nowLocal.slice(0, 13)) return data;

  const { time } = data.hourly;
  let idx = -1;
  for (let i = 0; i < time.length && time[i] <= nowLocal; i++) idx = i;
  if (idx < 0) return data;

  const today = nowLocal.slice(0, 10);
  const keep = data.daily.time.map((d) => d >= today);
  const slice = <T>(arr: T[]) => arr.filter((_, i) => keep[i]);
  const temp = data.hourly.temperature_2m[idx];

  return {
    ...data,
    current: {
      ...data.current,
      time: time[idx],
      temperature_2m: temp,
      apparent_temperature: temp,
      weather_code: data.hourly.weather_code[idx],
      is_day: data.hourly.is_day?.[idx] ?? data.current.is_day,
    },
    daily: {
      time: slice(data.daily.time),
      weather_code: slice(data.daily.weather_code),
      temperature_2m_max: slice(data.daily.temperature_2m_max),
      temperature_2m_min: slice(data.daily.temperature_2m_min),
      precipitation_probability_max: slice(data.daily.precipitation_probability_max),
      sunrise: slice(data.daily.sunrise),
      sunset: slice(data.daily.sunset),
    },
  };
}
