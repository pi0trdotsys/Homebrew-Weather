// What the shared parity cases (testdata/parity/rain-nowcast.json) don't cover:
// the hero's sentence, the lookup on a whole forecast, and old caches.
import { describe, expect, test } from "bun:test";
import { describeNowcast, nowcastFor, type NowcastResult } from "./rain-nowcast";
import { rebaseWeather } from "./rebase";
import { STRINGS } from "./i18n";
import type { WeatherResponse } from "./weather-api";

const dry = (over: Partial<NowcastResult>): NowcastResult => ({
  ongoing: false,
  startsInMinutes: null,
  endsInMinutes: null,
  totalMm: 0,
  ...over,
});

describe("describeNowcast", () => {
  const en = STRINGS.en;
  const pl = STRINGS.pl;

  test("rain soon is said in rounded minutes", () => {
    expect(describeNowcast(dry({ startsInMinutes: 12 }), en)).toBe("rain in about 10 min");
    expect(describeNowcast(dry({ startsInMinutes: 12 }), pl)).toBe("deszcz za ok. 10 min");
  });

  test("rain a moment away is 'any minute'", () => {
    expect(describeNowcast(dry({ startsInMinutes: 2 }), en)).toBe("rain any minute");
    expect(describeNowcast(dry({ startsInMinutes: 1 }), pl)).toBe("deszcz za chwilę");
  });

  test("rain within the hour only: 60 minutes still counts, 61 does not", () => {
    expect(describeNowcast(dry({ startsInMinutes: 60 }), en)).toBe("rain in about 60 min");
    expect(describeNowcast(dry({ startsInMinutes: 61 }), en)).toBeNull();
    expect(describeNowcast(dry({ startsInMinutes: 120 }), en)).toBeNull();
  });

  test("while it rains and the end is known", () => {
    const n = dry({ ongoing: true, endsInMinutes: 22 });
    expect(describeNowcast(n, en)).toBe("raining, easing off in about 20 min");
    expect(describeNowcast(n, pl)).toBe("pada, przestanie za ok. 20 min");
  });

  test("while it rains and it is about to stop", () => {
    expect(describeNowcast(dry({ ongoing: true, endsInMinutes: 2 }), en)).toBe(
      "raining, easing off any minute",
    );
  });

  test("while it rains with no known end the usual sentence stands", () => {
    expect(describeNowcast(dry({ ongoing: true }), en)).toBeNull();
  });

  test("no rain, or no nowcast, says nothing", () => {
    expect(describeNowcast(dry({}), en)).toBeNull();
    expect(describeNowcast(null, en)).toBeNull();
  });
});

describe("nowcastFor", () => {
  // 2026-11-17 15:05 UTC is 16:05 local at +01:00.
  const nowMs = Date.UTC(2026, 10, 17, 15, 5);
  const minutely = {
    time: ["2026-11-17T16:00", "2026-11-17T16:15", "2026-11-17T16:30"],
    precipitation: [0, 0.3, 0.2],
  };

  test("reads the minutely block against the location's clock", () => {
    const r = nowcastFor({ minutely_15: minutely, utc_offset_seconds: 3600 }, nowMs);
    expect(r?.startsInMinutes).toBe(10);
    expect(r?.totalMm).toBeCloseTo(0.5);
  });

  test("a forecast cached before minutely data existed gives nothing", () => {
    expect(nowcastFor({ utc_offset_seconds: 3600 }, nowMs)).toBeNull();
  });

  test("without an offset there is no local clock to read against", () => {
    expect(nowcastFor({ minutely_15: minutely }, nowMs)).toBeNull();
  });

  test("steps long over give nothing", () => {
    expect(
      nowcastFor({ minutely_15: minutely, utc_offset_seconds: 3600 }, nowMs + 3_600_000),
    ).toBeNull();
  });

  test("a short precipitation array reads the missing steps as unknown, not rain", () => {
    const r = nowcastFor(
      { minutely_15: { time: minutely.time, precipitation: [0] }, utc_offset_seconds: 3600 },
      nowMs,
    );
    expect(r).toEqual({ ongoing: false, startsInMinutes: null, endsInMinutes: null, totalMm: 0 });
  });
});

describe("rebaseWeather", () => {
  test("keeps the minutely block of a stale forecast", () => {
    const data = {
      current: {
        time: "2026-11-17T10:00",
        weather_code: 0,
        temperature_2m: 5,
        apparent_temperature: 5,
      },
      hourly: {
        time: ["2026-11-17T10:00", "2026-11-17T11:00"],
        temperature_2m: [5, 6],
        weather_code: [0, 0],
        precipitation_probability: [0, 0],
      },
      daily: {
        time: ["2026-11-17"],
        weather_code: [0],
        temperature_2m_max: [8],
        temperature_2m_min: [1],
        precipitation_probability_max: [0],
        sunrise: ["2026-11-17T07:00"],
        sunset: ["2026-11-17T16:00"],
      },
      minutely_15: { time: ["2026-11-17T10:00"], precipitation: [0] },
      timezone: "UTC",
      utc_offset_seconds: 0,
    } as unknown as WeatherResponse;
    const out = rebaseWeather(data, Date.UTC(2026, 10, 17, 11, 20));
    expect(out.minutely_15).toEqual(data.minutely_15);
    expect(out.current.time).toBe("2026-11-17T11:00");
  });
});
