import { describe, expect, test } from "bun:test";
import { countValue, easeOutCubic } from "./useCountUp";

describe("the count-up curve", () => {
  test("starts at the start, ends at the end, never overshoots", () => {
    expect(easeOutCubic(0)).toBe(0);
    expect(easeOutCubic(1)).toBe(1);
    expect(easeOutCubic(2)).toBe(1);
    expect(easeOutCubic(-1)).toBe(0);
    let prev = 0;
    for (let t = 0; t <= 1; t += 0.05) {
      const v = easeOutCubic(t);
      expect(v).toBeGreaterThanOrEqual(prev);
      prev = v;
    }
  });
  test("eases out: more than half way in the first third", () => {
    expect(easeOutCubic(1 / 3)).toBeGreaterThan(0.5);
  });
  test("counts whole numbers, up and down, and holds the end", () => {
    expect(countValue(0, 20, 0, 600)).toBe(0);
    expect(countValue(0, 20, 600, 600)).toBe(20);
    expect(countValue(0, 20, 5000, 600)).toBe(20);
    expect(Number.isInteger(countValue(0, 20, 123, 600))).toBe(true);
    expect(countValue(20, -4, 300, 600)).toBeLessThan(20);
    expect(countValue(20, -4, 300, 600)).toBeGreaterThan(-4);
    expect(countValue(5, 9, 100, 0)).toBe(9);
  });
});
