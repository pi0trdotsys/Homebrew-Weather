// One colour scale for temperature, shared by the dashboard's big number, the
// 24-hour chart and the widget. Web twin of android/.../widget/TempColor.kt:
// the two must give identical results, which testdata/parity/temp-color.json
// checks on both sides.
//
// Four stops, plain linear blending between neighbours, clamped at both ends:
// a cold blue below freezing, cyan at 0, the app's amber at 22, crimson from 35.
// Unknown reads as the freezing cyan.

/** [°C, 0xRRGGBB], ascending. */
const STOPS: ReadonlyArray<readonly [number, number]> = [
  [-15, 0x7a9bff], // bitter cold
  [0, 0x55ffff], //   freezing: the app's cyan
  [22, 0xffb000], //  warm: the app's amber
  [35, 0xff5555], //  hot: the app's crimson
];

/** The colour for [celsius] as 0xRRGGBB; unknown (null, undefined, NaN) reads as the freezing cyan. */
export function tempRgbInt(celsius: number | null | undefined): number {
  if (celsius == null || Number.isNaN(celsius)) return STOPS[1][1];
  if (celsius <= STOPS[0][0]) return STOPS[0][1];
  if (celsius >= STOPS[STOPS.length - 1][0]) return STOPS[STOPS.length - 1][1];
  let i = 0;
  for (let k = 0; k < STOPS.length; k++) if (STOPS[k][0] <= celsius) i = k;
  const [t0, c0] = STOPS[i];
  const [t1, c1] = STOPS[i + 1];
  const f = (celsius - t0) / (t1 - t0);
  const ch = (shift: number) => {
    const a = (c0 >> shift) & 0xff;
    const b = (c1 >> shift) & 0xff;
    return Math.floor(a + (b - a) * f + 0.5);
  };
  return (ch(16) << 16) | (ch(8) << 8) | ch(0);
}

/** The same colour as [r, g, b], 0-255 each (for canvas and rgba()). */
export function tempRgb(celsius: number | null | undefined): [number, number, number] {
  const c = tempRgbInt(celsius);
  return [(c >> 16) & 0xff, (c >> 8) & 0xff, c & 0xff];
}

/** "#rrggbb", lowercase — the form the shared fixture uses. */
export function tempColor(celsius: number | null | undefined): string {
  return "#" + tempRgbInt(celsius).toString(16).padStart(6, "0");
}

/** The colour with an alpha, as a CSS rgba() string. */
export function tempColorAlpha(celsius: number | null | undefined, alpha: number): string {
  const [r, g, b] = tempRgb(celsius);
  return `rgba(${r},${g},${b},${alpha})`;
}
