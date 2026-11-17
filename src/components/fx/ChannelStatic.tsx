import { useEffect, useRef, useState } from "react";

import { useMotionOk } from "./useMotionOk";

const BURST_MS = 250;
const FRAME_MS = 40; // ~25 fps of noise
const SCALE = 4; // one noise pixel = 4 css px

/**
 * A short burst of CRT static over its (relatively positioned) parent when
 * [cityKey] changes to a different value — a channel change instead of a hard
 * cut. Silent on first load (no previous key) and on a plain refetch (same
 * key). Click-through, aria-hidden, and absent under reduced motion.
 */
export function ChannelStatic({ cityKey }: { cityKey: string | null }) {
  const motionOk = useMotionOk();
  const prev = useRef<string | null>(null);
  const [burst, setBurst] = useState(0);

  useEffect(() => {
    if (cityKey == null) return;
    if (motionOk && prev.current != null && prev.current !== cityKey) setBurst((n) => n + 1);
    prev.current = cityKey;
  }, [cityKey, motionOk]);

  if (burst === 0) return null;
  return <Burst key={burst} />;
}

function Burst() {
  const ref = useRef<HTMLCanvasElement>(null);
  const [done, setDone] = useState(false);

  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext("2d");
    const host = canvas?.parentElement;
    if (!canvas || !ctx || !host) return;

    // Size the noise to the parent, capped so a very tall page stays cheap.
    const w = Math.max(1, Math.min(240, Math.ceil(host.clientWidth / SCALE)));
    const h = Math.max(1, Math.min(600, Math.ceil(host.clientHeight / SCALE)));
    canvas.width = w;
    canvas.height = h;
    const img = ctx.createImageData(w, h);

    const start = performance.now();
    let last = -FRAME_MS;
    let raf = 0;
    const draw = (now: number) => {
      const t = now - start;
      if (t >= BURST_MS) {
        setDone(true);
        return;
      }
      if (t - last >= FRAME_MS) {
        last = t;
        const fade = 1 - t / BURST_MS;
        const d = img.data;
        for (let i = 0; i < d.length; i += 4) {
          const v = Math.random();
          // phosphor-tinted grey noise; sparse bright specks
          const lum = v > 0.985 ? 255 : Math.floor(v * 150);
          d[i] = lum * 0.45;
          d[i + 1] = lum;
          d[i + 2] = lum * 0.55;
          d[i + 3] = Math.floor(255 * (0.25 + 0.65 * fade));
        }
        // a couple of horizontal tears
        for (let k = 0; k < 2; k++) {
          const row = Math.floor(Math.random() * h);
          for (let x = 0; x < w; x++) {
            const i = (row * w + x) * 4;
            d[i] = 80;
            d[i + 1] = 255;
            d[i + 2] = 120;
            d[i + 3] = Math.floor(255 * 0.8 * fade);
          }
        }
        ctx.putImageData(img, 0, 0);
      }
      raf = requestAnimationFrame(draw);
    };
    raf = requestAnimationFrame(draw);
    return () => cancelAnimationFrame(raf);
  }, []);

  if (done) return null;
  return <canvas ref={ref} className="channel-static" aria-hidden="true" />;
}
