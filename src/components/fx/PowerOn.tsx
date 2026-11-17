import { useEffect, useState } from "react";

import { useMotionOk } from "./useMotionOk";

const FLAG = "brew-wx:powered-on";
const DURATION_MS = 720;

// sessionStorage can be unavailable; the flag then lives for this page load.
let playedInMemory = false;

function alreadyPlayed(): boolean {
  if (playedInMemory) return true;
  try {
    return sessionStorage.getItem(FLAG) === "1";
  } catch {
    return false;
  }
}

function markPlayed() {
  playedInMemory = true;
  try {
    sessionStorage.setItem(FLAG, "1");
  } catch {
    // ignore
  }
}

/**
 * The CRT powering on, once per app launch: black, a bright line that opens to
 * the full screen, then a phosphor scanline sweeping top to bottom. It is a
 * fixed, click-through overlay (nothing waits for it and nothing moves under
 * it) that removes itself after ~700 ms. Not rendered at all under
 * `prefers-reduced-motion: reduce`.
 */
export function PowerOn() {
  const motionOk = useMotionOk();
  const [playing, setPlaying] = useState(false);

  useEffect(() => {
    if (!motionOk || alreadyPlayed()) return;
    markPlayed();
    setPlaying(true);
  }, [motionOk]);

  // Its own effect so a StrictMode re-run of the one above can't leave the
  // overlay without a timer.
  useEffect(() => {
    if (!playing) return;
    const id = window.setTimeout(() => setPlaying(false), DURATION_MS);
    return () => window.clearTimeout(id);
  }, [playing]);

  if (!playing) return null;
  return (
    <div className="crt-poweron" aria-hidden="true">
      <div className="crt-poweron__half crt-poweron__half--top" />
      <div className="crt-poweron__half crt-poweron__half--bottom" />
      <div className="crt-poweron__line" />
      <div className="crt-poweron__sweep" />
    </div>
  );
}
