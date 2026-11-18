import { useEffect, useId, useMemo, useRef, useState } from "react";
import type { KeyboardEvent, PointerEvent, RefObject } from "react";
import type { WeatherResponse } from "@/lib/weather-api";
import { wmoLabelIn, wmoToKind } from "@/lib/wmo";
import { rainNounFor, rainRuns, type RainRun } from "@/lib/rain-window";
import { formatMm, useI18n } from "@/lib/i18n";
import { tempColor } from "@/lib/temp-color";
import {
  chartHours,
  chartIndices,
  extremes,
  nightSpans,
  plotBox,
  posToX,
  rainColumnHeight,
  sunMarkers,
  tempScale,
  tickIndices,
  xToIndex,
  type ChartHour,
} from "@/lib/chart-geometry";
import { PixelIcon } from "./PixelIcon";

const HEIGHT = 200;
const TOOLTIP_W = 152;
/** Width of an "HH:mm" label at 10 px in the mono font, with a hair to spare. */
const TICK_LABEL_W = 31;

const BAND_TEXT = {
  rain: "text-[color:var(--cyan)]",
  snow: "text-[color:var(--phosphor)]",
  thunder: "text-[color:var(--amber)]",
} as const;

const clock = (iso: string) => iso.split("T")[1]?.slice(0, 5) ?? iso;

/** A half sun resting on the floor, 9 x 5 pixels (drawn two units to a pixel). */
const SUN = ["....#....", ".#..#..#.", "..#####..", ".#######.", "#########"];
const SUN_PX = 2;

/** The width of [ref]'s element, kept current. 0 until measured. */
function useElementWidth(ref: RefObject<HTMLElement | null>): number {
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const read = () => setWidth(Math.round(el.clientWidth));
    read();
    const ro = new ResizeObserver(read);
    ro.observe(el);
    return () => ro.disconnect();
  }, [ref]);
  return width;
}

/**
 * The next 24 hours as one picture: the temperature as a line coloured by how
 * warm each stretch is, rain as columns from the floor, nights as a faint band,
 * the sun's own minutes of rising and setting, and a cursor to scrub through
 * the hours (mouse, touch or arrow keys) with the details of the hour under it.
 *
 * It replaces the row of 24 cards: the same numbers, but the shape of the day
 * can be read at a glance. The rain window stays above as a sentence.
 *
 * With [date], charts that day's hours instead (the day panel, DayDetail).
 * Geometry lives in lib/chart-geometry.ts.
 */
