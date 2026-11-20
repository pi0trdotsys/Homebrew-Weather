// How the footer picks its line: which pools it draws from and which line of them.
// Mirrors LinePicker.kt (the widget); testdata/parity/line-picker.json holds the
// cases both sides run.
//
// No repeats until the whole pool has been seen: every stretch of `size` hours is a fresh
// seeded shuffle of the pool, so each line is shown once before any comes back, in a
// different order each round, and the last line of one round is never the first of the next.
// (The old rule, pool[hour % size], brought a 12-line pool back every 12 hours, always at
// the same hours.)
//
// A joke is drawn from the weather's own pool plus whichever of these apply: after dark, a
// hot day, a freezing one, and the day of the week (Monday, Friday, the weekend).
import { Mulberry32 } from "./skyline";

export const HOT_FROM_C = 28;
export const COLD_UP_TO_C = 2;
export const KINDS = ["sun", "partly", "cloud", "fog", "rain", "snow", "thunder"];

function shuffled(cycle: number, n: number): number[] {
  const rng = new Mulberry32((Math.imul(cycle, 0x9e3779b1 | 0) + Math.imul(n, 0x85ebca6b | 0)) | 0);
  const a = Array.from({ length: n }, (_, i) => i);
  for (let i = n - 1; i >= 1; i--) {
    const j = rng.nextInt(0, i + 1);
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function permutation(cycle: number, n: number): number[] {
  const perm = shuffled(cycle, n);
  if (cycle > 0 && perm[0] === shuffled(cycle - 1, n)[n - 1])
    [perm[0], perm[1]] = [perm[1], perm[0]];
  return perm;
}

/** Index into a pool of [size] lines for [seed] (the hour). 0 for a pool of one line or none. */
export function lineIndex(seed: number, size: number): number {
  if (size <= 1) return 0;
  const s = Math.trunc(seed) | 0;
  const h = s === -2147483648 ? 0 : Math.abs(s);
  if (size === 2) return h % 2;
  const cycle = Math.floor(h / size);
  return permutation(cycle, size)[h % size];
}

/** Day of the week of an ISO date (or a longer timestamp): 0 Sunday .. 6 Saturday; null if it isn't one. */
export function dayOfWeek(isoDate: string | null | undefined): number | null {
  if (!isoDate || isoDate.length < 10) return null;
  const y = Number(isoDate.slice(0, 4));
  const m = Number(isoDate.slice(5, 7));
  const d = Number(isoDate.slice(8, 10));
  if (![y, m, d].every(Number.isInteger) || isoDate[4] !== "-" || isoDate[7] !== "-") return null;
  if (m < 1 || m > 12 || d < 1 || d > 31) return null;
  const t = [0, 3, 2, 5, 0, 3, 5, 1, 4, 6, 2, 4];
  const yy = m < 3 ? y - 1 : y;
  return (yy + Math.trunc(yy / 4) - Math.trunc(yy / 100) + Math.trunc(yy / 400) + t[m - 1] + d) % 7;
}

/**
 * The pools a joke is drawn from, in the order they are joined: night, the contextual ones
 * (hot, cold, the day of the week), then the weather kind's own. [temperature] may be null.
 */
export function jokeKeys(
  kind: string,
  isNight: boolean,
  temperature: number | null | undefined,
  dow: number | null | undefined,
): string[] {
  const keys: string[] = [];
  const t = temperature ?? NaN;
  if (isNight) keys.push("night");
  if (!Number.isNaN(t) && t >= HOT_FROM_C) keys.push("hot");
  if (!Number.isNaN(t) && t <= COLD_UP_TO_C) keys.push("cold");
  if (dow === 1) keys.push("monday");
  else if (dow === 5) keys.push("friday");
  else if (dow === 0 || dow === 6) keys.push("weekend");
  keys.push(KINDS.includes(kind) ? kind : "cloud");
  return keys;
}

/** The joke for [seed] from [pools] (a pool a language or tone does not have is left out). */
export function pickJoke(
  pools: Record<string, string[]>,
  kind: string,
  isNight: boolean,
  seed: number,
  temperature?: number | null,
  dow?: number | null,
): string {
  const pool = jokeKeys(kind, isNight, temperature, dow).flatMap((k) => pools[k] ?? []);
  const lines = pool.length ? pool : pools.cloud;
  return lines[lineIndex(seed, lines.length)];
}
