// Contour lines (isolines) of a scalar field by marching squares — the strokes
// of the terrain backdrop. Web twin of android/.../widget/Contours.kt, with the
// same case table and the same saddle rule (testdata/parity/contours.json).
//
// A field is a row-major array of w x h samples; a line is a list of short
// segments in sample coordinates, which the caller scales to pixels. The Kotlin
// side computes in 32-bit floats, this one in doubles; the parity cases
// compare with a tolerance for that.

export type Segment = { x1: number; y1: number; x2: number; y2: number };

export type Field = { data: number[]; w: number; h: number };

/**
 * [field] enlarged [factor] times in each direction by bilinear interpolation,
 * so a coarse 24 x 12 grid contours into flowing curves instead of facets.
 */
export function upsample(field: ArrayLike<number>, w: number, h: number, factor: number): Field {
  const nw = (w - 1) * factor + 1;
  const nh = (h - 1) * factor + 1;
  const out = new Array<number>(nw * nh);
  for (let y = 0; y < nh; y++) {
    const fy = y / factor;
    const y0 = Math.min(Math.trunc(fy), h - 2);
    const ty = fy - y0;
    for (let x = 0; x < nw; x++) {
      const fx = x / factor;
      const x0 = Math.min(Math.trunc(fx), w - 2);
      const tx = fx - x0;
      const a = field[y0 * w + x0];
      const b = field[y0 * w + x0 + 1];
      const c = field[(y0 + 1) * w + x0];
      const d = field[(y0 + 1) * w + x0 + 1];
      out[y * nw + x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty;
    }
  }
  return { data: out, w: nw, h: nh };
}

/** A 3x3 box blur, to take the edge off a noisy surface model before contouring. */
export function smooth(field: ArrayLike<number>, w: number, h: number): number[] {
  const out = new Array<number>(w * h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      let sum = 0;
      let n = 0;
      for (let dy = -1; dy <= 1; dy++) {
        for (let dx = -1; dx <= 1; dx++) {
          const xx = x + dx;
          const yy = y + dy;
          if (xx >= 0 && xx < w && yy >= 0 && yy < h) {
            sum += field[yy * w + xx];
            n++;
          }
        }
      }
      out[y * w + x] = sum / n;
    }
  }
  return out;
}

/** Where between two corner values the [level] sits, 0..1. */
const frac = (a: number, b: number, level: number): number =>
  a === b ? 0.5 : Math.min(1, Math.max(0, (level - a) / (b - a)));

/**
 * Segments of the line where the field crosses [level]. Per cell, the case is
 * which of the four corners are at or above it; edges are numbered 0 top,
 * 1 right, 2 bottom, 3 left, and each case joins the edges it crosses. The two
 * ambiguous "saddle" cases are decided by the cell's average.
 */
export function segments(field: ArrayLike<number>, w: number, h: number, level: number): Segment[] {
  const out: Segment[] = [];
  for (let y = 0; y < h - 1; y++) {
    for (let x = 0; x < w - 1; x++) {
      const tl = field[y * w + x];
      const tr = field[y * w + x + 1];
      const br = field[(y + 1) * w + x + 1];
      const bl = field[(y + 1) * w + x];
      const c =
        (tl >= level ? 8 : 0) |
        (tr >= level ? 4 : 0) |
        (br >= level ? 2 : 0) |
        (bl >= level ? 1 : 0);
      if (c === 0 || c === 15) continue;

      const edge = (e: number): [number, number] => {
        switch (e) {
          case 0:
            return [x + frac(tl, tr, level), y];
          case 1:
            return [x + 1, y + frac(tr, br, level)];
          case 2:
            return [x + frac(bl, br, level), y + 1];
          default:
            return [x, y + frac(tl, bl, level)];
        }
      };
      let pairs: Array<[number, number]>;
      switch (c) {
        case 1:
        case 14:
          pairs = [[3, 2]];
          break;
        case 2:
        case 13:
          pairs = [[2, 1]];
          break;
        case 3:
        case 12:
          pairs = [[3, 1]];
          break;
        case 4:
        case 11:
          pairs = [[0, 1]];
          break;
        case 6:
        case 9:
          pairs = [[0, 2]];
          break;
        case 7:
        case 8:
          pairs = [[3, 0]];
          break;
        case 5:
          pairs =
            (tl + tr + br + bl) / 4 >= level
              ? [
                  [3, 0],
                  [2, 1],
                ]
              : [
                  [3, 2],
                  [0, 1],
                ];
          break;
        default: // 10
          pairs =
            (tl + tr + br + bl) / 4 >= level
              ? [
                  [3, 2],
                  [0, 1],
                ]
              : [
                  [3, 0],
                  [2, 1],
                ];
      }
      for (const [a, b] of pairs) {
        const p = edge(a);
        const q = edge(b);
        out.push({ x1: p[0], y1: p[1], x2: q[0], y2: q[1] });
      }
    }
  }
  return out;
}

/**
 * About ten lines across [relief] metres, at a step a map would use: the first
 * of 5, 10, 20, 25, 50, 100, 200 m that gives no more than ten.
 */
export function niceStep(relief: number): number {
  return [5, 10, 20, 25, 50, 100, 200].find((s) => relief / s <= 10) ?? 500;
}
