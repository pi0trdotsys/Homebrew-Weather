import { describe, expect, test } from "bun:test";
import {
  backdropKind,
  contourPlan,
  RELIEF_MIN_M,
  SKYLINE_ALPHA,
  CONTOUR_ALPHA,
  skylinePlan,
} from "./backdrop";
import type { Grid } from "./terrain";

const grid = (fn: (x: number, y: number) => number, cols = 24, rows = 12): Grid => ({
  cols,
  rows,
  elevation: Array.from({ length: cols * rows }, (_, i) => fn(i % cols, Math.floor(i / cols))),
});

describe("which picture", () => {
  test("no grid yet, or flat land, is a skyline", () => {
    expect(backdropKind(null)).toBe("skyline");
    expect(backdropKind(grid(() => 120))).toBe("skyline");
    expect(backdropKind(grid((x) => 100 + (x === 3 ? RELIEF_MIN_M - 1 : 0)))).toBe("skyline");
  });
  test("relief of 40 m or more is contours", () => {
    expect(backdropKind(grid((x) => 100 + (x === 3 ? RELIEF_MIN_M : 0)))).toBe("contours");
    expect(backdropKind(grid((x, y) => 200 + x * 10 + y * 5))).toBe("contours");
  });
});

describe("contour plan", () => {
  const hill = grid((x, y) => Math.round(300 - Math.hypot(x - 11.5, (y - 5.5) * 2) * 12));
  test("lines stay inside a canvas that exactly matches the map's shape, and every fifth is major", () => {
    const plan = contourPlan(hill, 460, 220);
    expect(plan.length).toBeGreaterThan(3);
    for (const lv of plan) {
      expect(lv.major).toBe(Math.round(lv.level / (plan[1].level - plan[0].level)) % 5 === 0);
      for (const [x1, y1, x2, y2] of lv.lines) {
        for (const v of [x1, x2]) expect(v).toBeGreaterThanOrEqual(-1e-6);
        for (const v of [y1, y2]) expect(v).toBeGreaterThanOrEqual(-1e-6);
        expect(Math.max(x1, x2)).toBeLessThanOrEqual(460 + 1e-6);
        expect(Math.max(y1, y2)).toBeLessThanOrEqual(220 + 1e-6);
      }
    }
    expect(plan.some((lv) => lv.major)).toBe(true);
    expect(plan.some((lv) => !lv.major)).toBe(true);
  });
  test("a wide strip crops the map instead of stretching it", () => {
    const square = contourPlan(hill, 460, 220);
    const wide = contourPlan(hill, 460, 55);
    const ys = wide.flatMap((lv) => lv.lines.flatMap((l) => [l[1], l[3]]));
    // The cover-scale is set by the width, so the map is 220 tall and only its middle 55 px show.
    expect(Math.min(...ys)).toBeLessThan(-1);
    expect(Math.max(...ys)).toBeGreaterThan(56);
    expect(wide.length).toBe(square.length);
  });
});

describe("skyline plan", () => {
  test("is the name's skyline, filling the width, no taller than a third", () => {
    const { unit, buildings } = skylinePlan("Wrocław", 600, 130);
    expect(buildings.length).toBeGreaterThan(10);
    const last = buildings[buildings.length - 1];
    expect((last.x + last.width) * unit).toBeGreaterThanOrEqual(600 - unit * 10);
    for (const b of buildings) expect(b.height * unit).toBeLessThanOrEqual(130 * 0.36 + unit);
  });
  test("a strip too short for buildings draws none", () => {
    expect(skylinePlan("Wrocław", 600, 8).buildings).toEqual([]);
  });
});

describe("alphas", () => {
  test("stay quiet: contours 12-30%, skyline mass at most 9%, windows at most 20%", () => {
    expect(CONTOUR_ALPHA.minor).toBeGreaterThanOrEqual(0.12);
    expect(CONTOUR_ALPHA.major).toBeLessThanOrEqual(0.3);
    expect(CONTOUR_ALPHA.major).toBeGreaterThan(CONTOUR_ALPHA.minor);
    expect(SKYLINE_ALPHA.mass).toBeLessThanOrEqual(0.09);
    expect(SKYLINE_ALPHA.windows).toBeLessThanOrEqual(0.2);
  });
});
