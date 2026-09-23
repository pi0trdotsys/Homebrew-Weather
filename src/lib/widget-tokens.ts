// Design tokens for the "Terminal 2.0" 4×2 home-screen widget mockup.
//
// Everything tagged "native" mirrors android/app/src/main/res/values/widget_colors.xml
// (and the layout in weather_widget.xml) 1:1 — keep hex values in sync with those
// files (see docs/widget-spec.md for the full element → @+id mapping).
// Tokens tagged "mockup-only" are HUD/neon accents that only exist in the web
// preview; docs/widget-spec.md maps each to the nearest widget_colors.xml entry or
// to a new drawable/color to add on the native side.

export type WidgetState = "ok" | "refreshing" | "stale" | "offline" | "no-location";

// ---------------------------------------------------------------------------
// Colors
// ---------------------------------------------------------------------------
export const WIDGET_COLORS = {
  // native (widget_colors.xml)
  bg: "#0a0f0a",
  green: "#33ff66",
  greenDim: "#4a6a4a",
  amber: "#ffb000",
  amberDim: "#806000",
  cyan: "#55ffff",
  cyanDim: "#2a6666",
  crimson: "#ff5555",
  crimsonDim: "#703030",
  online: "#33ff66",
  offline: "#ff5555",
  aqiGood: "#33ff66",
  aqiModerate: "#ffb000",
  aqiSensitive: "#ff7a1a",
  aqiUnhealthy: "#ff3b3b",
  aqiVeryUnhealthy: "#a64dff",
  aqiHazardous: "#7f0000",

  // mockup-only HUD accents (defined as CSS vars in src/styles.css too)
  hudCyan: "#55ffff",
  hudCyanDim: "#2a6666",
  hudGrid: "rgba(85, 255, 255, 0.10)",
  hudGlow: "rgba(85, 255, 255, 0.45)",
  hudLine: "rgba(85, 255, 255, 0.35)",
  hudScan: "rgba(85, 255, 255, 0.06)",
  hudMagenta: "#ff4fd8",
};

