import { useEffect, useRef } from "react";

import { useMotionOk } from "./useMotionOk";

export type PrecipitationKind = "rain" | "snow";

type Particle = {
  x: number;
  y: number;
  vx: number;
  vy: number;
  size: number;
  alpha: number;
  phase: number;
};

// Pixel-art weather behind the hero: small square particles in the app's cyan
// (snow: whiter, larger and slower). Each tuple is [min, max].
const PROFILE = {
  rain: {
    rgb: "85,255,255",
    size: [2, 2] as [number, number],
    vy: [280, 440] as [number, number],
    vx: -36,
    alpha: [0.25, 0.6] as [number, number],
    areaPerParticle: 2500,
  },
  snow: {
    rgb: "232,255,255",
    size: [3, 4] as [number, number],
    vy: [26, 56] as [number, number],
    vx: 0,
    alpha: [0.35, 0.75] as [number, number],
    areaPerParticle: 3500,
  },
} as const;

const MAX_PARTICLES = 60;
const MIN_FRAME_MS = 1000 / 30; // ~30 fps

const rand = (a: number, b: number) => a + Math.random() * (b - a);

/**
 * Rain or snow falling across its (relatively positioned) parent, drawn on a
 * canvas that sits behind the content (place it with `-z-10` in an `isolate`d
 * parent). Capped at ~30 fps and 60 particles, scaled to the area, and
 * devicePixelRatio-aware. It stops drawing while the page is hidden or the
 * parent is off-screen, and releases everything on unmount. Not rendered
 * under reduced motion.
 */
export function Precipitation({ kind }: { kind: PrecipitationKind | null }) {
  const motionOk = useMotionOk();
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    const host = canvas?.parentElement;
    const ctx = canvas?.getContext("2d");
    if (!kind || !motionOk || !canvas || !host || !ctx) return;
    const profile = PROFILE[kind];

    let width = 0;
    let height = 0;
    const particles: Particle[] = [];

    const spawn = (anywhere: boolean): Particle => ({
      x: rand(0, width + 40),
      y: anywhere ? rand(0, height) : rand(-12, 0),
      vx: profile.vx * rand(0.7, 1.3),
      vy: rand(...profile.vy),
      size: Math.round(rand(...profile.size)),
      alpha: rand(...profile.alpha),
      phase: rand(0, Math.PI * 2),
    });

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      width = host.clientWidth;
      height = host.clientHeight;
      canvas.width = Math.max(1, Math.round(width * dpr));
      canvas.height = Math.max(1, Math.round(height * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      const target = Math.min(
        MAX_PARTICLES,
        Math.max(8, Math.round((width * height) / profile.areaPerParticle)),
      );
      while (particles.length < target) particles.push(spawn(true));
      particles.length = Math.min(particles.length, target);
    };

    let raf = 0;
    let last = 0;
    let onScreen = true;

    const frame = (now: number) => {
      raf = requestAnimationFrame(frame);
      const dt = now - last;
      if (dt < MIN_FRAME_MS) return;
      last = now;
      const s = Math.min(dt, 100) / 1000;
      ctx.clearRect(0, 0, width, height);
      for (const p of particles) {
        p.y += p.vy * s;
        p.x += p.vx * s + (kind === "snow" ? Math.sin(now / 900 + p.phase) * 14 * s : 0);
        if (p.y > height || p.x < -6 || p.x > width + 6) Object.assign(p, spawn(false));
        ctx.fillStyle = `rgba(${profile.rgb},${p.alpha})`;
        // integer css pixels keep the squares crisp
        ctx.fillRect(Math.round(p.x), Math.round(p.y), p.size, p.size);
      }
    };

    const update = () => {
      const run = onScreen && !document.hidden;
      if (run && raf === 0) {
        last = 0;
        raf = requestAnimationFrame(frame);
      } else if (!run && raf !== 0) {
        cancelAnimationFrame(raf);
        raf = 0;
      }
    };

    resize();
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(host);
    const intersection = new IntersectionObserver((entries) => {
      onScreen = entries[entries.length - 1]?.isIntersecting ?? true;
      update();
    });
    intersection.observe(host);
    document.addEventListener("visibilitychange", update);
    update();

    return () => {
      if (raf !== 0) cancelAnimationFrame(raf);
      raf = 0;
      resizeObserver.disconnect();
      intersection.disconnect();
      document.removeEventListener("visibilitychange", update);
      ctx.clearRect(0, 0, canvas.width, canvas.height);
    };
  }, [kind, motionOk]);

  if (!kind || !motionOk) return null;
  return <canvas ref={canvasRef} className="precipitation" aria-hidden="true" />;
}
