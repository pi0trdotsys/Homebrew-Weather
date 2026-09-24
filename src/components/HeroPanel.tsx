import type { WeatherResponse } from "@/lib/weather-api";
import { wmoLabel, wmoToKind } from "@/lib/wmo";
import { describeRainWindow, findRainWindow } from "@/lib/rain-window";
import { PixelIcon } from "./PixelIcon";

/** Feels-like is only information when it disagrees with the thermometer — the
 * same threshold the widget's standard density uses (WidgetContent.FEELS_DELTA). */
const FEELS_DELTA = 3;

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
  const c = data.current;
  const baseKind = wmoToKind(c.weather_code);
  const kind = !c.is_day && (baseKind === "sun" || baseKind === "partly") ? "moon" : baseKind;
  const rain = findRainWindow(data);
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
          {rain ? describeRainWindow(rain) : wmoLabel(c.weather_code).toLowerCase()}
        </p>
        {feelsDiffers && (
          <p className="mt-1 text-xs text-[color:var(--phosphor-dim)]">
            feels like {Math.round(c.apparent_temperature)}°
          </p>
        )}
      </div>
    </div>
  );
}
