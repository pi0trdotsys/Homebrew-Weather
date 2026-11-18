import { useEffect, useState } from "react";

const QUERY = "(prefers-reduced-motion: no-preference)";

/** The same answer read right now, for effects that must decide before the first paint. */
export function prefersMotion(): boolean {
  return typeof window !== "undefined" && typeof window.matchMedia === "function"
    ? window.matchMedia(QUERY).matches
    : false;
}

/**
 * True only where the user has not asked for reduced motion. False on the
 * server and until the first effect, so nothing animates by default — every
 * screen effect (power-on, rain, channel change) is gated on this.
 */
export function useMotionOk(): boolean {
  const [ok, setOk] = useState(false);
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return;
    const mq = window.matchMedia(QUERY);
    const update = () => setOk(mq.matches);
    update();
    mq.addEventListener("change", update);
    return () => mq.removeEventListener("change", update);
  }, []);
  return ok;
}
