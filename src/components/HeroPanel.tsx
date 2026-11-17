import type { WeatherResponse } from "@/lib/weather-api";
import { wmoLabelIn, wmoToKind } from "@/lib/wmo";
import {
  currentHourIndex,
  describeRainWindow,
  EVENING_HOUR,
  currentHour,
  findTodayRainWindow,
} from "@/lib/rain-window";
import { GUST_NOTABLE, UV_NOTABLE, uvBand } from "@/lib/conditions";
import { useI18n } from "@/lib/i18n";
import { PixelIcon } from "./PixelIcon";

/** Feels-like is only information when it disagrees with the thermometer — the
 * same threshold the widget's standard density uses (WidgetContent.FEELS_DELTA). */
const FEELS_DELTA = 3;

/** Strongest gust still ahead today (this hour included), km/h, or null when
 * the forecast carries no gusts (an older saved one). */
function gustAheadToday(data: WeatherResponse): number | null {
  const gusts = data.hourly.wind_gusts_10m;
  const now = currentHourIndex(data);
  if (!gusts || now < 0) return data.current.wind_gusts_10m ?? null;
  const today = data.hourly.time[now].slice(0, 10);
  let max = data.current.wind_gusts_10m ?? 0;
  for (let i = now; i < gusts.length && data.hourly.time[i].startsWith(today); i++)
    max = Math.max(max, gusts[i] ?? 0);
  return max;
}

/**
 * The dashboard's at-a-glance answer: how warm it is, and whether it's going
 * to rain.
 *
 * The dashboard used to have no hero at all. The current temperature was one
 * row in a key/value table, the same size as surface pressure and the timezone,
 * and the same readings were printed again in the terminal panel beside it —
 * so the app was harder to read at a glance than its own home-screen widget.
 * This puts one big number first, one sentence under it, and leaves the full
 * readout to the terminal panel further down.
 */
export function HeroPanel({ data }: { data: WeatherResponse }) {
  const { lang, t } = useI18n();
  const c = data.current;
  const baseKind = wmoToKind(c.weather_code);
  const kind = !c.is_day && (baseKind === "sun" || baseKind === "partly") ? "moon" : baseKind;
  // Today's rain only: another day's belongs on its own row in DailyForecast.
  const rain = findTodayRainWindow(data);
  // UV and gusts only get a line when they're notable. UV peaks at midday, so
  // after the evening cut-off today's maximum is history and stays quiet.
  const uvToday = data.daily.uv_index_max?.[0];
  const uv = uvToday != null && currentHour(data) < EVENING_HOUR ? Math.round(uvToday) : null;
  const gust = gustAheadToday(data);
  const notable = [
    uv != null && uv >= UV_NOTABLE ? t.uvNotable(uv, t.uvBands[uvBand(uv)]) : null,
    gust != null && gust >= GUST_NOTABLE ? t.gustsNotable(Math.round(gust)) : null,
  ].filter((x): x is string => x !== null);
  const feelsDiffers = Math.abs(c.apparent_temperature - c.temperature_2m) >= FEELS_DELTA;

  return (
    <div className="terminal-box flex items-center gap-5 p-5">
      <PixelIcon kind={kind} size={88} />
      <div className="min-w-0">
        <div className="flex items-baseline gap-1">
          <span className="font-display text-6xl leading-none text-[color:var(--cyan)] crt-glow sm:text-7xl">
            {Math.round(c.temperature_2m)}°
          </span>
          <span className="text-lg text-[color:var(--phosphor-dim)]">C</span>
        </div>
        <p
          className={
            "mt-2 text-sm sm:text-base " +
            (rain ? "text-[color:var(--amber)]" : "text-[color:var(--phosphor)]")
          }
        >
          {rain ? describeRainWindow(rain, t, lang) : wmoLabelIn(c.weather_code, lang)}
        </p>
        {notable.length > 0 && (
          <p className="mt-1 text-xs text-[color:var(--amber)]">{notable.join(" · ")}</p>
        )}
        {feelsDiffers && (
          <p className="mt-1 text-xs text-[color:var(--phosphor-dim)]">
            {t.feelsLike(Math.round(c.apparent_temperature))}
          </p>
        )}
      </div>
    </div>
  );
}
