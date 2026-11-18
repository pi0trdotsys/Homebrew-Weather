// What the city backdrop draws and where (components/CityBackdrop.tsx). Web
// twin of the layout decisions in android/.../widget/WidgetBackdropArt.kt: contour
// lines of the terrain around the place where it has relief, a pixel skyline
// made from the city's name where it doesn't. Pure, so it can be tested; the
// canvas code only paints what this returns.
import { niceStep, segments, smooth, upsample } from "./contours";
import { buildings, type Building } from "./skyline";
import { relief, type Grid } from "./terrain";

/** Contours need at least this much height difference (metres) to be worth drawing. */
export const RELIEF_MIN_M = 40;

/** Bilinear enlargement of the 24 x 12 grid before contouring. */
export const UPSAMPLE = 6;

/** Alpha of a contour line, and of its every-fifth "major" one. */
export const CONTOUR_ALPHA = { minor: 0.14, major: 0.28 } as const;
/** Alpha of the skyline's mass and of its lit windows. */
export const SKYLINE_ALPHA = { mass: 0.085, windows: 0.2 } as const;
/** Everything is dimmed to this fraction under high contrast, which is about legibility. */
export const QUIET = 0.55;

/** One CSS pixel of skyline is this many; the skyline's own "pixel unit". */
export const SKYLINE_UNIT = 3;

export type BackdropKind = "contours" | "skyline";

export const backdropKind = (grid: Grid | null): BackdropKind =>
  grid !== null && relief(grid) >= RELIEF_MIN_M ? "contours" : "skyline";

export type ContourLevel = {
  level: number;
  /** Every fifth line is a "major" one, as on a topographic map. */
  major: boolean;
  /** [x1, y1, x2, y2] in pixels. */
  lines: Array<[number, number, number, number]>;
};

/**
 * The contour lines of [grid], scaled to cover [w] x [h] pixels undistorted and
 * centred — like the widget does, so a wide strip shows a band of the map.
 */
export function contourPlan(grid: Grid, w: number, h: number): ContourLevel[] {
  const smoothed = smooth(grid.elevation, grid.cols, grid.rows);
  const field = upsample(smoothed, grid.cols, grid.rows, UPSAMPLE);
  let lo = Infinity;
  let hi = -Infinity;
  for (const v of field.data) {
    if (v < lo) lo = v;
    if (v > hi) hi = v;
  }
  const step = niceStep(hi - lo);
  const scale = Math.max(w / (field.w - 1), h / (field.h - 1));
  const offX = (w - (field.w - 1) * scale) / 2;
  const offY = (h - (field.h - 1) * scale) / 2;

  const out: ContourLevel[] = [];
  for (let level = Math.ceil(lo / step) * step; level <= hi; level += step) {
    const major = Math.round(level / step) % 5 === 0;
    out.push({
      level,
      major,
      lines: segments(field.data, field.w, field.h, level).map((s) => [
        offX + s.x1 * scale,
        offY + s.y1 * scale,
        offX + s.x2 * scale,
        offY + s.y2 * scale,
      ]),
    });
  }
  return out;
}

export type SkylinePlan = { unit: number; buildings: Building[] };

/** The skyline for [name] across [w] x [h] pixels: buildings up to 36% of the height. */
export function skylinePlan(name: string, w: number, h: number): SkylinePlan {
  const unit = SKYLINE_UNIT;
  return {
    unit,
    buildings: buildings(name, Math.floor(w / unit) + 1, Math.floor((h * 0.36) / unit)),
  };
}
