// Parity tests: the rules that exist twice, in Kotlin (the widget) and in
// TypeScript (the dashboard), run against language-neutral cases in
// testdata/parity/*.json. The Kotlin side runs the same files from
// android/app/src/test/.../widget/ParityTest.kt, so changing one side's rule
// fails the other's tests. Each adapter here only maps a case's JSON input to
// this side's types, calls the real production function, and maps the result
// back; the rules themselves live in the fixtures.
//
// A case with `"only": "kotlin"` has no counterpart on the web (no air quality,
// no notification horizon, no footer cutting) and is skipped here.
import { describe, expect, test } from "bun:test";
import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import type { WeatherResponse } from "./weather-api";
import { findDayRainWindow, findTodayRainWindow, rainIntensity, rainNounFor } from "./rain-window";
import { findSevereWeather } from "./severe-weather";
import {
  cleanWikitext,
  eventLine,
  parseEnglishEvents,
  parsePolishEvents,
  pickOnThisDay,
} from "./on-this-day";
import { sameCity, withCity, type Coords } from "./settings";
import { wellbeingSituation } from "./wellbeing";
import { analyseNowcast, localMinuteIso, roundForSpeech } from "./rain-nowcast";
import { STRINGS } from "./i18n";
import { tempColor } from "./temp-color";
import { cellKey, parseElevations, relief, samplePoints } from "./terrain";
import { niceStep, segments, smooth, upsample } from "./contours";
import { buildings, Mulberry32, skylineSeed } from "./skyline";

type Json = any; // eslint-disable-line @typescript-eslint/no-explicit-any
type Case = { name: string; fn?: string; only?: "kotlin" | "ts"; input: Json; expect: Json };
type Fixture = { rule: string; tolerance?: number; cases: Case[] };

const DIR = join(import.meta.dir, "../../testdata/parity");
const fixtures: Fixture[] = readdirSync(DIR)
  .filter((f) => f.endsWith(".json"))
  .sort()
  .map((f) => JSON.parse(readFileSync(join(DIR, f), "utf8")) as Fixture);

/** Amounts are sums of doubles: compare to 6 decimals, like the Kotlin side. */
const round6 = (v: unknown): unknown =>
  JSON.parse(JSON.stringify(v, (_k, x) => (typeof x === "number" ? Math.round(x * 1e6) / 1e6 : x)));

/** Deep equality where numbers may differ by [tol] (the Kotlin side computes in 32-bit floats). */
function expectClose(actual: unknown, expected: unknown, tol: number, path = "$"): void {
  if (typeof expected === "number" && typeof actual === "number") {
    if (Math.abs(actual - expected) > tol)
      throw new Error(`${path}: ${actual} is not within ${tol} of ${expected}`);
  } else if (Array.isArray(expected) && Array.isArray(actual)) {
    if (actual.length !== expected.length)
      throw new Error(`${path}: length ${actual.length}, expected ${expected.length}`);
    expected.forEach((e, i) => expectClose(actual[i], e, tol, `${path}[${i}]`));
  } else if (expected && typeof expected === "object" && actual && typeof actual === "object") {
    const keys = Object.keys(expected as object);
    expect(Object.keys(actual as object).sort()).toEqual([...keys].sort());
    for (const k of keys)
      expectClose((actual as Json)[k], (expected as Json)[k], tol, `${path}.${k}`);
  } else {
    expect(actual).toEqual(expected);
  }
}

/** A forecast with only what the rules read filled in. */
function forecast(over: {
  now: string;
  code: number;
  temp?: number | null;
  gust?: number | null;
  isDay?: boolean;
  hourly: Partial<WeatherResponse["hourly"]> & { time: string[] };
  daily?: Partial<WeatherResponse["daily"]>;
}): WeatherResponse {
  const n = over.hourly.time.length;
  return {
    current: {
      time: over.now,
      temperature_2m: (over.temp ?? undefined) as number,
      apparent_temperature: 0,
      relative_humidity_2m: 50,
      weather_code: over.code,
      wind_speed_10m: 0,
      is_day: over.isDay === false ? 0 : 1,
      surface_pressure: 1013,
      wind_gusts_10m: (over.gust ?? undefined) as number,
    },
    hourly: {
      temperature_2m: new Array(n).fill(15),
      weather_code: new Array(n).fill(0),
      precipitation_probability: new Array(n).fill(0),
      ...over.hourly,
    },
    daily: {
      time: [],
      weather_code: [],
      temperature_2m_max: [],
      temperature_2m_min: [],
      precipitation_probability_max: [],
      sunrise: [],
      sunset: [],
      ...over.daily,
    },
    timezone: "UTC",
  };
}

