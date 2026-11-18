// Rain by the minute: "rain in about 10 min", from Open-Meteo's 15-minute
// precipitation forecast. Web twin of android/.../widget/RainNowcast.kt — the
// RULE is identical and must stay so (shared cases in testdata/parity/
// rain-nowcast.json, run by both sides).
//
// A step is *wet* when it holds at least WET_MM (0.1 mm in 15 minutes). The
// step the clock is in is the last one that started at or before now and is not
// over yet (now < start + 15 min); with no such step there is no answer.
//
//  - Raining now (that step is wet): minutes until the first dry step after it,
//    or null if it is still wet when the data ends; the total is that wet run.
//  - Dry now: minutes until the first wet step, if it is within HORIZON_MIN;
//    further out (or never) is "no rain" and the hourly window says the rest.
//
// All times are the location's local ISO strings (no offset), so they compare
// and subtract directly once put on one timeline (UTC here, which keeps
// daylight-saving jumps out of it).

import type { Strings } from "./i18n";

export const WET_MM = 0.1;
export const STEP_MIN = 15;
export const HORIZON_MIN = 120;

/** Open-Meteo's `minutely_15` block: local start of each 15 minutes, and what falls in it. */
export type Minutely15 = { time: string[]; precipitation: number[] };

export type NowcastResult = {
  ongoing: boolean;
  /** Set only when it is dry now and rain is coming within the horizon. */
  startsInMinutes: number | null;
  /** Set only while it is raining and the data shows it stopping. */
  endsInMinutes: number | null;
  /** What falls in the wet run (the whole data's worth if it never stops). */
  totalMm: number;
};

/** Minutes since the epoch for the first 16 characters ("yyyy-MM-ddTHH:mm") of [iso], or null. */
export function isoMinutes(iso: string): number | null {
  const m = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(iso);
  if (!m) return null;
  const [y, mo, d, h, mi] = m.slice(1).map(Number);
  // Lenient like SimpleDateFormat on the native side: out-of-range parts roll over.
  const ms = Date.UTC(y, mo - 1, d, h, mi);
  return Number.isFinite(ms) ? Math.floor(ms / 60_000) : null;
}

/** "2026-11-17T16:05" — the current minute in a location [offsetSeconds] from UTC
 * (the twin of WeatherApi.localMinuteIso). */
export function localMinuteIso(nowMs: number, offsetSeconds: number): string {
  return new Date(nowMs + offsetSeconds * 1000).toISOString().slice(0, 16);
}

/** What the data says for [nowLocalIso], or null with no usable steps around that time. */
export function analyseNowcast(
  steps: Array<{ time: string; mm: number }>,
  nowLocalIso: string,
): NowcastResult | null {
  const now = isoMinutes(nowLocalIso);
  if (now === null) return null;
  const timed: Array<[number, number]> = [];
  for (const s of steps) {
    const t = isoMinutes(s.time);
    if (t !== null) timed.push([t, s.mm]);
  }
  timed.sort((a, b) => a[0] - b[0]);
  if (timed.length === 0) return null;

  // The step the clock is in: the last that started at or before now, and not over yet.
  let cur = -1;
  for (let i = 0; i < timed.length; i++) if (timed[i][0] <= now) cur = i;
  if (cur < 0 || now >= timed[cur][0] + STEP_MIN) return null;

  // NaN / missing readings are never wet (NaN >= x is false).
  const wet = (i: number) => timed[i][1] >= WET_MM;
  const sum = (from: number, to: number) => {
    let total = 0;
    for (let i = from; i < to; i++) total += timed[i][1];
    return total;
  };

  if (wet(cur)) {
    let end = cur;
    while (end < timed.length && wet(end)) end++;
    return {
      ongoing: true,
      startsInMinutes: null,
      endsInMinutes: end < timed.length ? timed[end][0] - now : null,
      totalMm: sum(cur, end),
    };
  }
  let start = -1;
  for (let i = cur + 1; i < timed.length; i++) {
    if (wet(i)) {
      start = i;
      break;
    }
  }
  const none: NowcastResult = {
    ongoing: false,
    startsInMinutes: null,
    endsInMinutes: null,
    totalMm: 0,
  };
  if (start < 0) return none;
  const startsIn = timed[start][0] - now;
  if (startsIn > HORIZON_MIN) return none;
  let end = start;
  while (end < timed.length && wet(end)) end++;
  return {
    ongoing: false,
    startsInMinutes: startsIn,
    endsInMinutes: null,
    totalMm: sum(start, end),
  };
}

/** Minutes as a person says them: to the nearest 5, and 0 ("any minute") under 3. */
export function roundForSpeech(minutes: number): number {
  return minutes < 3 ? 0 : Math.max(1, Math.round(minutes / 5)) * 5;
}

/** Rain starting within this many minutes is said in minutes; further out the
 * hourly sentence ("rain from 15:00") reads better. */
export const SAY_IN_MINUTES_MAX = 60;

/** The hero's sentence for a nowcast, or null when it has nothing to add and
 * the usual rain sentence should stand. */
export function describeNowcast(n: NowcastResult | null, t: Strings): string | null {
  if (!n) return null;
  if (n.ongoing && n.endsInMinutes != null) {
    const m = roundForSpeech(n.endsInMinutes);
    return m === 0 ? t.rainStopsAnyMinute : t.rainStopsInMinutes(m);
  }
  if (!n.ongoing && n.startsInMinutes != null && n.startsInMinutes <= SAY_IN_MINUTES_MAX) {
    const m = roundForSpeech(n.startsInMinutes);
    return m === 0 ? t.rainAnyMinute : t.rainInMinutes(m);
  }
  return null;
}

/** The nowcast for a whole forecast at the real clock [nowMs], or null when the
 * forecast has no 15-minute data (an older cache) or it doesn't cover now. */
export function nowcastFor(
  data: { minutely_15?: Minutely15; utc_offset_seconds?: number },
  nowMs: number,
): NowcastResult | null {
  const m = data.minutely_15;
  const offset = data.utc_offset_seconds;
  if (!m || offset == null || !Array.isArray(m.time) || !Array.isArray(m.precipitation))
    return null;
  const steps = m.time.map((time, i) => ({ time, mm: m.precipitation[i] ?? Number.NaN }));
  return analyseNowcast(steps, localMinuteIso(nowMs, offset));
}
