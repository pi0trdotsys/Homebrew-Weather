import { useCallback, useEffect, useState } from "react";

import { isNative, widgetStatus, type WidgetStatus } from "@/lib/native";

/**
 * Whether the launcher can pin a widget from the app and how many are placed.
 * `null` until known (and forever in a browser). Re-read when the app comes
 * back to the foreground — that is when a widget has just been added.
 */
export function useWidgetStatus(): { status: WidgetStatus | null; refresh: () => void } {
  const [status, setStatus] = useState<WidgetStatus | null>(null);
  const refresh = useCallback(() => {
    if (!isNative()) return;
    void widgetStatus().then(setStatus);
  }, []);
  useEffect(() => {
    refresh();
    const onVisible = () => {
      if (!document.hidden) refresh();
    };
    document.addEventListener("visibilitychange", onVisible);
    return () => document.removeEventListener("visibilitychange", onVisible);
  }, [refresh]);
  return { status, refresh };
}
