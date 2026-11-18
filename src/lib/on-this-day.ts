import type { Lang } from "./i18n";

// "On this day": a real event for today's date from Wikipedia, as the footer
// line. The native widget fetches the same sources and applies the identical
// pick rule (see [pickOnThisDay]) — keep the two in step.
//
// - Polish: the day's own article (pl.wikipedia.org "17 listopada") through the
//   parse API, taking only its two event lists ("Wydarzenia w Polsce" and
//   "Wydarzenia na świecie" — the page also lists births and deaths in the
//   same bullet format). The "selected on this day" feed has no Polish edition.
// - English: the Wikimedia "selected on this day" feed.

export type OnThisDayEvent = { year: string; text: string };

/** The widget footer's ceiling. Events whose line fits are preferred, so the
 * dashboard and the widget lean to the same short ones. */
export const SHORT_LINE_MAX = 50;

/** How long to leave Wikipedia alone after a failed or empty answer. */
export const FAILURE_BACKOFF_MS = 30 * 60 * 1000;

const API_USER_AGENT = "HomebrewWeather/0.6 (https://github.com/pi0trdotsys/Homebrew-Weather)";
const FETCH_TIMEOUT_MS = 12_000;

/** Polish month names in the genitive, as the day pages are titled ("17 listopada"). */
export const MONTHS_GENITIVE_PL = [
  "stycznia",
  "lutego",
  "marca",
  "kwietnia",
  "maja",
  "czerwca",
  "lipca",
  "sierpnia",
  "września",
  "października",
  "listopada",
  "grudnia",
];

const WANTED_SECTIONS = new Set(["Wydarzenia w Polsce", "Wydarzenia na świecie"]);

// ---------------------------------------------------------------- parsing

/** Wiki markup to plain text: links, bold/italic, refs, other tags, {{templates}}. */
export function cleanWikitext(raw: string): string {
  let s = raw;
  s = s.replace(/<!--[\s\S]*?-->/g, "");
  s = s.replace(/<ref\b[^>]*\/>/gi, "");
  s = s.replace(/<ref\b[^>]*>[\s\S]*?<\/ref>/gi, "");
  // Two templates carry text that the sentence needs: {{link-interwiki |Name |…}}
  // is the name of a person or place without a page of its own here, and
  // {{C|19}} is a temperature. Dropping them whole leaves "został premierem…"
  // and "rekord temperatury: .".
  s = s.replace(/\{\{\s*link-interwiki\s*\|\s*([^|{}]+?)\s*(?:\|[^{}]*)?\}\}/gi, "$1");
  s = s.replace(/\{\{\s*C\s*\|\s*([^|{}]+?)\s*\}\}/g, "$1°C");
  // Templates nest, so peel the innermost ones until none are left.
  for (let prev = ""; prev !== s; ) {
    prev = s;
    s = s.replace(/\{\{[^{}]*\}\}/g, "");
  }
  // Files and categories are not text.
  s = s.replace(/\[\[(?:File|Image|Plik|Grafika|Category|Kategoria):[^\]]*\]\]/gi, "");
  s = s.replace(/\[\[[^\]|]*\|([^\]]*)\]\]/g, "$1");
  s = s.replace(/\[\[([^\]]*)\]\]/g, "$1");
  // External links: [http://x label] -> label, [http://x] -> nothing.
  s = s.replace(/\[(?:https?:)?\/\/\S+\s+([^\]]+)\]/g, "$1");
  s = s.replace(/\[(?:https?:)?\/\/\S+\]/g, "");
  s = s.replace(/'{2,}/g, "");
  s = s.replace(/<[^>]+>/g, "");
  s = s
    .replace(/&nbsp;/g, " ")
    .replace(/&ndash;/g, "–")
    .replace(/&mdash;/g, "—")
    .replace(/&quot;/g, '"')
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&amp;/g, "&");
  return s.replace(/[\s\u00a0]+/g, " ").trim();
}

/**
 * The events in a Polish day article's wikitext, in page order: Poland's
 * first, then the world's. Bullets look like `* [[1892]] – text` (the year
 * is not always linked, and is 3-4 digits — OnThisDay.kt reads the same
 * grammar, so both pools are the same list). Sub-headings stay part of their
 * section; `[[1950]]:` headings over `**` sub-bullets are not events.
 */
export function parsePolishEvents(wikitext: string): OnThisDayEvent[] {
  const events: OnThisDayEvent[] = [];
  let inEvents = false;
  for (const raw of wikitext.replace(/\r\n/g, "\n").split("\n")) {
    const line = raw.trimEnd();
    const heading = line.match(/^==([^=].*?)==\s*$/);
    if (heading) {
      inEvents = WANTED_SECTIONS.has(cleanWikitext(heading[1]));
      continue;
    }
    if (!inEvents) continue;
    const bullet = line.match(/^\*\s*(?:\[\[)?(\d{3,4})(?:\]\])?\s*[–—-]\s*(.+)$/);
    if (!bullet) continue;
    const text = cleanWikitext(bullet[2]);
    if (text) events.push({ year: bullet[1], text });
  }
  return events;
}