// ----------------------------------------------------------------- adapters

function rainData(input: Json): WeatherResponse {
  const rows: Json[][] = input.hourly;
  return forecast({
    now: input.now,
    code: input.currentCode,
    hourly: {
      time: rows.map((r) => r[0]),
      precipitation_probability: rows.map((r) => r[1]),
      weather_code: rows.map((r) => r[2]),
      precipitation: rows.map((r) => r[3]),
    },
  });
}

/** The intensity word for a window or a bare peak. The web names the intensity
 * only for plain rain; snow and thunder keep their own noun, whatever the amount. */
function intensityOf(kind: string, peak: number | undefined): string {
  if (kind === "rain") return rainIntensity(peak);
  const t = STRINGS.en;
  expect(rainNounFor(kind as "snow" | "thunder", peak, t)).toBe(
    t.rainNoun[kind as "snow" | "thunder"],
  );
  return "rain";
}

const rainWindow: Record<string, (i: Json) => Json> = {
  today(input) {
    const w = findTodayRainWindow(rainData(input));
    if (!w) return null;
    return {
      kind: w.kind,
      startsInHours: w.startsInHours,
      start: w.start,
      end: w.end,
      ongoing: w.ongoing,
      totalMm: w.peakMm === undefined ? null : w.totalMm,
      peakMm: w.peakMm ?? null,
      intensity: intensityOf(w.kind, w.peakMm),
    };
  },
  forDay(input) {
    const w = findDayRainWindow(rainData(input), input.date);
    if (!w) return null;
    return {
      kind: w.kind,
      startHour: parseInt(w.start, 10),
      endHour: w.end === null ? null : parseInt(w.end, 10),
      moreLater: w.moreLater,
      totalMm: w.peakMm === undefined ? null : w.totalMm,
      peakMm: w.peakMm ?? null,
    };
  },
  intensity: (input) => ({ intensity: intensityOf(input.kind, input.peakMm ?? undefined) }),
};

function severe(input: Json): Json {
  const rows: Json[][] = input.hourly;
  const data = forecast({
    now: input.now,
    code: input.currentCode,
    temp: input.currentTemp,
    gust: input.currentGust,
    hourly: {
      time: rows.map((r) => r[0]),
      weather_code: rows.map((r) => r[1]),
      temperature_2m: rows.map((r) => r[2]),
      wind_gusts_10m: rows.map((r) => r[3]),
    },
  });
  return findSevereWeather(data);
}

const onThisDay: Record<string, (i: Json) => Json> = {
  clean: (i) => ({ text: cleanWikitext(i.text) }),
  parsePolish: (i) => ({ events: parsePolishEvents(i.wikitext) }),
  parseEnglish: (i) => ({ events: parseEnglishEvents(i.json) }),
  pick: (i) => ({ year: pickOnThisDay(i.events, i.dayOfYear)?.year ?? null }),
  line: (i) => ({ line: eventLine(i) }),
};

function citiesAdapter(input: Json): Json {
  const out = withCity(input.list as Coords[], input.city as Coords);
  return { list: out };
}

function wellbeing(input: Json): Json {
  const hh = String(input.hour).padStart(2, "0");
  const data = forecast({
    now: `2026-11-17T${hh}:30`,
    code: input.code,
    temp: input.temp,
    gust: input.gust,
    isDay: input.isDay,
    hourly: { time: [`2026-11-17T${hh}:00`] },
    daily: input.uvMax == null ? {} : { uv_index_max: [input.uvMax] },
  });
  return { situation: wellbeingSituation(data) };
}

