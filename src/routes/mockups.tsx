import { createFileRoute, Link } from "@tanstack/react-router";
import { useState } from "react";
import { WidgetMock4x2 } from "@/components/mockups/WidgetMock4x2";
import { WidgetStates } from "@/components/mockups/WidgetStates";
import {
  METRICS,
  PREVIEW,
  ROWS,
  STATE_VARIANTS,
  TRANSPARENCY_LEVELS,
  TYPE_SCALE,
  WIDGET_COLORS,
  dp,
  fitReport,
  type WidgetState,
} from "@/lib/widget-tokens";

export const Route = createFileRoute("/mockups")({
  head: () => ({
    meta: [
      { title: "widget mockups — Homebrew Weather" },
      {
        name: "description",
        content:
          "Terminal 2.0 4×2 widget mockups: state gallery, transparency, zoom and the full design-token map.",
      },
    ],
  }),
  component: Mockups,
});

const STATE_KEYS = Object.keys(STATE_VARIANTS) as WidgetState[];
const ZOOMS = [0.5, 0.75, 1, 1.25, 1.5];

function ButtonGroup<T extends string>({
  options,
  value,
  onChange,
}: {
  options: { id: T; label: string }[];
  value: T;
  onChange: (v: T) => void;
}) {
  return (
    <div className="flex flex-wrap gap-1">
      {options.map((o) => {
        const active = o.id === value;
        return (
          <button
            key={o.id}
            type="button"
            onClick={() => onChange(o.id)}
            className={
              "border px-2 py-1 text-xs uppercase tracking-widest " +
              (active
                ? "border-[color:var(--phosphor)] bg-[color:var(--phosphor)] text-black"
                : "border-[color:var(--phosphor-dim)] text-[color:var(--phosphor-dim)] hover:border-[color:var(--phosphor)] hover:text-[color:var(--phosphor)]")
            }
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

function Mockups() {
  const [state, setState] = useState<WidgetState>("ok");
  const [alphaId, setAlphaId] = useState<string>("opaque");
  const [zoom, setZoom] = useState<number>(1);
  const alpha = TRANSPARENCY_LEVELS.find((t) => t.id === alphaId)?.alpha ?? 1;

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6">
      <Link
        to="/"
        className="text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)] hover:text-[color:var(--phosphor)]"
      >
        ← cd ~/
      </Link>
      <h1 className="mt-4 font-display text-4xl crt-glow">
        $ open widget-mockups<span className="blink">_</span>
      </h1>
      <p className="mt-2 max-w-3xl text-sm text-[color:var(--phosphor-dim)]">
        {
          "// Terminal 2.0 · hiper future tech HUD · makieta natywnego widgetu 4×2. Wszystko poniżej mapuje się 1:1 na RemoteViews (patrz docs/widget-spec.md)."
        }
      </p>

      <div className="terminal-box mt-6 space-y-4 p-5 text-sm">
        <div className="grid gap-4 md:grid-cols-3">
          <div>
            <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
              STATE
            </div>
            <ButtonGroup
              options={STATE_KEYS.map((s) => ({ id: s, label: s }))}
              value={state}
              onChange={setState}
            />
          </div>
          <div>
            <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
              BACKGROUND
            </div>
            <ButtonGroup
              options={TRANSPARENCY_LEVELS.map((t) => ({ id: t.id, label: t.label }))}
              value={alphaId}
              onChange={setAlphaId}
            />
          </div>
          <div>
            <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
              ZOOM
            </div>
            <ButtonGroup
              options={ZOOMS.map((z) => ({ id: String(z), label: `${z}×` }))}
              value={String(zoom)}
              onChange={(v) => setZoom(Number(v))}
            />
          </div>
        </div>
      </div>

      {/* live preview over a "home screen" wallpaper so transparency reads clearly */}
      <div className="terminal-box mt-6 p-5">
        <div className="mb-2 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          $ cat /sdcard/Wallpaper → preview
        </div>
        <div
          className="flex items-center justify-center overflow-auto p-6"
          style={{
            background:
              "repeating-conic-gradient(#0d1322 0% 25%, #121a2e 0% 50%) 0 0 / 28px 28px, radial-gradient(ellipse at 50% 0%, rgba(85,255,255,0.12), transparent 60%)",
          }}
        >
          <WidgetMock4x2 state={state} bgAlpha={alpha} zoom={zoom} />
        </div>
      </div>

      <div className="mt-8">
        <h2 className="font-display text-2xl crt-glow">$ states --all</h2>
        <div className="mt-4">
          <WidgetStates zoom={0.75} />
        </div>
      </div>

      <div className="mt-8">
        <h2 className="font-display text-2xl crt-glow">$ tokens --dump</h2>
        <div className="mt-4 grid gap-4 lg:grid-cols-3">
          <ColorTable />
          <TypeScaleTable />
          <MetricsTable />
        </div>
      </div>
    </div>
  );
}

function ColorTable() {
  const rows = Object.entries(WIDGET_COLORS);
  return (
    <div className="terminal-box p-4 text-sm">
      <div className="mb-3 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
        colors
      </div>
      <div className="space-y-1">
        {rows.map(([k, hex]) => (
          <div key={k} className="flex items-center gap-2">
            <span
              className="inline-block h-4 w-4 shrink-0 border border-white/10"
              style={{ background: hex }}
            />
            <code className="truncate text-xs">{k}</code>
            <span className="ml-auto text-[10px] text-[color:var(--phosphor-dim)]">{hex}</span>
            <span className="text-[10px] text-[color:var(--phosphor-dim)]">
              {k.startsWith("hud") ? "mockup" : "native"}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function TypeScaleTable() {
  const rows = Object.entries(TYPE_SCALE);
  return (
    <div className="terminal-box p-4 text-sm">
      <div className="mb-3 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
        type scale (px @{METRICS.scale}×)
      </div>
      <div className="space-y-1">
        {rows.map(([k, px]) => (
          <div key={k} className="flex items-center gap-2">
            <span className="text-xs" style={{ fontSize: px }}>
              Aa
            </span>
            <code className="truncate text-xs">{k}</code>
            <span className="ml-auto text-[10px] text-[color:var(--phosphor-dim)]">
              {px}px ≈ {(px / METRICS.scale).toFixed(1)}sp
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function MetricsTable() {
  const fit = fitReport();
  return (
    <div className="terminal-box p-4 text-sm">
      <div className="mb-3 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
        metrics 4×2 · fit check
      </div>
      <div className="space-y-1 text-xs">
        <p>
          cells <span className="text-[color:var(--phosphor-dim)]">=</span>{" "}
          {METRICS.cells.join("×")}
        </p>
        <p>
          dp <span className="text-[color:var(--phosphor-dim)]">=</span> {METRICS.dp.width}×
          {METRICS.dp.height}
        </p>
        <p>
          preview <span className="text-[color:var(--phosphor-dim)]">=</span> {PREVIEW.width}×
          {PREVIEW.height}px @{METRICS.scale}×
        </p>
        <p>
          padding <span className="text-[color:var(--phosphor-dim)]">=</span> {METRICS.padDp}dp ·
          radius {METRICS.radiusDp}dp · bracket {METRICS.cornerDp}dp
        </p>
      </div>

      <div className="mt-3 space-y-1 text-xs">
        {fit.rows.map((r) => (
          <div key={r.name} className="flex justify-between">
            <code>{r.name}</code>
            <span className="text-[color:var(--phosphor-dim)]">{r.dp}dp</span>
          </div>
        ))}
        <div className="mt-2 flex justify-between border-t border-[color:var(--phosphor-dim)]/40 pt-2">
          <code>used / available</code>
          <span style={{ color: fit.fits ? "var(--hud-cyan)" : "#ff5555" }}>
            {fit.usedDp}dp / {fit.contentHeightDp}dp ({fit.slackDp >= 0 ? "+" : ""}
            {fit.slackDp}dp)
          </span>
        </div>
        <div className="flex justify-between">
          <code>grid column rows</code>
          <span style={{ color: fit.gridFits ? "var(--hud-cyan)" : "#ff5555" }}>
            {fit.gridUsedDp}dp / {ROWS.grid}dp
          </span>
        </div>
        <div className="flex justify-between">
          <code>verdict</code>
          <span style={{ color: fit.fits && fit.gridFits ? "var(--hud-cyan)" : "#ff5555" }}>
            {fit.fits && fit.gridFits ? "FITS 4×2" : "OVERFLOW"}
          </span>
        </div>
      </div>

      <p className="mt-3 text-[10px] leading-relaxed text-[color:var(--phosphor-dim)]">
        {
          "// budżet pionowy = 110dp − 2×padding. Każdy wiersz ma stałą wysokość dp, teksty są jednoliniowe z ellipsize — nic nie może wypchnąć layoutu. weather_widget_info.xml musi mieć targetCellHeight=2 / minHeight=110dp."
        }
      </p>
    </div>
  );
}

