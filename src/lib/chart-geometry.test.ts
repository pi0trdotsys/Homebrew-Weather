import { describe, expect, test } from "bun:test";
import type { WeatherResponse } from "./weather-api";
import {
  chartHours,
  extremes,
  nightSpans,
  plotBox,
  posToX,
  rainColumnHeight,
  rainFraction,
  RAIN_SHARE,
  sunMarkers,
  tempScale,
  tickIndices,
  xToIndex,
} from "./chart-geometry";

/** 24 local hours from [first] ("2026-11-18T06:00") on, one a time, across midnight if need be. */
function hoursFrom(date: string, hour: number, n = 24): string[] {
  const t0 = Date.UTC(+date.slice(0, 4), +date.slice(5, 7) - 1, +date.slice(8, 10), hour);
  return Array.from({ length: n }, (_, i) =>
    new Date(t0 + i * 3_600_000).toISOString().slice(0, 16),
  );
}

describe("the plot box", () => {
  const box = plotBox(340, 200);
  test("hours are spread evenly from the left edge to the right edge", () => {
    expect(posToX(0, 24, box)).toBe(box.x0);
    expect(posToX(23, 24, box)).toBe(box.x1);
    expect(posToX(11.5, 24, box)).toBeCloseTo((box.x0 + box.x1) / 2, 9);
  });
  test("a single point sits in the middle", () => {
    expect(posToX(0, 1, box)).toBe((box.x0 + box.x1) / 2);
  });
  test("an x snaps to the nearest hour and stays inside the data", () => {
    expect(xToIndex(box.x0, 24, box)).toBe(0);
    expect(xToIndex(box.x1, 24, box)).toBe(23);
    expect(xToIndex(-500, 24, box)).toBe(0);
    expect(xToIndex(5000, 24, box)).toBe(23);
    const step = (box.x1 - box.x0) / 23;
    expect(xToIndex(box.x0 + step * 7.4, 24, box)).toBe(7);
    expect(xToIndex(box.x0 + step * 7.6, 24, box)).toBe(8);
  });
  test("a tiny SVG still gives a usable box", () => {
    const b = plotBox(10, 10);
    expect(b.x1).toBeGreaterThan(b.x0);
    expect(b.y1).toBeGreaterThan(b.y0);
  });
});

describe("rain columns", () => {
  test("height is mm / 4, clamped to 0.15..1", () => {
    expect(rainFraction(2, 80)).toBe(0.5);
    expect(rainFraction(0.2, 80)).toBe(0.15);
    expect(rainFraction(4, 80)).toBe(1);
    expect(rainFraction(30, 100)).toBe(1);
  });
  test("a dry hour has no column, whatever the chance says, once the amount is known", () => {
    expect(rainFraction(0, 90)).toBe(0);
    expect(rainFraction(0.04, 90)).toBe(0);
  });
  test("without an amount, a likely hour gets a middling column and an unlikely one none", () => {
    expect(rainFraction(null, 50)).toBeGreaterThan(0.2);
    expect(rainFraction(null, 50)).toBeLessThan(0.7);
    expect(rainFraction(null, 49)).toBe(0);
    expect(rainFraction(null, 100)).toBe(rainFraction(null, 50));
  });
  test("pixels are a fixed share of the plot", () => {
    expect(rainColumnHeight(8, 100, 200)).toBeCloseTo(RAIN_SHARE * 200, 9);
    expect(rainColumnHeight(2, 100, 100)).toBeCloseTo(0.5 * RAIN_SHARE * 100, 9);
    expect(rainColumnHeight(0, 100, 200)).toBe(0);
  });
});

