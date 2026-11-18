// The lay of the land around a place — a grid of elevations — for the city
// backdrop (CityBackdrop.tsx). Web twin of android/.../widget/Terrain.kt: the
// same 24 x 12 grid, the same sample points, the same 0.1 degree cache cell
// (testdata/parity/terrain.json pins the pure parts together).
//
// Where it comes from: Open-Meteo's elevation endpoint, no key, up to 100
// points a call, so 288 points are three calls — made once per 0.1 degree cell
// and kept for good in localStorage (a hill doesn't move). The free tier
// answers 429 when asked too fast, so the calls are spaced out, and a failed
// attempt is not retried for ten minutes (longer if the server says so).

export const COLS = 24;
export const ROWS = 12;

/** Width of the area the grid covers, km; the height follows the grid's 2:1 shape. */
export const SPAN_KM_X = 14;

/** The elevation endpoint takes at most this many points per call. */
const CHUNK = 100;
const CHUNK_GAP_MS = 400;

export const FAILURE_BACKOFF_MS = 10 * 60 * 1000;

/** Elevations in metres, row-major from the north-west corner: [rows] rows of [cols]. */
export type Grid = { cols: number; rows: number; elevation: number[] };

/** Highest minus lowest point, metres. */
export function relief(grid: Grid): number {
  if (grid.elevation.length === 0) return 0;
  let lo = Infinity;
  let hi = -Infinity;
  for (const e of grid.elevation) {
    if (e < lo) lo = e;
    if (e > hi) hi = e;
  }
  return hi - lo;
}

/**
 * `%.1f` the way Java's Formatter does it: it rounds the shortest decimal
 * representation of the double half-up, where toFixed() rounds its exact binary
 * value (so 0.35 is "0.4" in Java and "0.3" in toFixed). The cache cell must be
 * the same string on both sides.
 */
function format1(x: number): string {
  const negative = x < 0 || Object.is(x, -0);
  const abs = Math.abs(x);
  const plain = String(abs);
  if (!/^\d+(\.\d+)?$/.test(plain)) return (negative ? "-" : "") + abs.toFixed(1);
  const [intPart, fracPart = ""] = plain.split(".");
  let digits = intPart + (fracPart + "0").slice(0, 1); // integer and first decimal as one number
  if (fracPart.length > 1 && fracPart.charCodeAt(1) >= 53 /* '5' */) {
    // add one in the last place, carrying through the digit string
    const a = digits.split("");
    let i = a.length - 1;
    while (i >= 0) {
      if (a[i] === "9") {
        a[i] = "0";
        i--;
      } else {
        a[i] = String(Number(a[i]) + 1);
        break;
      }
    }
    digits = (i < 0 ? "1" : "") + a.join("");
  }
  return (negative ? "-" : "") + digits.slice(0, -1) + "." + digits.slice(-1);
}

/** "51.1_17.0": the 0.1 degree cell a place falls in. */
export function cellKey(lat: number, lon: number): string {
  return `${format1(lat)}_${format1(lon)}`;
}

/** The points to ask about, in the order [Grid] expects: north to south, west to east. */
export function samplePoints(lat: number, lon: number): Array<[number, number]> {
  const spanY = SPAN_KM_X / (COLS / ROWS);
  const dLat = spanY / 111;
  const dLon = SPAN_KM_X / (111 * Math.max(Math.cos((lat * Math.PI) / 180), 0.05));
  const out: Array<[number, number]> = [];
  for (let r = 0; r < ROWS; r++) {
    const la = lat + dLat / 2 - (dLat * r) / (ROWS - 1);
    for (let c = 0; c < COLS; c++) out.push([la, lon - dLon / 2 + (dLon * c) / (COLS - 1)]);
  }
  return out;
}

