import { useEffect, useRef } from "react";
import type { WeatherResponse } from "@/lib/weather-api";
import { wmoLabelIn } from "@/lib/wmo";
import {
  describeDayRainWindow,
  describeRainWindow,
  findDayRainWindow,
  findTodayRainWindow,
} from "@/lib/rain-window";
import { useI18n } from "@/lib/i18n";
import { HourlyStrip } from "./HourlyStrip";
import { RAIN_COLOR } from "./DailyForecast";

/**
 * One day's hours: when it rains, how warm it gets, hour by hour.
 *
 * Opened by tapping a day — a column on the home-screen widget (which starts
 * the app on that day, see MainActivity.EXTRA_DAY) or a row in the 7-day
 * list. The widget's grid says *that* it rains on a day ("▽ 7–19"); this is
 * where you look to see what that day actually holds.
 */
export function DayDetail({
  data,
  date,
  onClose,
}: {
  data: WeatherResponse;
  date: string;
  onClose: () => void;
}) {
  const { lang, t } = useI18n();
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    ref.current?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [date]);

  const i = data.daily.time.indexOf(date);
  if (i < 0) return null;
  const isToday = i === 0;
  const label = isToday
    ? t.today
    : i === 1
      ? t.tomorrow
      : new Date(`${date}T12:00`).toLocaleDateString(lang === "pl" ? "pl-PL" : "en-GB", {
          weekday: "long",
          day: "numeric",
          month: "short",
        });

  const todayRain = isToday ? findTodayRainWindow(data) : null;
  const dayRain = isToday ? null : findDayRainWindow(data, date);
  const rainText = todayRain
    ? describeRainWindow(todayRain, t)
    : dayRain
      ? describeDayRainWindow(dayRain, t)
      : null;
  const rainKind = todayRain?.kind ?? dayRain?.kind;

  return (
    <div ref={ref} className="terminal-box scroll-mt-4 p-4">
      <div className="mb-2 flex items-baseline justify-between gap-3">
        <div className="text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          $ weather --day {date} · {t.dayHours}
        </div>
        <button
          type="button"
          onClick={onClose}
          className="text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)] hover:text-[color:var(--phosphor)]"
        >
          {t.close}
        </button>
      </div>
      <div className="mb-3 flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <span className="font-display text-2xl crt-glow">{label}</span>
        <span className="font-mono tabular-nums">
          <span className="text-[color:var(--amber)]">
            {Math.round(data.daily.temperature_2m_max[i])}°
          </span>
          <span className="text-[color:var(--phosphor-dim)]"> / </span>
          <span>{Math.round(data.daily.temperature_2m_min[i])}°</span>
        </span>
        <span className="text-sm text-[color:var(--phosphor-dim)]">
          {wmoLabelIn(data.daily.weather_code[i], lang)}
        </span>
        <span
          className={
            "text-sm " + (rainKind ? RAIN_COLOR[rainKind] : "text-[color:var(--phosphor-dim)]")
          }
        >
          {rainText ?? t.noRainThatDay}
        </span>
      </div>
      <HourlyStrip data={data} date={date} />
    </div>
  );
}
