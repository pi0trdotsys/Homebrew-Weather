import { useCallback, useEffect, useState } from "react";
import { Preferences } from "@capacitor/preferences";

import { useKonamiCode } from "@/hooks/useKonamiCode";
import { pickLegendary } from "@/lib/legendary-jokes";

const UNLOCK_KEY = "easter-egg:sigma-unlocked";

export function EasterEgg() {
  const [unlocked, setUnlocked] = useState(false);
  const [flashing, setFlashing] = useState(false);
  const [message, setMessage] = useState("");

  useEffect(() => {
    Preferences.get({ key: UNLOCK_KEY }).then(({ value }) => {
      if (value === "true") setUnlocked(true);
    });
  }, []);

  const trigger = useCallback(() => {
    setUnlocked(true);
    setMessage(pickLegendary(Date.now()));
    setFlashing(true);
    Preferences.set({ key: UNLOCK_KEY, value: "true" });
    window.setTimeout(() => setFlashing(false), 3600);
  }, []);

  useKonamiCode(trigger);

  return (
    <>
      {flashing && (
        <div className="konami-flash fixed inset-0 z-[100] flex items-center justify-center px-4">
          <div className="terminal-box max-w-md border-[color:var(--amber)] bg-black/90 p-5 text-center">
            <div className="font-display text-2xl crt-glow text-[color:var(--amber)]">
              &gt; sudo access granted_
            </div>
            <p className="mt-3 text-sm text-[color:var(--phosphor)]">{message}</p>
          </div>
        </div>
      )}
      {unlocked && !flashing && (
        <div
          className="fixed bottom-2 right-2 z-40 select-none text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)] egg-badge"
          title="you know the code"
        >
          [root]<span className="blink">_</span>
        </div>
      )}
    </>
  );
}