/** Returns a hex color as rgba() with the given alpha (0..1). */
export function withAlpha(hex: string, alpha: number): string {
  const h = hex.replace("#", "");
  const full =
    h.length === 3
      ? h
          .split("")
          .map((c) => c + c)
          .join("")
      : h;
  const n = parseInt(full, 16);
  const r = (n >> 16) & 255;
  const g = (n >> 8) & 255;
  const b = n & 255;
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

// ---------------------------------------------------------------------------
// Metrics — the REFERENCE design (scale 1.0); preview renders it at 1.8×.
//
// These numbers no longer dictate what the native widget draws, and saying so
// here is the point. The native widget derives every dp and sp at render time
// from the footprint the launcher actually granted that instance
// (WidgetMetrics.kt), because a fixed ladder was only ever correct at one size:
// a real 4×2 placement on the reporting device measured 368×176dp, while this
// file asserted 250×110 and the native layout had separately drifted to a third
// number. Chasing one blessed footprint is what this stopped doing.
//
// What survives here is the reference design the native ladder scales from —
// its rows at scale 1.0, which is what the mockup renders. Native at some other
// size is these proportions times a scale factor, not a different layout. Keep
// them in sync with WidgetMetrics.kt's REF_* constants; the colours below
// remain a strict 1:1 contract with widget_colors.xml.
// ---------------------------------------------------------------------------
export const METRICS = {
  cells: [4, 2] as const,
  /** Footprint at which WidgetMetrics solves to scale 1.0. */
  dp: { width: 368, height: 192 },
  scale: 1.8,
  padDp: 6,
  radiusDp: 5,
  /** Arm length of the HUD corner brackets (widget_hud_corners.xml). */
  cornerDp: 11,
  /** HUD background grid pitch (widget_hud_grid_tile.png). */
  gridCellDp: 16,
};

/** dp/sp → preview px */
export const dp = (v: number): number => Math.round(v * METRICS.scale * 100) / 100;

export const PREVIEW = {
  width: dp(METRICS.dp.width),
  height: dp(METRICS.dp.height),
};

// ---------------------------------------------------------------------------
// Typography — authored in sp (native truth), exposed in px for the mockup.
// 8sp is the hard floor: anything smaller is unreadable on a home screen.
// ---------------------------------------------------------------------------
export const SP_SCALE = {
  header: 11,
  hero: 28,
  heroUnit: 12,
  condition: 11,
  dayLabel: 11,
  temp: 14,
  pop: 10,
  meta: 10,
  footer: 11,
  /** Hero stat column: AQI, sync clock, "▽ max%". */
  stat: 10,
  /** Hero PoP sparkline (▁▂▃▅▇ blocks). */
  spark: 14,
} as const;

export type TypeToken = keyof typeof SP_SCALE;

export const TYPE_SCALE = Object.fromEntries(
  Object.entries(SP_SCALE).map(([k, sp]) => [k, dp(sp)]),
) as Record<TypeToken, number>;

// ---------------------------------------------------------------------------
// Vertical budget at scale 1.0 — must fit METRICS.dp.height minus padding.
//
// Text rows are the sp size times their measured line factor, and that factor
// is not constant: Latin monospace with includeFontPadding="false" measures
// ~1.16×, but a row containing a fallback-font glyph (the `▽` PoP marker, the
// header's `┌─ ─┐` box drawing) measures ~1.35× because the fallback font
// carries its own taller metrics. Both factors were read off real device
// renders. Assuming a single factor is what made earlier budgets simultaneously
// over- and under-reserve rows, and silently shear the bottom one.
// ---------------------------------------------------------------------------
export const ROWS = {
  header: 14.7, // 11sp × 1.34 (box drawing)
  gapA: 3,
  hero: 46, // temp 28sp × 1.17 + condition 11sp × 1.16
  rule: 1,
  gapB: 8, // the rule's margins, 4dp either side
  grid: 74.9, // GRID_ROWS + 7dp of column padding and icon margins
  gapC: 4,
  meta: 11.7, // 10sp × 1.17
  gapD: 3,
  footer: 12.8, // 11sp × 1.16
} as const;

/** Inner rows of one forecast column — must sum to at most ROWS.grid. */
export const GRID_ROWS = {
  label: 13, // 11sp × 1.16
  icon: 20,
  temp: 16.3, // 14sp × 1.16
  /** Temperature range bar (WidgetGraphics.rangeBar). */
  bar: 5,
  pop: 13.6, // 10sp × 1.36 (▽ glyph)
} as const;

export const LAYOUT = {
  heroIconDp: 30,
  gridIconDp: 20,
  sparkBarDp: 3,
  sparkGapDp: 1,
  sparkHeightDp: 17.5, // 14sp × 1.25, block glyphs
  refreshHitDp: 13,
  dotDp: 8,
  columnGapDp: 1,
  barHeightDp: 5,
} as const;

export type FitReport = {
  contentHeightDp: number;
  usedDp: number;
  slackDp: number;
  fits: boolean;
  gridUsedDp: number;
  gridFits: boolean;
  rows: { name: string; dp: number }[];
};

/** Static assertion helper — surfaced in /mockups so a regression is visible. */
export function fitReport(): FitReport {
  const contentHeightDp = METRICS.dp.height - METRICS.padDp * 2;
  const rows = Object.entries(ROWS).map(([name, v]) => ({ name, dp: v }));
  const usedDp = rows.reduce((a, r) => a + r.dp, 0);
  const gridUsedDp = Object.values(GRID_ROWS).reduce((a, v) => a + v, 0);
  return {
    contentHeightDp,
    usedDp,
    slackDp: contentHeightDp - usedDp,
    fits: usedDp <= contentHeightDp,
    gridUsedDp,
    gridFits: gridUsedDp <= ROWS.grid,
    rows,
  };
}


// Per-widget-instance background fill transparency (mirrors WidgetTransparency.kt).
export const TRANSPARENCY_LEVELS = [
  { id: "opaque", label: "100%", alpha: 1 },
  { id: "high", label: "85%", alpha: 0.85 },
  { id: "medium", label: "60%", alpha: 0.6 },
  { id: "low", label: "35%", alpha: 0.35 },
] as const;

// ---------------------------------------------------------------------------
// State variants
// ---------------------------------------------------------------------------
export type StateVariant = {
  label: string;
  code: string;
  dot: string;
  header: string;
  banner: string | null;
  bannerColor: string;
  dimData: boolean;
  blink: boolean;
  sweepFast: boolean;
};

export const STATE_VARIANTS: Record<WidgetState, StateVariant> = {
  ok: {
    label: "ok",
    code: "200 OK",
    dot: WIDGET_COLORS.online,
    header: WIDGET_COLORS.green,
    banner: null,
    bannerColor: WIDGET_COLORS.green,
    dimData: false,
    blink: true,
    sweepFast: false,
  },
  refreshing: {
    label: "refreshing",
    code: "GET /forecast",
    dot: WIDGET_COLORS.online,
    header: WIDGET_COLORS.cyan,
    banner: "⟳ refreshing…",
    bannerColor: WIDGET_COLORS.cyan,
    dimData: false,
    blink: true,
    sweepFast: true,
  },
  stale: {
    label: "stale",
    code: "304 retrying",
    dot: WIDGET_COLORS.amber,
    header: WIDGET_COLORS.amber,
    banner: "⚠ stale · retrying",
    bannerColor: WIDGET_COLORS.amber,
    dimData: true,
    blink: true,
    sweepFast: false,
  },
  offline: {
    label: "offline",
    code: "0 net · cache",
    dot: WIDGET_COLORS.offline,
    header: WIDGET_COLORS.crimson,
    banner: "offline · serving cached snapshot",
    bannerColor: WIDGET_COLORS.offline,
    dimData: true,
    blink: false,
    sweepFast: false,
  },
  "no-location": {
    label: "no-location",
    code: "404 city",
    dot: WIDGET_COLORS.crimson,
    header: WIDGET_COLORS.crimson,
    banner: null,
    bannerColor: WIDGET_COLORS.crimson,
    dimData: true,
    blink: true,
    sweepFast: false,
  },
};

// ---------------------------------------------------------------------------
// POP → sparkline mapping
// ---------------------------------------------------------------------------
export type Sparkline = {
  bars: number[]; // normalized 0..1, one entry per day
  max: number; // max daily PoP across the window
  hasRain: boolean; // any day >= 50%
};

export function popToSparkline(pop: number[]): Sparkline {
  const max = Math.max(1, ...pop);
  return {
    bars: pop.map((p) => p / max),
    max,
    hasRain: pop.some((p) => p >= 50),
  };
}

// ---------------------------------------------------------------------------
// AQI helpers (mirrors WeatherWidgetProvider.aqiLabelAndColor)
// ---------------------------------------------------------------------------
export function aqiLabel(aqi: number): string {
  if (aqi <= 50) return "good";
  if (aqi <= 100) return "moderate";
  if (aqi <= 150) return "sensitive";
  if (aqi <= 200) return "unhealthy";
  if (aqi <= 300) return "very unhealthy";
  return "hazardous";
}

export function aqiColor(aqi: number): string {
  if (aqi <= 50) return WIDGET_COLORS.aqiGood;
  if (aqi <= 100) return WIDGET_COLORS.aqiModerate;
  if (aqi <= 150) return WIDGET_COLORS.aqiSensitive;
  if (aqi <= 200) return WIDGET_COLORS.aqiUnhealthy;
  if (aqi <= 300) return WIDGET_COLORS.aqiVeryUnhealthy;
  return WIDGET_COLORS.aqiHazardous;
}

// ---------------------------------------------------------------------------
// Mock data
// ---------------------------------------------------------------------------
export const PL_DOW = ["nd", "pn", "wt", "śr", "cz", "pt", "sb"];

export function dayLabel(isoDate: string, index: number): string {
  if (index === 0) return "dziś";
  const d = new Date(`${isoDate}T12:00:00Z`);
  if (Number.isNaN(d.getTime())) return "?";
  return PL_DOW[d.getUTCDay()];
}

function isoDaysFromNow(offset: number): string {
  const d = new Date();
  d.setUTCHours(12, 0, 0, 0);
  d.setUTCDate(d.getUTCDate() + offset);
  return d.toISOString().slice(0, 10);
}

export type MockDay = {
  date: string;
  code: number; // Open-Meteo WMO weather code
  tempDay: number;
  tempNight: number;
  pop: number; // daily precipitation probability, %
};

export const MOCK_DAYS: MockDay[] = [
  { date: isoDaysFromNow(0), code: 2, tempDay: 24, tempNight: 14, pop: 20 },
  { date: isoDaysFromNow(1), code: 61, tempDay: 19, tempNight: 12, pop: 85 },
  { date: isoDaysFromNow(2), code: 3, tempDay: 17, tempNight: 11, pop: 35 },
  { date: isoDaysFromNow(3), code: 0, tempDay: 22, tempNight: 13, pop: 5 },
];

export const MOCK_WIDGET = {
  location: "Warszawa",
  isLive: false,
  now: { temp: 21, code: 2, isDay: true },
  nowPop: 20,
  aqi: 42,
  sigma: "grindset weather. wychodzisz albo zostajesz nikim",
  days: MOCK_DAYS,
};
