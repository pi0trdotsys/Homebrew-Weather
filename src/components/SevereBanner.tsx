import type { WeatherResponse } from "@/lib/weather-api";
import { findSevereWeather, type SevereWarning } from "@/lib/severe-weather";
import { useI18n, type Strings } from "@/lib/i18n";

function line(w: SevereWarning, t: Strings): string {
  const detail =
    w.kind === "wind" && w.peak != null
      ? t.severeWind(Math.round(w.peak))
      : w.kind === "heat" && w.peak != null
        ? t.severeHeat(Math.round(w.peak))
        : null;
  const when = w.startsInHours === 0 ? t.severeNow : t.severeIn(w.startsInHours);
  return [t.severeTitle[w.kind], detail, when].filter(Boolean).join(" · ");
}

/**
 * Severe-weather warnings derived from the forecast (see severe-weather.ts).
 * Renders nothing on an ordinary day. Always a plain, serious voice: a safety
 * warning is not a punchline, whatever the tone setting says.
 */
export function SevereBanner({ data }: { data: WeatherResponse }) {
  const { t } = useI18n();
  const warnings = findSevereWeather(data);
  if (warnings.length === 0) return null;
  return (
    <div
      role="alert"
      className="terminal-box border-[color:var(--crimson)] p-4 text-sm text-[color:var(--crimson)]"
    >
      <ul className="space-y-1 font-bold uppercase tracking-wider">
        {warnings.map((w) => (
          <li key={w.kind}>⚠ {line(w, t)}</li>
        ))}
      </ul>
      <p className="mt-2 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
        {t.severeNote}
      </p>
    </div>
  );
}
