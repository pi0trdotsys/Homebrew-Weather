import { useEffect, useState } from "react";

import { useI18n } from "@/lib/i18n";
import { useWidgetStatus } from "@/hooks/useWidgetStatus";
import { requestPinWidget } from "@/lib/native";

const PIN_DISMISSED_KEY = "brew-wx:pin-dismissed";

/** "[ add widget ]" — asks the launcher to pin one, then says what to do next. */
export function AddWidgetButton({
  onRequested,
  className = "",
}: {
  onRequested?: () => void;
  className?: string;
}) {
  const { t } = useI18n();
  const [state, setState] = useState<"idle" | "asking" | "confirm" | "refused">("idle");

  const add = async () => {
    setState("asking");
    const requested = await requestPinWidget();
    setState(requested ? "confirm" : "refused");
    if (requested) onRequested?.();
  };

  return (
    <span className={"inline-flex flex-wrap items-center gap-2 " + className}>
      <button
        type="button"
        onClick={() => void add()}
        disabled={state === "asking"}
        className="border border-[color:var(--phosphor)] px-3 py-1.5 text-xs uppercase tracking-widest hover:bg-[color:var(--phosphor)] hover:text-black disabled:opacity-50"
      >
        {state === "asking" ? t.widgetAdding : t.widgetAdd}
      </button>
      {state === "confirm" && (
        <span
          role="status"
          className="text-[10px] uppercase tracking-widest text-[color:var(--amber)]"
        >
          {t.widgetConfirm}
        </span>
      )}
      {state === "refused" && (
        <span
          role="status"
          className="text-[10px] uppercase tracking-widest text-[color:var(--amber)]"
        >
          {t.widgetNotAdded}
        </span>
      )}
    </span>
  );
}

/**
 * A dismissible one-line nudge on the dashboard: shown only in the Android app,
 * when the launcher can pin a widget and none is placed yet. Dismissal is
 * remembered (localStorage), and a widget being added makes it go away by itself.
 */
export function AddWidgetPrompt() {
  const { t } = useI18n();
  const { status } = useWidgetStatus();
  const [dismissed, setDismissed] = useState(true); // hidden until storage has been read
  useEffect(() => {
    try {
      setDismissed(localStorage.getItem(PIN_DISMISSED_KEY) === "1");
    } catch {
      setDismissed(false);
    }
  }, []);

  if (dismissed || !status || !status.pinSupported || status.count > 0) return null;

  const dismiss = () => {
    setDismissed(true);
    try {
      localStorage.setItem(PIN_DISMISSED_KEY, "1");
    } catch {
      // ignore: it will simply show again next launch
    }
  };

  return (
    <div className="terminal-box flex flex-wrap items-center gap-x-3 gap-y-2 px-3 py-2 text-xs">
      <span className="text-[color:var(--phosphor-dim)]">{"// "}</span>
      <span className="min-w-0 flex-1">{t.widgetPrompt}</span>
      <AddWidgetButton />
      <button
        type="button"
        onClick={dismiss}
        aria-label={t.widgetDismiss}
        title={t.widgetDismiss}
        className="px-1.5 text-[color:var(--phosphor-dim)] hover:text-[color:var(--crimson)]"
      >
        ×
      </button>
    </div>
  );
}