describe("sun markers", () => {
  test("a sunrise at the exact minute sits a fraction past its hour", () => {
    const times = hoursFrom("2026-11-18", 0);
    const [m] = sunMarkers(times, ["2026-11-18T07:15"], []);
    expect(m.kind).toBe("sunrise");
    expect(m.pos).toBeCloseTo(7.25, 9);
    expect(m.label).toBe("07:15");
  });

  test("a sunrise exactly on the hour is exactly that hour's position", () => {
    const times = hoursFrom("2026-11-18", 0);
    const m = sunMarkers(times, ["2026-11-18T07:00"], []);
    expect(m).toEqual([{ kind: "sunrise", pos: 7, label: "07:00" }]);
  });

  test("the first and the last hour of the window are inside it", () => {
    const times = hoursFrom("2026-11-18", 6);
    expect(sunMarkers(times, ["2026-11-18T06:00"], [])[0].pos).toBe(0);
    expect(sunMarkers(times, [], ["2026-11-19T05:00"])[0].pos).toBe(23);
    expect(sunMarkers(times, ["2026-11-18T05:59"], ["2026-11-19T05:01"])).toEqual([]);
  });

  test("a window that crosses midnight takes the next day's sunrise, in order", () => {
    // 20:00 on the 18th to 19:00 on the 19th.
    const times = hoursFrom("2026-11-18", 20);
    const ms = sunMarkers(
      times,
      ["2026-11-18T07:12", "2026-11-19T07:12", "2026-11-20T07:13"],
      ["2026-11-18T16:10", "2026-11-19T16:09", "2026-11-20T16:08"],
    );
    expect(ms.map((m) => [m.kind, m.label])).toEqual([
      ["sunrise", "07:12"],
      ["sunset", "16:09"],
    ]);
    expect(ms[0].pos).toBeCloseTo(11.2, 9); // 20:00 + 11 h = 07:00, then 12 min
    expect(ms[1].pos).toBeCloseTo(20 + 9 / 60, 9); // 16:00 is the 20th hour
  });

  test("missing or empty sun times (polar day) are skipped", () => {
    const times = hoursFrom("2026-06-21", 0);
    expect(sunMarkers(times, ["", undefined, null], [""])).toEqual([]);
    expect(sunMarkers([], ["2026-06-21T03:00"], [])).toEqual([]);
  });

  test("the sunrise and the sunset of one day come out in order", () => {
    const times = hoursFrom("2026-11-18", 0);
    const ms = sunMarkers(times, ["2026-11-18T07:12"], ["2026-11-18T16:10"]);
    expect(ms.map((m) => m.kind)).toEqual(["sunrise", "sunset"]);
  });
});

describe("night bands", () => {
  const hoursWith = (times: string[], isDay: (boolean | null)[]) =>
    times.map((time, i) => ({ time, isDay: isDay[i] }));

  test("a day that starts at night: one band from the left edge to the sunrise", () => {
    const times = hoursFrom("2026-11-18", 0);
    const isDay = times.map((_, i) => i >= 7 && i < 16);
    const markers = sunMarkers(times, ["2026-11-18T06:40"], ["2026-11-18T15:30"]);
    const spans = nightSpans(hoursWith(times, isDay), markers);
    expect(spans.length).toBe(2);
    // is_day flips between hour 6 (night) and 7 (day); the sunrise at 06:40 is within that step.
    expect(spans[0][0]).toBe(0);
    expect(spans[0][1]).toBeCloseTo(6 + 40 / 60, 9);
    expect(spans[1][0]).toBeCloseTo(15.5, 9);
    expect(spans[1][1]).toBe(23);
  });

  test("a window that crosses midnight: day, a night in the middle, day again", () => {
    // From 10:00 on the 18th: sunset 16:10 (is_day is still 1 at 16:00 and 0 at 17:00), then night
    // until the 07:00 sunrise, which is exactly hour 21 of the window.
    const times = hoursFrom("2026-11-18", 10);
    const isDay = times.map((t) => {
      const h = Number(t.slice(11, 13));
      return h >= 7 && h <= 16;
    });
    const markers = sunMarkers(times, ["2026-11-19T07:00"], ["2026-11-18T16:10"]);
    const spans = nightSpans(hoursWith(times, isDay), markers);
    expect(spans.length).toBe(1);
    expect(spans[0][0]).toBeCloseTo(6 + 10 / 60, 9);
    expect(spans[0][1]).toBe(21);
  });

  test("a sunrise exactly on the hour puts the edge on that hour", () => {
    const times = hoursFrom("2026-11-18", 4, 6); // 04..09
    const isDay = [false, false, false, true, true, true]; // 04,05,06 night; 07 day
    const markers = sunMarkers(times, ["2026-11-18T07:00"], []);
    expect(nightSpans(hoursWith(times, isDay), markers)).toEqual([[0, 3]]);
  });

  test("without a matching sun marker the edge is halfway between the two hours", () => {
    const times = hoursFrom("2026-11-18", 12, 6);
    const isDay = [true, true, false, false, false, false];
    expect(nightSpans(hoursWith(times, isDay), [])).toEqual([[1.5, 5]]);
  });

  test("all day and all night", () => {
    const times = hoursFrom("2026-11-18", 12, 5);
    expect(nightSpans(hoursWith(times, [true, true, true, true, true]), [])).toEqual([]);
    expect(nightSpans(hoursWith(times, [false, false, false, false, false]), [])).toEqual([[0, 4]]);
  });

  test("without is_day the sun times decide, per date", () => {
    const times = hoursFrom("2026-11-18", 12, 24); // 12:00 on the 18th to 11:00 on the 19th
    const daily = {
      time: ["2026-11-18", "2026-11-19"],
      sunrise: ["2026-11-18T07:00", "2026-11-19T07:02"],
      sunset: ["2026-11-18T16:00", "2026-11-19T16:01"],
    };
    const markers = sunMarkers(times, daily.sunrise, daily.sunset);
    const spans = nightSpans(
      times.map((time) => ({ time, isDay: null })),
      markers,
      daily,
    );
    expect(spans.length).toBe(1);
    expect(spans[0][0]).toBe(4); // sunset 16:00 is exactly hour 4 (it flips between 15:00 and 16:00)
    expect(spans[0][1]).toBeCloseTo(19 + 2 / 60, 9); // sunrise 07:02 lies in the 19 -> 20 step
  });

  test("an hour that can't be told (no is_day, no sun times) counts as day", () => {
    const times = hoursFrom("2026-11-18", 0, 4);
    expect(
      nightSpans(
        times.map((time) => ({ time, isDay: null })),
        [],
        { time: [], sunrise: [], sunset: [] },
      ),
    ).toEqual([]);
  });
});