const nowcast: Record<string, (i: Json) => Json> = {
  analyse: (i) =>
    analyseNowcast(
      (i.steps as Json[][]).map(([time, mm]) => ({ time, mm: mm ?? Number.NaN })),
      i.now,
    ),
  speech: (i) => ({ minutes: roundForSpeech(i.minutes) }),
  localMinute: (i) => ({ iso: localMinuteIso(i.nowMs, i.offsetSeconds) }),
};

const terrain: Record<string, (i: Json) => Json> = {
  cellKey: (i) => ({ key: cellKey(i.lat, i.lon) }),
  samplePoints(i) {
    const p = samplePoints(i.lat, i.lon);
    return {
      count: p.length,
      first: p[0],
      second: p[1],
      endOfFirstRow: p[23],
      startOfSecondRow: p[24],
      middle: p[150],
      last: p[p.length - 1],
      sumLat: p.reduce((a, q) => a + q[0], 0),
      sumLon: p.reduce((a, q) => a + q[1], 0),
    };
  },
  parseElevations: (i) => ({ elevations: parseElevations(i.json) }),
  relief: (i) => ({ relief: relief(i) }),
};

const contours: Record<string, (i: Json) => Json> = {
  upsample(i) {
    const r = upsample(i.field, i.w, i.h, i.factor);
    return { w: r.w, h: r.h, field: r.data };
  },
  smooth: (i) => ({ field: smooth(i.field, i.w, i.h) }),
  segments: (i) => ({
    segments: segments(i.field, i.w, i.h, i.level).map((s) => [s.x1, s.y1, s.x2, s.y2]),
  }),
  niceStep: (i) => ({ step: niceStep(i.relief) }),
};

const skyline: Record<string, (i: Json) => Json> = {
  hash: (i) => ({ hash: skylineSeed(i.text) }),
  prng(i) {
    const r = new Mulberry32(skylineSeed(i.text));
    return { doubles: Array.from({ length: i.count }, () => r.nextDouble()) };
  },
  buildings: (i) => ({ buildings: buildings(i.seed, i.widthUnits, i.maxHeightUnits) }),
};

const adapters: Record<string, (c: Case) => Json> = {
  "rain-window": (c) => rainWindow[c.fn!](c.input),
  "severe-weather": (c) => severe(c.input),
  "on-this-day": (c) => onThisDay[c.fn!](c.input),
  "saved-cities": (c) => citiesAdapter(c.input),
  "wellbeing-situation": (c) => wellbeing(c.input),
  "rain-nowcast": (c) => nowcast[c.fn!](c.input),
  "temp-color": (c) => ({ hex: tempColor(c.input.celsius) }),
  terrain: (c) => terrain[c.fn!](c.input),
  contours: (c) => contours[c.fn!](c.input),
  skyline: (c) => skyline[c.fn!](c.input),
};

// -------------------------------------------------------------------- tests

describe("parity fixtures", () => {
  test("every fixture has an adapter and every rule has a fixture", () => {
    expect(fixtures.map((f) => f.rule).sort()).toEqual(Object.keys(adapters).sort());
  });

  test("case names are unique within a fixture and every case has an expectation", () => {
    for (const f of fixtures) {
      const names = f.cases.map((c) => c.name);
      expect(new Set(names).size).toBe(names.length);
      for (const c of f.cases) expect("expect" in c && "input" in c).toBe(true);
    }
  });
});

for (const f of fixtures) {
  describe(f.rule, () => {
    for (const c of f.cases) {
      if (c.only === "kotlin") continue;
      test(c.name, () => {
        const actual = adapters[f.rule](c);
        if (f.tolerance !== undefined) expectClose(round6(actual), c.expect, f.tolerance);
        else expect(round6(actual)).toEqual(round6(c.expect));
      });
    }
  });
}

// A few direct checks the shared cases can't express.
describe("sameCity", () => {
  test("is symmetric", () => {
    const a: Coords = { lat: 50, lon: 20, name: "A" };
    const b: Coords = { lat: 50.005, lon: 20.005, name: "B" };
    expect(sameCity(a, b)).toBe(sameCity(b, a));
  });
});
