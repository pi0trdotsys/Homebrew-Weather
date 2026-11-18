import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { prefersMotion } from "@/components/fx/useMotionOk";

/** Slows down towards the end: 0 -> 1. */
export const easeOutCubic = (t: number): number => 1 - Math.pow(1 - Math.min(1, Math.max(0, t)), 3);

/** The whole number [elapsed] ms into a count from [from] to [to] lasting [duration] ms. */
export function countValue(from: number, to: number, elapsed: number, duration: number): number {
  if (duration <= 0 || elapsed >= duration) return to;
  return Math.round(from + (to - from) * easeOutCubic(elapsed / duration));
}

/**
 * The number last on screen, kept across the hero unmounting while another
 * city's forecast loads: the next city counts on from it instead of from zero.
 */
let lastShown: number | null = null;

/** Forgets the remembered number (for tests). */
export function resetCountUp(): void {
  lastShown = null;
}

const useIsoLayoutEffect = typeof window === "undefined" ? useEffect : useLayoutEffect;

/**
 * [target] counted up to over [duration] ms, but only when it matters: the
 * first time it appears (from 0, or from the number last shown) and when
 * [cityKey] changes (from the previous city's number). A new [target] under the
 * same key — a refetch, the minute tick — just takes effect, and with reduced
 * motion every change does.
 *
 * Returns the value to show now (exact [target] at rest) and the number the
 * count started from, so the caller can reserve room for the wider of the two.
 */
export function useCountUp(
  target: number,
  cityKey: string,
  duration = 600,
): { value: number; from: number } {
  const [state, setState] = useState({ value: target, from: target });
  const key = useRef<string | null>(null);
  const shown = useRef(target);
  const frame = useRef(0);

  useIsoLayoutEffect(() => {
    cancelAnimationFrame(frame.current);
    const before = key.current;
    const first = before === null;
    const changed = first || before !== cityKey;
    key.current = cityKey;

    const from = first ? (lastShown ?? 0) : shown.current;
    if (!changed || !prefersMotion() || Math.round(from) === Math.round(target)) {
      shown.current = target;
      lastShown = target;
      setState((s) => ({ value: target, from: changed ? target : s.from }));
      return;
    }

    setState({ value: from, from });
    shown.current = from;
    let finished = false;
    const t0 = performance.now();
    const tick = (now: number) => {
      const elapsed = now - t0;
      if (elapsed >= duration) {
        finished = true;
        shown.current = target;
        lastShown = target;
        setState((s) => ({ value: target, from: s.from }));
        return;
      }
      const v = countValue(from, target, elapsed, duration);
      shown.current = v;
      lastShown = v;
      setState((s) => ({ value: v, from: s.from }));
      frame.current = requestAnimationFrame(tick);
    };
    frame.current = requestAnimationFrame(tick);
    // Frames don't run in a hidden page; the end value still has to arrive.
    const failsafe = window.setTimeout(() => {
      if (!finished) tick(performance.now() + duration);
    }, duration + 200);
    return () => {
      cancelAnimationFrame(frame.current);
      window.clearTimeout(failsafe);
      // Interrupted before it ended (React's dev double-run, or a new value
      // arriving): let the next run see this city as not yet counted.
      if (!finished) key.current = before;
    };
  }, [target, cityKey, duration]);

  return state;
}
