import { useState } from "react";
import { geocode, type GeoResult } from "@/lib/weather-api";
import { sameCity, type Coords } from "@/lib/settings";
import { useI18n } from "@/lib/i18n";

/** City search box and GPS button, shared by the location bar and first launch (Welcome). */
export function CitySearch({
  onLocate,
  onPick,
  locating,
  locateLabel,
}: {
  onLocate: () => void;
  onPick: (r: GeoResult) => void;
  locating: boolean;
  locateLabel?: string;
}) {
  const { lang, t } = useI18n();
  const [q, setQ] = useState("");
  const [results, setResults] = useState<GeoResult[]>([]);
  const [busy, setBusy] = useState(false);

  const search = async () => {
    if (!q.trim()) return;
    setBusy(true);
    try {
      setResults(await geocode(q, lang));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <form
        className="flex items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          void search();
        }}
      >
        <span className="text-[color:var(--phosphor-dim)]">{">"}</span>
        <input
          value={q}
          onChange={(e) => setQ(e.target.value)}
          placeholder={t.searchPlaceholder}
          className="min-w-0 flex-1 bg-transparent outline-none placeholder:text-[color:var(--phosphor-dim)]"
        />
        <button
          type="submit"
          className="border border-[color:var(--phosphor-dim)] px-2 py-0.5 text-xs uppercase hover:border-[color:var(--phosphor)]"
        >
          {busy ? "…" : "grep"}
        </button>
        <button
          type="button"
          onClick={onLocate}
          disabled={locating}
          className="border border-[color:var(--phosphor)] px-2 py-0.5 text-xs uppercase tracking-widest hover:bg-[color:var(--phosphor)] hover:text-black disabled:opacity-50"
        >
          {locating ? t.locatingBtn : (locateLabel ?? t.locateBtn)}
        </button>
      </form>
      {results.length > 0 && (
        <ul className="mt-2 max-h-40 overflow-y-auto border border-[color:var(--phosphor-dim)]/40 text-sm">
          {results.map((r, i) => (
            <li key={`${r.latitude}-${r.longitude}-${i}`}>
              <button
                onClick={() => {
                  onPick(r);
                  setResults([]);
                  setQ("");
                }}
                className="block w-full px-2 py-1 text-left hover:bg-[color:var(--phosphor)] hover:text-black"
              >
                → {r.name}
                {r.admin1 ? `, ${r.admin1}` : ""}, {r.country}
                <span className="ml-2 text-xs text-[color:var(--phosphor-dim)]">
                  {r.latitude.toFixed(2)}, {r.longitude.toFixed(2)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </>
  );
}

export function LocationBar({
  location,
  onLocate,
  onPick,
  locating,
  cities,
  current,
  onSelectCity,
  onRemoveCity,
}: {
  location: string;
  onLocate: () => void;
  onPick: (r: GeoResult) => void;
  locating: boolean;
  cities: Coords[];
  current: Coords | null;
  onSelectCity: (c: Coords) => void;
  onRemoveCity: (c: Coords) => void;
}) {
  const { t } = useI18n();
  return (
    <div className="terminal-box p-3">
      <div className="mb-2 flex flex-wrap items-center gap-2 text-sm">
        <span className="text-[color:var(--phosphor-dim)]">location =</span>
        <span className="crt-glow">{location || "null"}</span>
      </div>
      {cities.length > 1 && (
        <div className="mb-2 flex flex-wrap items-center gap-1.5 text-xs">
          <span className="text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.savedCities}:
          </span>
          {cities.map((c) => {
            const active = current != null && sameCity(c, current);
            return (
              <span
                key={`${c.lat},${c.lon}`}
                className={
                  "flex items-center border " +
                  (active
                    ? "border-[color:var(--phosphor)] bg-[color:var(--phosphor)] text-black"
                    : "border-[color:var(--phosphor-dim)]/60")
                }
              >
                <button type="button" onClick={() => onSelectCity(c)} className="px-2 py-0.5">
                  {c.name.split(",")[0]}
                </button>
                {!active && (
                  <button
                    type="button"
                    onClick={() => onRemoveCity(c)}
                    aria-label={t.removeCity(c.name)}
                    title={t.removeCity(c.name)}
                    className="border-l border-[color:var(--phosphor-dim)]/60 px-1.5 py-0.5 text-[color:var(--phosphor-dim)] hover:text-[color:var(--crimson)]"
                  >
                    ×
                  </button>
                )}
              </span>
            );
          })}
        </div>
      )}
      <CitySearch onLocate={onLocate} onPick={onPick} locating={locating} />
    </div>
  );
}
