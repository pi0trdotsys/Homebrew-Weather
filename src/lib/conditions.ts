// Readings that are only worth a line when they are high: the UV index and
// wind gusts. Thresholds match the defaults of the native notification
// settings (settings:notif-uv-threshold / settings:notif-gust-threshold), and
// the UV bands are the same ones the widget and the morning brief use.

/** UV index at which the sun is worth mentioning ("very high"). */
export const UV_NOTABLE = 8;

/** Gusts, km/h, from which wind is worth mentioning. */
export const GUST_NOTABLE = 60;

export type UvBand = "low" | "moderate" | "high" | "veryHigh" | "extreme";

/** <3 low, 3-5 moderate, 6-7 high, 8-10 very high, >=11 extreme. */
export function uvBand(uv: number): UvBand {
  const v = Math.round(uv);
  if (v >= 11) return "extreme";
  if (v >= 8) return "veryHigh";
  if (v >= 6) return "high";
  if (v >= 3) return "moderate";
  return "low";
}
