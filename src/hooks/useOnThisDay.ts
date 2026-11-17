import { useEffect, useState } from "react";

import type { Lang } from "@/lib/i18n";
import { dayOfYear, eventLine, loadOnThisDay, pickOnThisDay } from "@/lib/on-this-day";

/**
 * Today's "on this day" line (`"1892 · …"`, whole, never truncated), or null
 * while it is loading, when [enabled] is false, or when Wikipedia could not
 * be reached — the caller shows its fallback in all those cases, so nothing
 * waits on the network.
 */
export function useOnThisDayLine(enabled: boolean, lang: Lang): string | null {
  const [line, setLine] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled) {
      setLine(null);
      return;
    }
    let cancelled = false;
    const now = new Date();
    void loadOnThisDay(lang, now).then((events) => {
      if (cancelled) return;
      const event = events ? pickOnThisDay(events, dayOfYear(now)) : null;
      setLine(event ? eventLine(event) : null);
    });
    return () => {
      cancelled = true;
    };
  }, [enabled, lang]);

  return line;
}
