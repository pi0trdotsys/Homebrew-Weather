// The geometry behind the 24-hour chart (components/HourlyChart.tsx), kept free
// of React and the DOM so it can be tested: where an hour sits across the plot,
// how tall a rain column is, which stretches are night, and where the sun rises
// and sets inside the window.
//
// Positions along the time axis are fractional hour indices: 0 is the first
// hour in view, 23 the last, and 6.2 is a fifth of the way from the 6th to the
// 7th. Open-Meteo gives every time in the location's own clock with no offset,
// so two of them compare directly once both are minutes on one timeline — the
// same way LocalIso.minutes does it on the widget side (isoMinutes).
import type { WeatherResponse } from "./weather-api";
import { isoMinutes } from "./rain-nowcast";

/** One hour of the chart. `mm` is null when the forecast carries no amounts;
 * `isDay` is null when it doesn't say. */
export type ChartHour = {
  time: string;
  temp: number;
  mm: number | null;
  pop: number;
  code: number;
  isDay: boolean | null;
};

/** Indices into the hourly forecast of the hours to chart: 24 from the current
 * hour, or the hours of [date]. */
export function chartIndices(data: WeatherResponse, date?: string): number[] {
  const { time } = data.hourly;
  if (date) return time.map((_, i) => i).filter((i) => time[i].startsWith(date));
  let now = time.findIndex((x) => x.slice(0, 13) >= data.current.time.slice(0, 13));
  if (now < 0) now = 0;
  return Array.from({ length: 24 }, (_, i) => now + i).filter((i) => i < time.length);
}

/** The hours to chart (see [chartIndices]). */
export function chartHours(data: WeatherResponse, date?: string): ChartHour[] {
  const h = data.hourly;
  return chartIndices(data, date).map((i) => {
    const mm = h.precipitation?.[i];
    const day = h.is_day?.[i];
    return {
      time: h.time[i],
      temp: h.temperature_2m[i],
      mm: typeof mm === "number" && Number.isFinite(mm) ? mm : null,
      pop: h.precipitation_probability[i] ?? 0,
      code: h.weather_code[i],
      isDay: day === 0 ? false : day === 1 ? true : null,
    };
  });
}

// ---------------------------------------------------------------- the plot box

/** The drawing area inside the SVG: x0..x1 across, y0 (top) .. y1 (the floor). */
export type Box = { x0: number; x1: number; y0: number; y1: number };

/** Where the plot sits in an SVG of [width] x [height], leaving room for labels. */
export function plotBox(width: number, height: number): Box {
  return { x0: 14, x1: Math.max(15, width - 14), y0: 20, y1: Math.max(21, height - 22) };
}

/** x of a fractional position among [count] points spread across the box. */
export function posToX(pos: number, count: number, box: Box): number {
  if (count <= 1) return (box.x0 + box.x1) / 2;
  return box.x0 + (pos / (count - 1)) * (box.x1 - box.x0);
}

/** The nearest point index to an x in the SVG's own coordinates. */
export function xToIndex(x: number, count: number, box: Box): number {
  if (count <= 1) return 0;
  const i = Math.round(((x - box.x0) / (box.x1 - box.x0)) * (count - 1));
  return Math.min(count - 1, Math.max(0, i));
}

// ------------------------------------------------------------------------ rain

/** The tallest rain column takes this share of the plot's height. */
export const RAIN_SHARE = 0.45;
/** Below this an hour's amount is not worth a column (the app's usual 0.1 mm line). */
export const RAIN_MIN_MM = 0.1;
/** The column height (0..1) when there is no amount but rain is likely. */
export const RAIN_UNKNOWN_FRACTION = 0.4;
/** An hour is likely wet from this chance (the rule used everywhere else). */
export const LIKELY_POP = 50;

/**
 * How tall an hour's rain column is, 0..1 of its maximum: clamp(mm / 4, 0.15, 1);
 * nothing under 0.1 mm; a fixed middling height when the amount is unknown and
 * rain is likely.
 */
export function rainFraction(mm: number | null, pop: number): number {
  if (mm === null) return pop >= LIKELY_POP ? RAIN_UNKNOWN_FRACTION : 0;
  if (!(mm >= RAIN_MIN_MM)) return 0;
  return Math.min(1, Math.max(0.15, mm / 4));
}

/** The column's height in pixels for a plot [plotHeight] tall. */
export function rainColumnHeight(mm: number | null, pop: number, plotHeight: number): number {
  return rainFraction(mm, pop) * RAIN_SHARE * plotHeight;
}

// ------------------------------------------------------------------ sun markers

export type SunMarker = {
  kind: "sunrise" | "sunset";
  /** Fractional hour index within the window, so a sunrise at 06:12 sits 0.2 of the way past 06:00. */
  pos: number;
  /** "06:12", the location's clock. */
  label: string;
};

/**
 * The sunrises and sunsets that fall inside the window of [times] (local ISO
 * hours, ascending), each at its exact minute. A time exactly at the first or
 * last hour counts as inside. Entries that don't parse (polar day and night
 * leave them empty) are skipped.
 */
