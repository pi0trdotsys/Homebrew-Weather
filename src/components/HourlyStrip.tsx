import type { WeatherResponse } from "@/lib/weather-api";
import { wmoToKind } from "@/lib/wmo";
import { rainRuns } from "@/lib/rain-window";
import { useI18n } from "@/lib/i18n";
import { PixelIcon } from "./PixelIcon";

const BAND_BG = {
  rain: "bg-[color:var(--cyan)]",
  snow: "bg-[color:var(--phosphor)]",
  thunder: "bg-[color:var(--amber)]",
} as const;

const BAND_TEXT = {
  rain: "text-[color:var(--cyan)]",
  snow: "text-[color:var(--phosphor)]",
  thunder: "text-[color:var(--amber)]",
} as const;

const clock = (iso: string) => iso.split("T")[1]?.slice(0, 5) ?? iso;

/**
 * The next 24 hours. Likely rain is one band across the hours it covers —
 * "rain 07:00–19:00 · max 90%" — instead of a percentage under each of the 24
 * cards, which made you read two dozen numbers to find out when it rains.
 *
 * With [date], shows that day's hours instead (the day panel, DayDetail).
 */
export function HourlyStrip({ data, date }: { data: WeatherResponse; date?: string }) {
  const { t } = useI18n();
  const { time } = data.hourly;

  let idx: number[];
  if (date) {
    idx = time.map((_, i) => i).filter((i) => time[i].startsWith(date));
  } else {
    let now = time.findIndex((x) => x.slice(0, 13) >= data.current.time.slice(0, 13));
    if (now < 0) now = 0;
    idx = Array.from({ length: 24 }, (_, i) => now + i).filter((i) => i < time.length);
  }
  const runs = rainRuns(data, idx);
  const runAt = (pos: number) => runs.find((r) => pos >= r.from && pos < r.to);

  return (
    <div className={date ? "" : "terminal-box p-4"}>
      {!date && (
        <div className="mb-3 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          $ weather --hourly 24
        </div>
      )}
      <div className="flex overflow-x-auto pb-2">
        {idx.map((i, pos) => {
          const kind = wmoToKind(data.hourly.weather_code[i]);
          const run = runAt(pos);
          // The run ends at the first dry hour, which is the slot after its
          // last wet one — whether or not that slot is still in view.
          const after = run ? time[idx[run.to - 1] + 1] : undefined;
          const endLabel = after ? clock(after) : "";
          return (
            <div key={i} className="flex min-w-[4.75rem] flex-col">
              {/* Band cells touch across columns, so a run reads as one bar. */}
              <div className="relative h-4">
                {run && run.from === pos && (
                  <span
                    className={
                      "absolute left-1.5 top-0 z-10 whitespace-nowrap text-[10px] font-bold uppercase tracking-wider " +
                      BAND_TEXT[run.kind]
                    }
                  >
                    ▽{" "}
                    {endLabel
                      ? t.rainRange(
                          t.rainNoun[run.kind],
                          clock(time[i]),
                          endLabel === "00:00" ? "24:00" : endLabel,
                        )
                      : t.rainFrom(t.rainNoun[run.kind], clock(time[i]))}{" "}
                    · {t.bandMax(run.maxPop)}
                  </span>
                )}
              </div>
              <div className={"h-1.5 " + (run ? BAND_BG[run.kind] : "")} />
              <div className="mx-1.5 mt-1 flex flex-col items-center gap-1 border border-[color:var(--phosphor-dim)]/40 p-2">
                <div className="text-xs text-[color:var(--phosphor-dim)]">
                  {clock(time[i]).slice(0, 2)}h
                </div>
                <PixelIcon kind={kind} size={32} />
                <div className="text-sm font-display leading-none">
                  {Math.round(data.hourly.temperature_2m[i])}°
                </div>
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
