// High-contrast display option (settings:high-contrast, see settings.ts).
//
// It is applied as `data-contrast="high"` on <html>; styles.css does the rest.
// The choice lives in Capacitor Preferences, which on Android is not readable
// synchronously — so a copy is mirrored into localStorage, and that is what is
// read before the first paint to avoid a flash of the dim palette. The mirror
// holds "high", "normal", or nothing at all (never chosen: the system's own
// "more contrast" preference then decides).

export const CONTRAST_MIRROR_KEY = "brew-wx:contrast";

const prefersMore = () =>
  typeof window !== "undefined" &&
  typeof window.matchMedia === "function" &&
  window.matchMedia("(prefers-contrast: more)").matches;

/** The mode in force for a stored choice (`null` = never chosen). */
export const resolveContrast = (choice: boolean | null): "high" | "normal" =>
  (choice ?? prefersMore()) ? "high" : "normal";

function mirror(choice: boolean | null) {
  try {
    if (choice == null) localStorage.removeItem(CONTRAST_MIRROR_KEY);
    else localStorage.setItem(CONTRAST_MIRROR_KEY, choice ? "high" : "normal");
  } catch {
    // storage unavailable: the choice still applies for this session
  }
}

let current: boolean | null = null;

/** Applies a choice to the page now, and remembers it for the next first paint. */
export function applyContrast(choice: boolean | null): void {
  current = choice;
  if (typeof document === "undefined") return;
  document.documentElement.dataset.contrast = resolveContrast(choice);
  mirror(choice);
}

/** Applies whatever the mirror says, synchronously — call as early as possible. */
export function applyStoredContrast(): void {
  if (typeof document === "undefined") return;
  let stored: string | null = null;
  try {
    stored = localStorage.getItem(CONTRAST_MIRROR_KEY);
  } catch {
    // ignore
  }
  current = stored === "high" ? true : stored === "normal" ? false : null;
  document.documentElement.dataset.contrast = resolveContrast(current);
}

/** Follows the system preference while the user has not chosen. Returns an unsubscribe. */
export function watchSystemContrast(): () => void {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return () => {};
  const mq = window.matchMedia("(prefers-contrast: more)");
  const on = () => {
    if (current == null) document.documentElement.dataset.contrast = resolveContrast(null);
  };
  mq.addEventListener("change", on);
  return () => mq.removeEventListener("change", on);
}

/** Inline <head> script for the server-rendered page: same rule as applyStoredContrast. */
export const CONTRAST_BOOT_SCRIPT = `(function(){try{var m=localStorage.getItem(${JSON.stringify(
  CONTRAST_MIRROR_KEY,
)});var h=m==="high"||(m!=="normal"&&window.matchMedia&&matchMedia("(prefers-contrast: more)").matches);document.documentElement.dataset.contrast=h?"high":"normal"}catch(e){}})();`;
