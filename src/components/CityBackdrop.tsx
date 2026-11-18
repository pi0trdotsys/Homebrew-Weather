import { useEffect, useRef, useState } from "react";
import {
  backdropKind,
  contourPlan,
  CONTOUR_ALPHA,
  QUIET,
  SKYLINE_ALPHA,
  skylinePlan,
} from "@/lib/backdrop";
import { cachedGrid, cellKey, loadTerrain, type Grid } from "@/lib/terrain";

/** "#33ff66" or "#3f6" as [r, g, b]; the phosphor green if it isn't a hex colour. */
function parseHex(value: string): [number, number, number] {
  const m = /^#([0-9a-f]{3}|[0-9a-f]{6})$/i.exec(value.trim());
  if (!m) return [51, 255, 102];
  const h = m[1].length === 3 ? m[1].replace(/./g, (c) => c + c) : m[1];
  return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
}

/** Waits this long after mounting before asking for terrain: the forecast is on screen first. */
const TERRAIN_DELAY_MS = 1200;

/**
 * The hero panel's "city map": faint contour lines of the terrain around the
 * city where it has relief, a pixel skyline made from the city's name where the
 * land is flat — and while the terrain is still being fetched or can't be.
 * The picture is the same one the home-screen widget draws (lib/backdrop.ts,
 * terrain.ts, contours.ts, skyline.ts mirror the Kotlin code).
 *
 * A canvas behind the hero's content (place it in a relatively positioned,
 * `isolate`d parent; it sits under the rain). Static: painted once, and again
 * only when the city, the size or the contrast setting changes. Theme-coloured
 * and quiet, quieter still under high contrast. aria-hidden and out of the
 * layout, so nothing moves when it appears.
 */
export function CityBackdrop({ name, lat, lon }: { name: string; lat: number; lon: number }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [fetched, setFetched] = useState<{ key: string; grid: Grid } | null>(null);
  const key = cellKey(lat, lon);

  // The terrain, once the forecast has had a moment on screen. A stored grid
  // needs no wait. A failure draws the skyline and is not retried for ten minutes.
  useEffect(() => {
    let cancelled = false;
    const stored = cachedGrid(lat, lon);
    if (stored) {
      setFetched({ key, grid: stored });
      return;
    }
    const timer = window.setTimeout(() => {
      void loadTerrain(lat, lon).then((grid) => {
        if (!cancelled && grid) setFetched({ key, grid });
      });
    }, TERRAIN_DELAY_MS);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [lat, lon, key]);

  // Only this city's grid: another city's must never draw under the new name.
  const grid = fetched && fetched.key === key ? fetched.grid : null;

  useEffect(() => {
    const canvas = canvasRef.current;
    const host = canvas?.parentElement;
    const ctx = canvas?.getContext("2d");
    if (!canvas || !host || !ctx) return;

    let raf = 0;
    const paint = () => {
      raf = 0;
      const w = host.clientWidth;
      const h = host.clientHeight;
      if (w < 8 || h < 8) return;
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);

      const style = getComputedStyle(canvas);
      const [r, g, b] = parseHex(style.getPropertyValue("--phosphor"));
      const [ar, ag, ab] = parseHex(style.getPropertyValue("--amber") || "#ffb000");
      const damp = document.documentElement.dataset.contrast === "high" ? QUIET : 1;

      if (backdropKind(grid) === "contours" && grid) {
        ctx.lineCap = "round";
        for (const level of contourPlan(grid, w, h)) {
          ctx.strokeStyle = `rgba(${r},${g},${b},${(level.major ? CONTOUR_ALPHA.major : CONTOUR_ALPHA.minor) * damp})`;
          ctx.lineWidth = level.major ? 1.3 : 0.8;
          ctx.beginPath();
          for (const [x1, y1, x2, y2] of level.lines) {
            ctx.moveTo(x1, y1);
            ctx.lineTo(x2, y2);
          }
          ctx.stroke();
        }
      } else {
        const { unit, buildings } = skylinePlan(name, w, h);
        ctx.fillStyle = `rgba(${r},${g},${b},${SKYLINE_ALPHA.mass * damp})`;
        for (const bd of buildings)
          ctx.fillRect(bd.x * unit, h - bd.height * unit, bd.width * unit, bd.height * unit);
        ctx.fillStyle = `rgba(${ar},${ag},${ab},${SKYLINE_ALPHA.windows * damp})`;
        for (const bd of buildings)
          for (const [wx, wy] of bd.windows)
            ctx.fillRect((bd.x + wx) * unit, h - bd.height * unit + wy * unit, unit, unit);
      }
    };
    const schedule = () => {
      if (raf === 0) raf = requestAnimationFrame(paint);
    };

    paint();
    const resize = new ResizeObserver(schedule);
    resize.observe(host);
    // High contrast is a data attribute on <html>; the picture dims with it.
    const attrs = new MutationObserver(schedule);
    attrs.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ["data-contrast"],
    });
    return () => {
      if (raf !== 0) cancelAnimationFrame(raf);
      resize.disconnect();
      attrs.disconnect();
    };
  }, [name, grid]);

  return <canvas ref={canvasRef} className="city-backdrop" aria-hidden="true" />;
}