export function HourlyChart({ data, date }: { data: WeatherResponse; date?: string }) {
  const { lang, t } = useI18n();
  const idx = useMemo(() => chartIndices(data, date), [data, date]);
  const hours = useMemo(() => chartHours(data, date), [data, date]);
  const time = data.hourly.time;
  const runs = useMemo(() => rainRuns(data, idx), [data, idx]);

  const hostRef = useRef<HTMLDivElement>(null);
  const width = useElementWidth(hostRef);
  const [cursor, setCursor] = useState<number | null>(null);
  const [announcement, setAnnouncement] = useState("");
  const ids = { title: useId(), desc: useId(), hint: useId() };

  const n = hours.length;
  const box = useMemo(() => plotBox(width, HEIGHT), [width]);
  const geo = useMemo(() => {
    if (n === 0) return null;
    const temps = hours.map((h) => h.temp);
    const y = tempScale(temps, box);
    const markers = sunMarkers(
      hours.map((h) => h.time),
      data.daily.sunrise,
      data.daily.sunset,
    );
    const night = nightSpans(hours, markers, data.daily);
    const { hi, lo } = extremes(temps);
    return {
      y,
      markers,
      night,
      hi,
      lo,
      ticks: tickIndices(hours.map((h) => h.time)),
      points: hours.map((h, i) => ({ x: posToX(i, n, box), y: y(h.temp) })),
    };
  }, [hours, box, n, data.daily]);

  if (n === 0 || !geo) return null;

  const runText = (run: RainRun) => {
    // The run ends at the first dry hour, the slot after its last wet one,
    // whether or not that slot is still in view.
    const after = time[idx[run.to - 1] + 1];
    const end = after ? clock(after) : "";
    const noun = rainNounFor(run.kind, run.peakMm, t);
    const range = end
      ? t.rainRange(noun, clock(time[idx[run.from]]), end === "00:00" ? "24:00" : end)
      : t.rainFrom(noun, clock(time[idx[run.from]]));
    return `${range} · ${t.bandMax(run.maxPop)}`;
  };

  const temps = hours.map((h) => h.temp);
  const summary =
    t.chartSummary(
      Math.round(temps[geo.lo]),
      Math.round(temps[geo.hi]),
      runs.length ? runs.map(runText).join("; ") : null,
    ) +
    (geo.markers.length
      ? " " +
        geo.markers
          .map((m) => (m.kind === "sunrise" ? t.chartSunrise(m.label) : t.chartSunset(m.label)))
          .join(", ") +
        "."
      : "");

  const iconKind = (h: ChartHour) => {
    const base = wmoToKind(h.code);
    return h.isDay === false && (base === "sun" || base === "partly") ? "moon" : base;
  };
  const say = (i: number) => {
    const h = hours[i];
    setAnnouncement(
      t.chartSelected(
        clock(h.time),
        Math.round(h.temp),
        wmoLabelIn(h.code, lang),
        formatMm(h.mm, lang),
        h.pop,
      ),
    );
  };

  const indexAt = (e: PointerEvent<HTMLDivElement>) => {
    const r = e.currentTarget.getBoundingClientRect();
    return xToIndex(e.clientX - r.left, n, box);
  };
  const onPointerDown = (e: PointerEvent<HTMLDivElement>) => setCursor(indexAt(e));
  const onPointerMove = (e: PointerEvent<HTMLDivElement>) => setCursor(indexAt(e));
  const onPointerEnd = (e: PointerEvent<HTMLDivElement>) => {
    // A mouse keeps hovering after a click; a finger or pen is gone once it lifts.
    if (e.pointerType !== "mouse" || e.type === "pointerleave") setCursor(null);
  };
  const onKeyDown = (e: KeyboardEvent<HTMLDivElement>) => {
    const last = n - 1;
    let next: number | null = null;
    if (e.key === "ArrowRight" || e.key === "ArrowUp") next = cursor === null ? 0 : cursor + 1;
    else if (e.key === "ArrowLeft" || e.key === "ArrowDown")
      next = cursor === null ? 0 : cursor - 1;
    else if (e.key === "Home") next = 0;
    else if (e.key === "End") next = last;
    if (next === null) return;
    e.preventDefault();
    next = Math.min(last, Math.max(0, next));
    setCursor(next);
    say(next);
  };

  const step = n > 1 ? (box.x1 - box.x0) / (n - 1) : 20;
  const colW = Math.max(2, Math.min(14, step * 0.78));
  const plotH = box.y1 - box.y0;
  const sel = cursor !== null ? hours[cursor] : null;
  const selPoint = cursor !== null ? geo.points[cursor] : null;
  const tipLeft = selPoint
    ? selPoint.x + 14 + TOOLTIP_W <= width
      ? selPoint.x + 14
      : Math.max(0, selPoint.x - 14 - TOOLTIP_W)
    : 0;

  const labelAnchor = (x: number) => (x < 24 ? "start" : x > width - 24 ? "end" : "middle");
  const dim = { fill: "var(--phosphor-dim)" } as const;

  // Tick labels that would touch their right-hand neighbour are left off (the
  // tick stays), so on a narrow screen the leftmost, edge-hugging one goes first.
  const tickLabelShown = new Map<number, boolean>();
  let nextStart = Infinity;
  for (const i of [...geo.ticks].reverse()) {
    const x = geo.points[i].x;
    const a = labelAnchor(x);
    const left = a === "start" ? x : a === "end" ? x - TICK_LABEL_W : x - TICK_LABEL_W / 2;
    const show = left + TICK_LABEL_W + 4 <= nextStart;
    tickLabelShown.set(i, show);
    if (show) nextStart = left;
  }

  return (
    <div className={date ? "" : "terminal-box p-4"}>
      {!date && (
        <div className="mb-2 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          $ weather --hourly 24
        </div>
      )}
      <div className="mb-2 flex min-h-4 flex-wrap gap-x-4 gap-y-1 text-[10px] font-bold uppercase tracking-wider">
        {runs.length ? (
          runs.map((run) => (
            <span key={run.from} className={BAND_TEXT[run.kind]}>
              ▽ {runText(run)}
            </span>
          ))
        ) : (
          <span className="font-normal text-[color:var(--phosphor-dim)]">{t.chartDry}</span>
        )}
      </div>

      <div
        ref={hostRef}
        role="group"
        aria-label={t.chartTitle}
        aria-describedby={ids.hint}
        tabIndex={0}
        className="hbw-chart relative select-none outline-none focus-visible:outline focus-visible:outline-1 focus-visible:outline-offset-2 focus-visible:outline-[color:var(--phosphor)]"
        style={{ height: HEIGHT, touchAction: "pan-y" }}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerEnd}
        onPointerCancel={onPointerEnd}
        onPointerLeave={onPointerEnd}
        onKeyDown={onKeyDown}
        onBlur={() => setCursor(null)}
      >
        <span id={ids.hint} className="sr-only">
          {t.chartHint}
        </span>
        <div className="sr-only" role="status" aria-live="polite">
          {announcement}
        </div>

        {width > 0 && (
          <svg
            width={width}
            height={HEIGHT}
            viewBox={`0 0 ${width} ${HEIGHT}`}
            role="img"
            aria-labelledby={ids.title}
            aria-describedby={ids.desc}
            className="block"
          >
            <title id={ids.title}>{t.chartTitle}</title>
            <desc id={ids.desc}>{summary}</desc>

            {/* night, behind everything */}
            {geo.night.map(([a, b]) => (
              <rect
                key={`night-${a}`}
                x={posToX(a, n, box)}
                y={0}
                width={Math.max(0, posToX(b, n, box) - posToX(a, n, box))}
                height={box.y1}
                style={{ fill: "var(--chart-night)" }}
              />
            ))}

            {/* the floor and the 3-hourly ticks */}
            <line
              x1={box.x0}
              x2={box.x1}
              y1={box.y1}
              y2={box.y1}
              strokeOpacity={0.6}
              style={{ stroke: "var(--phosphor-dim)" }}
            />
            {geo.ticks.map((i) => (
              <g key={`tick-${i}`}>
                <line
                  x1={geo.points[i].x}
                  x2={geo.points[i].x}
                  y1={box.y1}
                  y2={box.y1 + 4}
                  strokeOpacity={0.6}
                  style={{ stroke: "var(--phosphor-dim)" }}
                />
                {tickLabelShown.get(i) && (
                  <text
                    x={geo.points[i].x}
                    y={box.y1 + 15}
                    fontSize={10}
                    textAnchor={labelAnchor(geo.points[i].x)}
                    style={dim}
                    className="tabular-nums"
                  >
                    {clock(hours[i].time)}
                  </text>
                )}
              </g>
            ))}

            {/* rain, from the floor up */}
            {hours.map((h, i) => {
              const ch = rainColumnHeight(h.mm, h.pop, plotH);
              if (ch <= 0) return null;
              return (
                <rect
                  key={`rain-${i}`}
                  x={geo.points[i].x - colW / 2}
                  y={box.y1 - ch}
                  width={colW}
                  height={ch}
                  style={{ fill: h.code >= 95 ? "var(--chart-storm)" : "var(--chart-rain)" }}
                />
              );
            })}

            {/* the sun's own minutes */}
            {geo.markers.map((m) => {
              const x = posToX(m.pos, n, box);
              const w = SUN[0].length * SUN_PX;
              return (
                <g key={`${m.kind}-${m.pos}`}>
                  {SUN.flatMap((row, r) =>
                    row
                      .split("")
                      .map((c, k) =>
                        c === "#" ? (
                          <rect
                            key={`${r}-${k}`}
                            x={Math.round(x - w / 2) + k * SUN_PX}
                            y={box.y1 - (SUN.length - r) * SUN_PX}
                            width={SUN_PX}
                            height={SUN_PX}
                            style={{ fill: "var(--amber)" }}
                          />
                        ) : null,
                      ),
                  )}
                  <text
                    x={x}
                    y={box.y1 - SUN.length * SUN_PX - 3}
                    fontSize={9}
                    textAnchor={labelAnchor(x)}
                    style={dim}
                    className="tabular-nums"
                  >
                    {m.label}
                  </text>
                </g>
              );
            })}

            {/* the temperature, each stretch in its own colour */}
            <g className="hbw-chart-draw">
              {geo.points.slice(1).map((p, i) => {
                const q = geo.points[i];
                return (
                  <line
                    key={`seg-${i}`}
                    x1={q.x}
                    y1={q.y}
                    x2={p.x}
                    y2={p.y}
                    strokeWidth={2.5}
                    strokeLinecap="round"
                    style={{ stroke: tempColor((hours[i].temp + hours[i + 1].temp) / 2) }}
                  />
                );
              })}
            </g>

            {/* now */}
            <rect
              x={geo.points[0].x - 3}
              y={geo.points[0].y - 3}
              width={6}
              height={6}
              fill="#ffffff"
            />

            {/* the high and the low */}
            {[geo.hi, geo.lo]
              .filter((v, k, all) => all.indexOf(v) === k)
              .map((i) => {
                const isHi = i === geo.hi;
                const p = geo.points[i];
                return (
                  <text
                    key={`ext-${i}`}
                    x={p.x}
                    y={isHi ? p.y - 8 : p.y + 16}
                    fontSize={12}
                    fontWeight={700}
                    textAnchor={labelAnchor(p.x)}
                    style={{ fill: tempColor(temps[i]) }}
                    className="tabular-nums"
                  >
                    {Math.round(temps[i])}°
                  </text>
                );
              })}

            {/* the cursor */}
            {selPoint && sel && (
              <g>
                <line
                  x1={selPoint.x}
                  x2={selPoint.x}
                  y1={0}
                  y2={box.y1}
                  style={{ stroke: "var(--phosphor)" }}
                  strokeOpacity={0.7}
                />
                <rect
                  x={selPoint.x - 4}
                  y={selPoint.y - 4}
                  width={8}
                  height={8}
                  style={{ fill: tempColor(sel.temp), stroke: "#000000" }}
                />
              </g>
            )}
          </svg>
        )}

        {sel && selPoint && (
          <div
            className="hbw-chart-tip pointer-events-none absolute top-1 z-10 border border-[color:var(--phosphor-dim)] bg-black/85 p-2 text-xs"
            style={{ left: tipLeft, width: TOOLTIP_W }}
            aria-hidden="true"
          >
            <div className="flex items-center gap-2">
              <PixelIcon kind={iconKind(sel)} size={32} />
              <div>
                <div className="tabular-nums text-[color:var(--phosphor-dim)]">
                  {clock(sel.time)}
                </div>
                <div
                  className="font-display text-2xl leading-none tabular-nums"
                  style={{ color: tempColor(sel.temp) }}
                >
                  {Math.round(sel.temp)}°
                </div>
              </div>
            </div>
            <div className="mt-1 flex justify-between gap-2 whitespace-nowrap text-[10px]">
              <span className="text-[color:var(--cyan)]">{formatMm(sel.mm, lang)}</span>
              <span className="text-[color:var(--phosphor-dim)]">{t.chartChance(sel.pop)}</span>
            </div>
          </div>
        )}
      </div>

      {/* The same numbers for screen readers, hour by hour. */}
      <table className="sr-only">
        <caption>{t.chartTitle}</caption>
        <thead>
          <tr>
            <th scope="col">{t.chartCols.hour}</th>
            <th scope="col">{t.chartCols.temp}</th>
            <th scope="col">{t.chartCols.sky}</th>
            <th scope="col">{t.chartCols.rain}</th>
            <th scope="col">{t.chartCols.chance}</th>
          </tr>
        </thead>
        <tbody>
          {hours.map((h) => (
            <tr key={h.time}>
              <th scope="row">{clock(h.time)}</th>
              <td>{Math.round(h.temp)}°</td>
              <td>{wmoLabelIn(h.code, lang)}</td>
              <td>{formatMm(h.mm, lang) || "0 mm"}</td>
              <td>{h.pop}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