export function sunMarkers(
  times: string[],
  sunrise: ReadonlyArray<string | null | undefined>,
  sunset: ReadonlyArray<string | null | undefined>,
): SunMarker[] {
  const mins = times.map(isoMinutes);
  if (times.length < 2 || mins.some((m) => m === null)) return [];
  const slots = mins as number[];
  const out: SunMarker[] = [];
  const place = (iso: string | null | undefined, kind: SunMarker["kind"]) => {
    const m = iso ? isoMinutes(iso) : null;
    if (m === null || !iso) return;
    if (m < slots[0] || m > slots[slots.length - 1]) return;
    let i = 0;
    while (i < slots.length - 2 && m >= slots[i + 1]) i++;
    const span = slots[i + 1] - slots[i];
    out.push({ kind, pos: i + (span > 0 ? (m - slots[i]) / span : 0), label: iso.slice(11, 16) });
  };
  sunrise.forEach((s) => place(s, "sunrise"));
  sunset.forEach((s) => place(s, "sunset"));
  return out.sort((a, b) => a.pos - b.pos);
}

// ------------------------------------------------------------------ night bands

/** Night by the sun alone, for an hour the forecast doesn't give `is_day` for:
 * before that date's sunrise or from its sunset. Null when the date has no sun times. */
function nightBySun(
  time: string,
  daily: {
    time: string[];
    sunrise: ReadonlyArray<string | null | undefined>;
    sunset: ReadonlyArray<string | null | undefined>;
  },
): boolean | null {
  const d = daily.time.indexOf(time.slice(0, 10));
  if (d < 0) return null;
  const now = isoMinutes(time);
  const rise = daily.sunrise[d] ? isoMinutes(daily.sunrise[d]!) : null;
  const set = daily.sunset[d] ? isoMinutes(daily.sunset[d]!) : null;
  if (now === null || rise === null || set === null) return null;
  return now < rise || now >= set;
}

/**
 * The stretches of the window that are night, as [from, to] fractional hour
 * indices, from `is_day` (or the sun times where it is missing; an hour that
 * can't be told counts as day). Where night begins or ends between two hours
 * the edge sits at the matching sunset or sunrise marker if there is one
 * within that step, else halfway.
 */
export function nightSpans(
  hours: Array<Pick<ChartHour, "time" | "isDay">>,
  markers: SunMarker[],
  daily?: {
    time: string[];
    sunrise: ReadonlyArray<string | null | undefined>;
    sunset: ReadonlyArray<string | null | undefined>;
  },
): Array<[number, number]> {
  const night = hours.map((h) =>
    h.isDay !== null ? !h.isDay : daily ? (nightBySun(h.time, daily) ?? false) : false,
  );
  const edge = (i: number, kind: SunMarker["kind"]) =>
    markers.find((m) => m.kind === kind && m.pos >= i && m.pos <= i + 1)?.pos ?? i + 0.5;
  const spans: Array<[number, number]> = [];
  let start: number | null = night[0] ? 0 : null;
  for (let i = 0; i < night.length - 1; i++) {
    if (night[i] === night[i + 1]) continue;
    if (night[i + 1]) start = edge(i, "sunset");
    else if (start !== null) {
      spans.push([start, edge(i, "sunrise")]);
      start = null;
    }
  }
  if (start !== null && night.length > 1) spans.push([start, night.length - 1]);
  return spans;
}

// -------------------------------------------------------------- ticks and scale

/** Indices of the hours that fall on a multiple of [every] hours of the local clock. */
export function tickIndices(times: string[], every = 3): number[] {
  const out: number[] = [];
  times.forEach((t, i) => {
    const hour = Number(t.slice(11, 13));
    if (t.slice(14, 16) === "00" && hour % every === 0) out.push(i);
  });
  return out;
}

/** Indices of the highest and the lowest temperature (the first of each if tied). */
export function extremes(temps: number[]): { hi: number; lo: number } {
  let hi = 0;
  let lo = 0;
  temps.forEach((t, i) => {
    if (t > temps[hi]) hi = i;
    if (t < temps[lo]) lo = i;
  });
  return { hi, lo };
}

/**
 * A temperature to a y in the box, the lowest at the bottom of the band and the
 * highest at the top, with at least [minSpan] degrees of range so a flat day
 * doesn't stretch noise into a mountain. The line keeps [padTop] and [padBottom]
 * pixels clear of the box's top and floor for labels.
 */
export function tempScale(
  temps: number[],
  box: Box,
  padTop = 16,
  padBottom = 18,
  minSpan = 6,
): (t: number) => number {
  const lo = Math.min(...temps);
  const hi = Math.max(...temps);
  const span = Math.max(minSpan, hi - lo);
  const mid = (hi + lo) / 2;
  const top = box.y0 + padTop;
  const bottom = box.y1 - padBottom;
  return (t) => bottom - ((t - (mid - span / 2)) / span) * (bottom - top);
}
