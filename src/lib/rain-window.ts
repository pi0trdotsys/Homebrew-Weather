// "When does it start raining, and when does it stop" — one sentence from the
// hourly forecast. Web twin of android/.../widget/RainWindow.kt: the RULE is
// identical and must stay so (it's covered by RainWindowTest on the Kotlin
// side); only the phrasing differs, because the web app speaks English and the
// widget Polish.
//
// Rule, in short: an hour is wet at >= 50% precipitation probability ("more
// likely than not" — a sentence that names a start time reads as a prediction),
// or at the current hour if it's precipitating right now. The window is the
// first run of wet hours starting within the next 12h; it ends at the first dry
// hour. Kind is the most severe hour in it: thunder > snow > rain.
import type { WeatherResponse } from "./weather-api";
import { wmoToKind } from "./wmo";

export const LIKELY_POP = 50;
export const START_HORIZON_HOURS = 12;

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

const isPrecip = (code: number) => {
  const k = wmoToKind(code);
  return k === "rain" || k === "snow" || k === "thunder";
};

const clock = (iso: string) => (iso.includes("T") ? iso.split("T")[1] : iso).slice(0, 5);

export function findRainWindow(data: WeatherResponse): RainWindow | null {
  const { time, precipitation_probability: pops, weather_code: codes } = data.hourly;
  // Current hour = the last hourly slot at or before `current.time`. Both are
  // local ISO strings in the same frame, so string order is time order.
  let now = -1;
  for (let i = 0; i < time.length; i++) {
    if (time[i] <= data.current.time) now = i;
    else break;
  }
  if (now < 0) return null;

  const nowIsWet = isPrecip(data.current.weather_code);
  const wet = (i: number) => (pops[i] ?? 0) >= LIKELY_POP || (i === now && nowIsWet);

  let start = -1;
  for (let i = now; i < Math.min(time.length, now + START_HORIZON_HOURS + 1); i++) {
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
  const kinds = spanCodes.map(wmoToKind);
  const kind: RainKind = kinds.includes("thunder") ? "thunder" : kinds.includes("snow") ? "snow" : "rain";

  return {
    kind,
    startsInHours: start - now,
    start: clock(time[start]),
    end: end < time.length ? clock(time[end]) : null,
    ongoing: start === now && nowIsWet,
  };
}

const NOUN: Record<RainKind, string> = { rain: "rain", snow: "snow", thunder: "thunderstorm" };
const ONGOING: Record<RainKind, string> = {
  rain: "raining",
  snow: "snowing",
  thunder: "storming",
};

/** Full-sentence form for the dashboard hero. */
export function describeRainWindow(w: RainWindow): string {
  const noun = NOUN[w.kind];
  if (w.ongoing) {
    return w.end ? `${ONGOING[w.kind]}, easing off around ${w.end}` : `${ONGOING[w.kind]}, no end in sight`;
  }
  if (w.startsInHours === 0) {
    return w.end ? `${noun} within the hour, until about ${w.end}` : `${noun} within the hour`;
  }
  return w.end ? `${noun} from ${w.start} until about ${w.end}` : `${noun} from ${w.start}`;
}
