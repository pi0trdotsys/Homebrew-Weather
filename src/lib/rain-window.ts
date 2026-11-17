// "When does it start raining, and when does it stop" — one sentence from the
// hourly forecast. Web twin of android/.../widget/RainWindow.kt: the RULE is
// identical and must stay so (it's covered by RainWindowTest on the Kotlin
// side); only the phrasing differs, because the web app speaks English and the
// widget Polish.
//
// Rule, in short: an hour is wet at >= 50% precipitation probability ("more
// likely than not" — a sentence that names a start time reads as a prediction),
// or at the current hour if it's precipitating right now. A window is a run of
// wet hours; it ends at the first dry hour. Kind is the most severe hour in it:
// thunder > snow > rain.
//
// Intensity (rain only; snow and thunder keep their noun): the peak hourly
// precipitation (mm/h) over the window's hours. < 0.5 drizzle, >= 4 downpour,
// otherwise plain rain. Missing or NaN data is plain rain — never a guess.
//
// Every window belongs to its day. The hero only ever describes rain that
// starts *today* (findTodayRainWindow); any other day's rain is shown on that
// day's forecast row (findDayRainWindow). The first version took any window
// starting within 12h, so in the evening the hero announced tomorrow morning's
// rain as if it were today's.
import type { WeatherResponse } from "./weather-api";
import { wmoToKind } from "./wmo";
import { formatMm, type Lang, type Strings } from "./i18n";

export const LIKELY_POP = 50;

/** From this local hour, today's forecast row is mostly history — same
 * cut-off as the widget's grid (WidgetContent.EVENING_HOUR). */
export const EVENING_HOUR = 18;

export type RainKind = "rain" | "snow" | "thunder";

/** Peak mm/h below which rain is drizzle, and from which it is a downpour. */
export const DRIZZLE_MAX_MM = 0.5;
export const HEAVY_MIN_MM = 4;

export type RainIntensity = "drizzle" | "rain" | "heavy";

/** How hard it rains at its peak; plain rain when the amount is unknown. */
export function rainIntensity(peakMm: number | undefined): RainIntensity {
  if (peakMm == null || !Number.isFinite(peakMm)) return "rain";
  if (peakMm < DRIZZLE_MAX_MM) return "drizzle";
  if (peakMm >= HEAVY_MIN_MM) return "heavy";
  return "rain";
}

/** The noun for a window: intensity-aware for rain, the plain noun otherwise. */
export function rainNounFor(kind: RainKind, peakMm: number | undefined, t: Strings): string {
  if (kind !== "rain") return t.rainNoun[kind];
  const i = rainIntensity(peakMm);
  return i === "rain" ? t.rainNoun.rain : t.rainIntensity[i];
}

/** Sum and peak of hourly precipitation over indices [from, to). Peak is
 * undefined when there is no usable data at all; non-finite hours are skipped. */
function precipOver(data: WeatherResponse, from: number, to: number) {
  const mm = data.hourly.precipitation;
  let total = 0;
  let peak: number | undefined;
  if (mm) {
    for (let i = from; i < to; i++) {
      const v = mm[i];
      if (typeof v !== "number" || !Number.isFinite(v)) continue;
      total += v;
      peak = peak === undefined ? v : Math.max(peak, v);
    }
  }
  return { totalMm: total, peakMm: peak };
}

export type RainWindow = {
  kind: RainKind;
  /** Hours from now until it starts; 0 = the current hour. */
  startsInHours: number;
  /** Local wall-clock "15:00". */
  start: string;
  /** Local wall-clock end, or null if still wet at the end of the data. */
  end: string | null;
  /** Precipitation is observed right now, not just forecast. */
  ongoing: boolean;
  /** Sum of hourly precipitation (mm) over the window's hours; 0 without data. */
  totalMm: number;
  /** Highest hourly precipitation (mm/h) in the window; undefined without data. */
  peakMm: number | undefined;
};

/** One day's first run of likely rain, for that day's forecast row. */
export type DayRainWindow = {
  kind: RainKind;
  start: string;
  /** First dry hour, "24:00" when it rains into midnight, null when the data
   * stops mid-day while it's still wet. */
  end: string | null;
  /** More wet hours later the same day, after this run has ended. */
  moreLater: boolean;
  /** As on RainWindow, over this run's hours. */
  totalMm: number;
  peakMm: number | undefined;
};

const isPrecip = (code: number) => {
  const k = wmoToKind(code);
  return k === "rain" || k === "snow" || k === "thunder";
};

const clock = (iso: string) => (iso.includes("T") ? iso.split("T")[1] : iso).slice(0, 5);
const dateOf = (iso: string) => iso.split("T")[0];

const severest = (codes: number[]): RainKind => {
  const kinds = codes.map(wmoToKind);
  return kinds.includes("thunder") ? "thunder" : kinds.includes("snow") ? "snow" : "rain";
};

/** Index of the current hour: the last hourly slot at or before `current.time`.
 * Both are local ISO strings in the same frame, so string order is time order. */
export function currentHourIndex(data: WeatherResponse): number {
  let now = -1;
  const { time } = data.hourly;
  for (let i = 0; i < time.length; i++) {
    if (time[i] <= data.current.time) now = i;
    else break;
  }
  return now;
}

/** Local hour of `current.time`, 0-23. */
export function currentHour(data: WeatherResponse): number {
  return Number(clock(data.current.time).slice(0, 2)) || 0;
}

/** The first window that starts today, or null — in the evening the next
 * rain is often tomorrow's, and that one belongs on tomorrow's row. */
