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
// Every window belongs to its day. The hero only ever describes rain that
// starts *today* (findTodayRainWindow); any other day's rain is shown on that
// day's forecast row (findDayRainWindow). The first version took any window
// starting within 12h, so in the evening the hero announced tomorrow morning's
// rain as if it were today's.
import type { WeatherResponse } from "./weather-api";
import { wmoToKind } from "./wmo";

export const LIKELY_POP = 50;

/** From this local hour, today's forecast row is mostly history — same
 * cut-off as the widget's grid (WidgetContent.EVENING_HOUR). */
export const EVENING_HOUR = 18;

export type RainKind = "rain" | "snow" | "thunder";

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
function currentHourIndex(data: WeatherResponse): number {
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
  };
}

/** Highest rain chance over the hours of today still ahead, or null. */
export function remainingTodayPop(data: WeatherResponse): number | null {
  const { time, precipitation_probability: pops } = data.hourly;
  const now = currentHourIndex(data);
  if (now < 0) return null;
  const today = dateOf(time[now]);
  let max = 0;
  for (let i = now; i < time.length && dateOf(time[i]) === today; i++) max = Math.max(max, pops[i] ?? 0);
  return max;
}

const NOUN: Record<RainKind, string> = { rain: "rain", snow: "snow", thunder: "thunderstorm" };
const ONGOING: Record<RainKind, string> = {
  rain: "raining",
  snow: "snowing",
  thunder: "storming",
};

/** An end at midnight reads "24:00" rather than "00:00". */
const endClock = (c: string) => (c === "00:00" ? "24:00" : c);

/** Full-sentence form for the dashboard hero. */
export function describeRainWindow(w: RainWindow): string {
  const noun = NOUN[w.kind];
  const end = w.end && endClock(w.end);
  if (w.ongoing) {
    return end ? `${ONGOING[w.kind]}, easing off around ${end}` : `${ONGOING[w.kind]}, no end in sight`;
  }
  if (w.startsInHours === 0) {
    return end ? `${noun} within the hour, until about ${end}` : `${noun} within the hour`;
  }
  return end ? `${noun} from ${w.start} until about ${end}` : `${noun} from ${w.start}`;
}

/** Short form for a forecast row: "rain 08:00–19:00", "rain 08:00–10:00, again later". */
export function describeDayRainWindow(w: DayRainWindow): string {
  const noun = NOUN[w.kind];
  if (!w.end) return `${noun} from ${w.start}`;
  return `${noun} ${w.start}–${w.end}` + (w.moreLater ? ", again later" : "");
}
