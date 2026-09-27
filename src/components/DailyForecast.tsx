import type { WeatherResponse } from "@/lib/weather-api";
import { wmoToKind, wmoLabel } from "@/lib/wmo";
import {
  EVENING_HOUR,
  currentHour,
  describeDayRainWindow,
  findDayRainWindow,
  findTodayRainWindow,
  remainingTodayPop,
} from "@/lib/rain-window";
import { PixelIcon } from "./PixelIcon";

const DOW = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];

/**
 * Seven days, one row each. A day with likely rain says when, on its own row
 * ("rain 08:00–19:00"), in place of the condition label — the same rule as the
 * widget's grid, so tomorrow's rain is never announced up in the hero as if it
 * were today's. Today is the exception: the hero owns today's rain, and
 * today's figure counts only the hours still ahead. In the evening today's row
 * fades, because by then its max and min are both history.
 */
export function DailyForecast({ data }: { data: WeatherResponse }) {
  const heroHasRain = findTodayRainWindow(data) !== null;
  const evening = currentHour(data) >= EVENING_HOUR;

  return (
    <div className="terminal-box p-4">
      <div className="mb-3 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
        $ weather --forecast 7
      </div>
      <div className="space-y-1">
        {data.daily.time.map((iso, i) => {
          const d = new Date(iso);
          const today = i === 0;
          const kind = wmoToKind(data.daily.weather_code[i]);
          const max = Math.round(data.daily.temperature_2m_max[i]);
          const min = Math.round(data.daily.temperature_2m_min[i]);
          const dailyPop = data.daily.precipitation_probability_max[i] ?? 0;
          const pop = today ? (remainingTodayPop(data) ?? dailyPop) : dailyPop;
          const rain = today ? null : findDayRainWindow(data, iso);
          return (
            <div
              key={iso}
              className={
                "grid grid-cols-[3rem_2.5rem_1fr_auto_auto] items-center gap-3 border-b border-[color:var(--phosphor-dim)]/30 py-1 text-sm last:border-0 " +
                (today && evening ? "opacity-50" : "")
              }
            >
              <span className="text-[color:var(--phosphor-dim)]">{today ? "today" : DOW[d.getDay()]}</span>
              <PixelIcon kind={kind} size={24} />
              {rain ? (
                <span className="truncate text-xs text-[color:var(--amber)]">
                  {describeDayRainWindow(rain)}
                </span>
              ) : (
                <span className="truncate text-xs text-[color:var(--phosphor-dim)]">
                  {wmoLabel(data.daily.weather_code[i])}
                </span>
              )}
              <span className="text-xs text-[color:var(--cyan)]">
                {today && heroHasRain ? "" : `${pop}%`}
              </span>
              <span className="font-mono tabular-nums">
                <span className="text-[color:var(--amber)]">{max}°</span>
                <span className="text-[color:var(--phosphor-dim)]"> / </span>
                <span>{min}°</span>
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