export function findTodayRainWindow(data: WeatherResponse): RainWindow | null {
  const { time, precipitation_probability: pops, weather_code: codes } = data.hourly;
  const now = currentHourIndex(data);
  if (now < 0) return null;

  const nowIsWet = isPrecip(data.current.weather_code);
  const wet = (i: number) => (pops[i] ?? 0) >= LIKELY_POP || (i === now && nowIsWet);
  const today = dateOf(time[now]);

  let start = -1;
  for (let i = now; i < time.length && dateOf(time[i]) === today; i++) {
    if (wet(i)) {
      start = i;
      break;
    }
  }
  if (start < 0) return null;

  let end = start;
  while (end < time.length && wet(end)) end++;

  const spanCodes = codes.slice(start, end);
  if (start === now && nowIsWet) spanCodes.push(data.current.weather_code);

  return {
    kind: severest(spanCodes),
    startsInHours: start - now,
    start: clock(time[start]),
    end: end < time.length ? clock(time[end]) : null,
    ongoing: start === now && nowIsWet,
    ...precipOver(data, start, end),
  };
}

/** The first run of likely-wet hours on `date` ("2026-09-28"). Forecast only,
 * so no "raining now" rule. */
export function findDayRainWindow(data: WeatherResponse, date: string): DayRainWindow | null {
  const { time, precipitation_probability: pops, weather_code: codes } = data.hourly;
  const idx = time.map((_, i) => i).filter((i) => dateOf(time[i]) === date);
  const wet = (i: number) => (pops[i] ?? 0) >= LIKELY_POP;
  const first = idx.findIndex(wet);
  if (first < 0) return null;

  let end = first;
  while (end < idx.length && wet(idx[end])) end++;
  const lastHour = clock(time[idx[idx.length - 1]]);

  return {
    kind: severest(idx.slice(first, end).map((i) => codes[i])),
    start: clock(time[idx[first]]),
    end: end < idx.length ? clock(time[idx[end]]) : lastHour === "23:00" ? "24:00" : null,
    moreLater: idx.slice(end).some(wet),
    ...precipOver(data, idx[first], idx[end - 1] + 1),
  };
}

/** Highest rain chance over the hours of today still ahead, or null. */
export function remainingTodayPop(data: WeatherResponse): number | null {
  const { time, precipitation_probability: pops } = data.hourly;
  const now = currentHourIndex(data);
  if (now < 0) return null;
  const today = dateOf(time[now]);
  let max = 0;
  for (let i = now; i < time.length && dateOf(time[i]) === today; i++)
    max = Math.max(max, pops[i] ?? 0);
  return max;
}

/** An end at midnight reads "24:00" rather than "00:00". */
const endClock = (c: string) => (c === "00:00" ? "24:00" : c);

/** Amounts below this are not worth a number in a sentence ("0.0 mm" is not
 * information). Same line as RainWindow.MIN_SHOWN_MM on the native side. */
export const SENTENCE_MIN_MM = 0.1;

/** The forecast list shows a day's amount only from here; below it the plain
 * percentage says enough and the row stays quiet. */
export const DAY_LIST_MIN_MM = 1;

/** Full-sentence form for the dashboard hero, in the app's language. The
 * window's total is added once it is worth a number (>= 0.1 mm). */
export function describeRainWindow(w: RainWindow, t: Strings, lang: Lang): string {
  const noun = rainNounFor(w.kind, w.peakMm, t);
  const intensity = w.kind === "rain" ? rainIntensity(w.peakMm) : "rain";
  const verb = intensity === "rain" ? t.rainOngoing[w.kind] : t.rainOngoingIntensity[intensity];
  const end = w.end && endClock(w.end);
  let text: string;
  if (w.ongoing) text = end ? t.rainEasing(verb, end) : t.rainNoEnd(verb);
  else if (w.startsInHours === 0)
    text = end ? t.rainWithinHourUntil(noun, end) : t.rainWithinHour(noun);
  else text = end ? t.rainFromUntil(noun, w.start, end) : t.rainFrom(noun, w.start);
  return w.totalMm >= SENTENCE_MIN_MM ? `${text} · ${formatMm(w.totalMm, lang)}` : text;
}

/** Short form for a forecast row: "rain 08:00–19:00", "rain 08:00–10:00, again later". */
export function describeDayRainWindow(w: DayRainWindow, t: Strings): string {
  const noun = rainNounFor(w.kind, w.peakMm, t);
  if (!w.end) return t.rainFrom(noun, w.start);
  return t.rainRange(noun, w.start, w.end) + (w.moreLater ? t.againLater : "");
}

/** A run of likely-wet hours inside a slice of the hourly forecast. */
export type RainRun = {
  from: number;
  to: number;
  kind: RainKind;
  maxPop: number;
  peakMm: number | undefined;
};

/**
 * Runs of likely rain among the hourly indices [idx], for the hourly strip:
 * one band over each run instead of a percentage under every hour. Same
 * "likely" bar as every other rain rule here.
 */
export function rainRuns(data: WeatherResponse, idx: number[]): RainRun[] {
  const { precipitation_probability: pops, weather_code: codes } = data.hourly;
  const runs: RainRun[] = [];
  let start = -1;
  const close = (endPos: number) => {
    const span = idx.slice(start, endPos);
    runs.push({
      from: start,
      to: endPos,
      kind: severest(span.map((i) => codes[i])),
      maxPop: Math.max(...span.map((i) => pops[i] ?? 0)),
      peakMm: precipOver(data, Math.min(...span), Math.max(...span) + 1).peakMm,
    });
    start = -1;
  };
  idx.forEach((i, pos) => {
    const wet = (pops[i] ?? 0) >= LIKELY_POP;
    if (wet && start < 0) start = pos;
    if (!wet && start >= 0) close(pos);
  });
  if (start >= 0) close(idx.length);
  return runs;
}
