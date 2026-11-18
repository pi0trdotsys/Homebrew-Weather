// A pixel skyline made from nothing but the city's name. Web twin of
// android/.../widget/Skyline.kt: the same name builds the same buildings on the
// dashboard as on the widget, which testdata/parity/skyline.json pins down.
//
// That is why the random source is Mulberry32 and not Math.random() or a
// library: a few lines that behave the same in every language. The seed is
// Kotlin's String.hashCode() of the lowercased name (31 * h + each UTF-16 code
// unit, wrapped to 32 bits).

export type Building = {
  x: number;
  width: number;
  height: number;
  /** Lit windows as [x, y] offsets inside the building. */
  windows: Array<[number, number]>;
};

/** Kotlin/Java `String.hashCode()`: 31-multiplier over UTF-16 code units, signed 32-bit. */
export function javaHash(s: string): number {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (Math.imul(h, 31) + s.charCodeAt(i)) | 0;
  return h;
}

/** The seed for a city name: the hash of its lowercase form. */
export const skylineSeed = (name: string): number => javaHash(name.toLowerCase());

/** Mulberry32, as in Skyline.kt's `Mulberry32` class. */
export class Mulberry32 {
  private state: number;

  constructor(seed: number) {
    this.state = seed | 0;
  }

  /** The next value in [0, 1). */
  nextDouble(): number {
    this.state = (this.state + 0x6d2b79f5) | 0;
    let t = this.state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  }

  /** An integer in [from, until). */
  nextInt(from: number, until: number): number {
    return from + Math.floor(this.nextDouble() * (until - from));
  }

  nextBoolean(): boolean {
    return this.nextDouble() < 0.5;
  }
}

/**
 * Buildings filling [widthUnits] from the left, none taller than [maxHeightUnits].
 * A third are towers; the rest sit low, which gives the line a skyline's uneven
 * rhythm rather than a comb. Same sequence of random draws as the Kotlin code.
 */
export function buildings(seed: string, widthUnits: number, maxHeightUnits: number): Building[] {
  if (widthUnits <= 0 || maxHeightUnits < 4) return [];
  const rnd = new Mulberry32(skylineSeed(seed));
  const out: Building[] = [];
  let x = 0;
  while (x < widthUnits) {
    const width = Math.min(rnd.nextInt(4, 10), widthUnits - x);
    const tower = rnd.nextDouble() < 0.35;
    const low = Math.max(3, Math.floor(maxHeightUnits / 3));
    const height = tower ? rnd.nextInt(low + 2, maxHeightUnits + 1) : rnd.nextInt(3, low + 1);
    const windows: Array<[number, number]> = [];
    for (let wx = 1; wx < width - 1; wx += 2) {
      for (let wy = 2; wy < height - 1; wy += 3) {
        if (rnd.nextDouble() < 0.4) windows.push([wx, wy]);
      }
    }
    out.push({ x, width, height: Math.min(height, maxHeightUnits), windows });
    x += width + (rnd.nextBoolean() ? 0 : 1);
  }
  return out;
}