/** The events in the English feed's answer, in feed order. */
export function parseEnglishEvents(json: unknown): OnThisDayEvent[] {
  const selected = (json as { selected?: unknown })?.selected;
  if (!Array.isArray(selected)) return [];
  const events: OnThisDayEvent[] = [];
  for (const e of selected as Array<{ year?: unknown; text?: unknown }>) {
    if (typeof e?.text !== "string" || (typeof e.year !== "number" && typeof e.year !== "string"))
      continue;
    const text = e.text.replace(/\u00a0/g, " ").trim();
    const year = String(e.year);
    if (text && year.trim()) events.push({ year, text });
  }
  return events;
}

// ------------------------------------------------------------ pick rule

/** `"<year> · <text>"` — the line the footer shows. */
export const eventLine = (e: OnThisDayEvent) => `${e.year} · ${e.text}`;

/** 1-based day number in the local calendar year (1 January = 1). */
export function dayOfYear(d: Date): number {
  return Math.round(
    (Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()) - Date.UTC(d.getFullYear(), 0, 0)) /
      86_400_000,
  );
}

/**
 * The widget's rule, exactly: the short events (line <= 50 characters) if there
 * are any, else all of them; then `pool[dayOfYear % pool.length]`. The
 * dashboard then shows the whole line, never truncated.
 */
export function pickOnThisDay(events: OnThisDayEvent[], day: number): OnThisDayEvent | null {
  if (events.length === 0) return null;
  const short = events.filter((e) => eventLine(e).length <= SHORT_LINE_MAX);
  const pool = short.length > 0 ? short : events;
  return pool[day % pool.length];
}

// ----------------------------------------------------- fetch and cache

const pad2 = (n: number) => String(n).padStart(2, "0");

export function onThisDayUrl(lang: Lang, date: Date): string {
  const d = date.getDate();
  const m = date.getMonth();
  if (lang === "pl") {
    const page = encodeURIComponent(`${d}_${MONTHS_GENITIVE_PL[m]}`);
    return `https://pl.wikipedia.org/w/api.php?action=parse&page=${page}&prop=wikitext%7Csections&format=json&formatversion=2&origin=*`;
  }
  return `https://api.wikimedia.org/feed/v1/wikipedia/en/onthisday/selected/${pad2(m + 1)}/${pad2(d)}`;
}

export async function fetchOnThisDay(lang: Lang, date: Date): Promise<OnThisDayEvent[]> {
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), FETCH_TIMEOUT_MS);
  try {
    const res = await fetch(onThisDayUrl(lang, date), {
      headers: { "Api-User-Agent": API_USER_AGENT },
      signal: ctrl.signal,
    });
    if (!res.ok) throw new Error(`on this day: HTTP ${res.status}`);
    const json = await res.json();
    if (lang === "pl") {
      const wikitext = (json as { parse?: { wikitext?: unknown } })?.parse?.wikitext;
      if (typeof wikitext !== "string") throw new Error("on this day: no wikitext");
      return parsePolishEvents(wikitext);
    }
    return parseEnglishEvents(json);
  } finally {
    clearTimeout(timer);
  }
}

const dayKey = (lang: Lang, date: Date) =>
  `${lang}:${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
const cacheKey = (lang: Lang, date: Date) => `brew-wx:otd:${dayKey(lang, date)}`;
const failKey = (lang: Lang, date: Date) => `brew-wx:otd-fail:${dayKey(lang, date)}`;

function readCache(lang: Lang, date: Date): OnThisDayEvent[] | null {
  try {
    const raw = localStorage.getItem(cacheKey(lang, date));
    if (!raw) return null;
    const list = JSON.parse(raw) as OnThisDayEvent[];
    return Array.isArray(list) &&
      list.length > 0 &&
      list.every((e) => typeof e?.year === "string" && typeof e?.text === "string")
      ? list
      : null;
  } catch {
    return null;
  }
}

function failedRecently(lang: Lang, date: Date, now: number): boolean {
  try {
    const at = Number(localStorage.getItem(failKey(lang, date)));
    return Number.isFinite(at) && at > 0 && now - at < FAILURE_BACKOFF_MS;
  } catch {
    return false;
  }
}

const inflight = new Map<string, Promise<OnThisDayEvent[] | null>>();

/**
 * Today's events for [lang], from the cache if it has them; otherwise fetched
 * once and cached per language and date. A failure or an empty answer is
 * remembered for 30 minutes so the network isn't hammered, and gives null —
 * the caller falls back to something else. Never throws.
 */
export function loadOnThisDay(
  lang: Lang,
  date: Date = new Date(),
): Promise<OnThisDayEvent[] | null> {
  const cached = readCache(lang, date);
  if (cached) return Promise.resolve(cached);
  const now = Date.now();
  if (failedRecently(lang, date, now)) return Promise.resolve(null);

  const key = dayKey(lang, date);
  const pending = inflight.get(key);
  if (pending) return pending;

  const job = fetchOnThisDay(lang, date)
    .then((events) => {
      if (events.length === 0) throw new Error("on this day: empty");
      try {
        localStorage.setItem(cacheKey(lang, date), JSON.stringify(events));
        localStorage.removeItem(failKey(lang, date));
      } catch {
        // cache is an optimisation only
      }
      return events;
    })
    .catch(() => {
      try {
        localStorage.setItem(failKey(lang, date), String(Date.now()));
      } catch {
        // ignore
      }
      return null;
    })
    .finally(() => inflight.delete(key));
  inflight.set(key, job);
  return job;
}