/** `{"elevation":[…]}` as metres, or null if it isn't that. */
export function parseElevations(json: string): number[] | null {
  try {
    const root: unknown = JSON.parse(json);
    if (root === null || typeof root !== "object" || Array.isArray(root)) return null;
    const arr = (root as { elevation?: unknown }).elevation;
    if (!Array.isArray(arr)) return null;
    const out: number[] = [];
    for (const v of arr) {
      // Like org.json's getDouble: a number, or a string that reads as one.
      const n =
        typeof v === "number" ? v : typeof v === "string" && v.trim() !== "" ? Number(v) : NaN;
      if (!Number.isFinite(n)) return null;
      out.push(n);
    }
    return out;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------------------
// Cache and fetch
// ---------------------------------------------------------------------------

const STORE_PREFIX = "hbw:terrain:";
const memory = new Map<string, Grid>();
const inFlight = new Map<string, Promise<Grid | null>>();

const storage = (): Storage | null => {
  try {
    return typeof localStorage === "undefined" ? null : localStorage;
  } catch {
    return null;
  }
};

/** The stored grid for a place, or null (never fetched, or the entry is damaged). */
export function cachedGrid(lat: number, lon: number): Grid | null {
  const key = cellKey(lat, lon);
  const hit = memory.get(key);
  if (hit) return hit;
  try {
    const raw = storage()?.getItem(`${STORE_PREFIX}t_${key}`);
    if (!raw) return null;
    const o = JSON.parse(raw) as { c?: unknown; r?: unknown; e?: unknown };
    if (
      typeof o.c !== "number" ||
      typeof o.r !== "number" ||
      !Array.isArray(o.e) ||
      o.e.length !== o.c * o.r ||
      !o.e.every((v) => typeof v === "number" && Number.isFinite(v))
    )
      return null;
    const grid: Grid = { cols: o.c, rows: o.r, elevation: o.e as number[] };
    memory.set(key, grid);
    return grid;
  } catch {
    return null;
  }
}

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

class RateLimited extends Error {
  constructor(readonly retryAfterMs: number) {
    super("HTTP 429");
  }
}

async function getJson(url: string): Promise<string> {
  const res = await fetch(url);
  if (res.status === 429) {
    const secs = Number(res.headers.get("retry-after"));
    throw new RateLimited(Number.isFinite(secs) && secs > 0 ? Math.min(secs, 3600) * 1000 : 0);
  }
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.text();
}

const elevationUrl = (chunk: Array<[number, number]>) =>
  "https://api.open-meteo.com/v1/elevation" +
  `?latitude=${chunk.map((p) => p[0].toFixed(5)).join(",")}` +
  `&longitude=${chunk.map((p) => p[1].toFixed(5)).join(",")}`;

/**
 * The grid around ([lat], [lon]): from the cache if it is there, otherwise
 * fetched in three spaced-out calls and stored. Null while a recent failure is
 * still being waited out, or when this attempt fails (which starts the wait —
 * ten minutes, or the server's Retry-After on a 429). Never throws, and one
 * place is never fetched twice at once.
 */
export function loadTerrain(lat: number, lon: number): Promise<Grid | null> {
  const key = cellKey(lat, lon);
  const cached = cachedGrid(lat, lon);
  if (cached) return Promise.resolve(cached);
  const failedKey = `${STORE_PREFIX}failed_${key}`;
  try {
    const until = Number(storage()?.getItem(failedKey) ?? 0);
    if (Date.now() < until) return Promise.resolve(null);
  } catch {
    // no storage: nothing to wait out
  }
  const running = inFlight.get(key);
  if (running) return running;

  const job = (async (): Promise<Grid | null> => {
    try {
      const points = samplePoints(lat, lon);
      const elevations: number[] = [];
      for (let i = 0; i < points.length; i += CHUNK) {
        if (i > 0) await sleep(CHUNK_GAP_MS);
        const got = parseElevations(await getJson(elevationUrl(points.slice(i, i + CHUNK))));
        if (!got) throw new Error("no elevations");
        elevations.push(...got);
      }
      if (elevations.length !== COLS * ROWS) throw new Error(`got ${elevations.length} points`);
      const grid: Grid = {
        cols: COLS,
        rows: ROWS,
        elevation: elevations.map((e) => Math.round(e)),
      };
      memory.set(key, grid);
      try {
        const s = storage();
        s?.setItem(
          `${STORE_PREFIX}t_${key}`,
          JSON.stringify({ c: grid.cols, r: grid.rows, e: grid.elevation }),
        );
        s?.removeItem(failedKey);
      } catch {
        // storage full or blocked: the grid still serves this session
      }
      return grid;
    } catch (e) {
      const wait = Math.max(FAILURE_BACKOFF_MS, e instanceof RateLimited ? e.retryAfterMs : 0);
      try {
        storage()?.setItem(failedKey, String(Date.now() + wait));
      } catch {
        // ignore
      }
      return null;
    } finally {
      inFlight.delete(key);
    }
  })();
  inFlight.set(key, job);
  return job;
}