describe("ticks and scale", () => {
  test("ticks fall on every third hour of the local clock, across midnight", () => {
    const times = hoursFrom("2026-11-18", 20, 12); // 20:00 .. 07:00
    expect(tickIndices(times).map((i) => times[i].slice(11, 16))).toEqual([
      "21:00",
      "00:00",
      "03:00",
      "06:00",
    ]);
  });

  test("the high and the low are the first of their kind", () => {
    expect(extremes([3, 9, 1, 9, 1, 4])).toEqual({ hi: 1, lo: 2 });
    expect(extremes([5, 5, 5])).toEqual({ hi: 0, lo: 0 });
  });

  test("the scale puts the high above the low and keeps clear of the edges", () => {
    const box = plotBox(340, 200);
    const y = tempScale([2, 9, 5], box);
    expect(y(9)).toBeLessThan(y(2));
    expect(y(9)).toBeGreaterThanOrEqual(box.y0);
    expect(y(2)).toBeLessThanOrEqual(box.y1);
  });

  test("a flat day is not stretched", () => {
    const box = plotBox(340, 200);
    const y = tempScale([10, 10.5], box);
    expect(y(10) - y(10.5)).toBeLessThan(20);
  });
});

describe("chartHours", () => {
  const time = hoursFrom("2026-11-18", 0, 48);
  const data = {
    current: { time: "2026-11-18T10:20" },
    hourly: {
      time,
      temperature_2m: time.map((_, i) => i),
      weather_code: time.map(() => 3),
      precipitation_probability: time.map((_, i) => (i === 12 ? 80 : 0)),
      precipitation: time.map((_, i) => (i === 12 ? 0.8 : 0)),
      is_day: time.map((_, i) => (i % 24 >= 7 && i % 24 < 16 ? 1 : 0)),
    },
    daily: {},
  } as unknown as WeatherResponse;

  test("24 hours from the current hour", () => {
    const h = chartHours(data);
    expect(h.length).toBe(24);
    expect(h[0].time).toBe("2026-11-18T10:00");
    expect(h[2].mm).toBe(0.8);
    expect(h[2].pop).toBe(80);
    expect(h[0].isDay).toBe(true);
    expect(h[7].isDay).toBe(false);
  });

  test("a given day's own hours", () => {
    const h = chartHours(data, "2026-11-19");
    expect(h.length).toBe(24);
    expect(h[0].time).toBe("2026-11-19T00:00");
  });

  test("missing amounts and day flags come out as null", () => {
    const bare = {
      ...data,
      hourly: { ...data.hourly, precipitation: undefined, is_day: undefined },
    } as unknown as WeatherResponse;
    const h = chartHours(bare);
    expect(h[0].mm).toBeNull();
    expect(h[0].isDay).toBeNull();
  });

  test("near the end of the data there are fewer than 24", () => {
    const late = { ...data, current: { time: "2026-11-19T20:00" } } as unknown as WeatherResponse;
    expect(chartHours(late).length).toBe(4);
  });
});
