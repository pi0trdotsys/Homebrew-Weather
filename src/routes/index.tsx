import { createFileRoute, Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState } from "react";

import { HeroPanel } from "@/components/HeroPanel";
import { StatusLine } from "@/components/StatusLine";
import { HourlyStrip } from "@/components/HourlyStrip";
import { DailyForecast } from "@/components/DailyForecast";
import { DayDetail } from "@/components/DayDetail";
import { TerminalOutput } from "@/components/TerminalOutput";
import { LocationBar } from "@/components/LocationBar";
import { Welcome } from "@/components/Welcome";
import { pickFooterJoke } from "@/lib/jokes.generated";
import { wmoToKind } from "@/lib/wmo";
import { APP_VERSION } from "@/lib/version";
import { useI18n } from "@/lib/i18n";
import { rebaseWeather } from "@/lib/rebase";
import { fetchWeather, reverseGeocode, type GeoResult } from "@/lib/weather-api";
import { loadWeatherCache, loadLastWeatherCache, saveWeatherCache } from "@/lib/weather-cache";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";
import {
  loadRefreshInterval,
  saveRefreshInterval,
  loadCoords,
  saveCoords,
  loadCities,
  saveCities,
  withCity,
  sameCity,
  takeFocusDay,
  loadTone,
  DEFAULT_TONE,
  type Tone,
  type RefreshInterval,
  type Coords as SavedCoords,
} from "@/lib/settings";

type Coords = SavedCoords | null;

export const Route = createFileRoute("/")({
  head: () => ({
    meta: [
      {
        property: "og:image",
        content:
          "https://id-preview--6313c827-2a95-4616-9590-077cbd7ceb49.lovable.app/og-cover.jpg",
      },
      {
        name: "twitter:image",
        content:
          "https://id-preview--6313c827-2a95-4616-9590-077cbd7ceb49.lovable.app/og-cover.jpg",
      },
    ],
  }),
  component: Index,
});

function Index() {
  const { lang, t } = useI18n();
  const [coords, setCoords] = useState<Coords>(null);
  const [cities, setCities] = useState<SavedCoords[]>([]);
  const [focusDay, setFocusDay] = useState<string | null>(null);
  const [locating, setLocating] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);
  const [booted, setBooted] = useState(false);

  useEffect(() => {
    Promise.all([loadCoords(), loadCities(), takeFocusDay()]).then(([c, list, day]) => {
      setCities(list);
      if (c) setCoords(c);
      // Opened by tapping a day on the widget (cold start).
      if (day) setFocusDay(day);
      setBooted(true);
    });
  }, []);

  // The current city is saved as "the" location (the widget's "use last app
  // location" reads it) and moved to the front of the saved list.
  useEffect(() => {
    if (!coords || !booted) return;
    saveCoords(coords);
    setCities((list) => {
      const next = withCity(list, coords);
      void saveCities(next);
      return next;
    });
  }, [coords, booted]);

  // Tapping a home-screen widget while the app is already running: the native
  // side (MainActivity.onNewIntent) has written the widget's city to storage,
  // but this page read storage long ago, so it's told directly as well — with
  // the day, if a day column was tapped. A cold start needs none of this.
  useEffect(() => {
    const onOpenCity = (e: Event) => {
      const c = (e as CustomEvent<SavedCoords & { day?: string }>).detail;
      if (c && Number.isFinite(c.lat) && Number.isFinite(c.lon)) {
        setGeoError(null);
        setCoords({ lat: c.lat, lon: c.lon, name: c.name });
        setFocusDay(c.day ?? null);
      }
    };
    window.addEventListener("hbw:open-city", onOpenCity);
    return () => window.removeEventListener("hbw:open-city", onOpenCity);
  }, []);

  // Only on request — see Welcome for why the app no longer locates by itself
  // on first launch.
  const locate = useCallback(() => {
    if (!("geolocation" in navigator)) {
      setGeoError("navigator.geolocation === undefined");
      return;
    }
    setLocating(true);
    setGeoError(null);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const lat = pos.coords.latitude;
        const lon = pos.coords.longitude;
        const name = await reverseGeocode(lat, lon, lang);
        setCoords({ lat, lon, name });
        setFocusDay(null);
        setLocating(false);
      },
      (err) => {
        setGeoError(err.message);
        setLocating(false);
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 5 * 60 * 1000 },
    );
  }, [lang]);

  const pickCity = useCallback((r: GeoResult) => {
    // A city chosen by hand answers the geolocation error, so stop showing it.
    setGeoError(null);
    setFocusDay(null);
    setCoords({
      lat: r.latitude,
      lon: r.longitude,
      name: `${r.name}, ${r.country}`,
    });
  }, []);

  const selectCity = useCallback((c: SavedCoords) => {
    setGeoError(null);
    setFocusDay(null);
    setCoords(c);
  }, []);

  const removeCity = useCallback((c: SavedCoords) => {
    setCities((list) => {
      const next = list.filter((x) => !sameCity(x, c));
      void saveCities(next);
      return next;
    });
  }, []);

  const [interval, setInterval] = useState<RefreshInterval>(30);
  useEffect(() => {
    loadRefreshInterval().then(setInterval);
  }, []);
  useEffect(() => {
    saveRefreshInterval(interval);
  }, [interval]);

  const online = useOnlineStatus();

  const { data, isLoading, error, refetch, dataUpdatedAt } = useQuery({
    enabled: !!coords,
    queryKey: ["weather", coords?.lat, coords?.lon],
    queryFn: async () => {
      if (!navigator.onLine) {
        const cached = loadWeatherCache(coords!.lat, coords!.lon);
        if (cached) return cached.data;
        throw new Error("offline & no cached snapshot available");
      }
      try {
        const fresh = await fetchWeather(coords!.lat, coords!.lon);
        saveWeatherCache({
          data: fresh,
          updatedAt: Date.now(),
          location: coords!.name,
          lat: coords!.lat,
          lon: coords!.lon,
        });
        return fresh;
      } catch (e) {
        const cached = loadWeatherCache(coords!.lat, coords!.lon);
        if (cached) return cached.data;
        throw e;
      }
    },
    initialData: () => {
      if (!coords) return undefined;
      return loadWeatherCache(coords.lat, coords.lon)?.data ?? loadLastWeatherCache()?.data;
    },
    initialDataUpdatedAt: () => {
      if (!coords) return undefined;
      return (
        loadWeatherCache(coords.lat, coords.lon)?.updatedAt ?? loadLastWeatherCache()?.updatedAt
      );
    },
    staleTime: interval * 60 * 1000,
    refetchInterval: online ? interval * 60 * 1000 : false,
    refetchIntervalInBackground: true,
    refetchOnWindowFocus: online,
    refetchOnReconnect: true,
    retry: online ? 2 : 0,
  });

  useEffect(() => {
    if (online && coords) refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [online]);

  // A saved forecast is read against the real clock (see rebaseWeather), so
  // an offline morning doesn't show last night as "now". Re-read each minute.
  const [nowTick, setNowTick] = useState(() => Date.now());
  useEffect(() => {
    const id = window.setInterval(() => setNowTick(Date.now()), 60_000);
    return () => window.clearInterval(id);
  }, []);
  const view = useMemo(() => (data ? rebaseWeather(data, nowTick) : undefined), [data, nowTick]);

  const isStale = !!dataUpdatedAt && Date.now() - dataUpdatedAt > interval * 60 * 1000;
  const fromCache = !online && !!data;

  // One joke on the dashboard, stable per hour, in the app-wide tone and
  // language — the same pools and pick rule as the widget footer.
  const [tone, setTone] = useState<Tone>(DEFAULT_TONE);
  useEffect(() => {
    loadTone().then(setTone);
  }, []);
  const joke = useMemo(() => {
    if (!view) return "";
    const kind = wmoToKind(view.current.weather_code);
    const night = view.current.is_day === 0;
    return pickFooterJoke(tone, lang, kind, night, Math.floor(Date.now() / 3_600_000));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [view?.current.time, view?.current.weather_code, tone, lang]);

  // A tapped day that the forecast no longer covers (stale widget) is dropped.
  const shownDay = focusDay && view?.daily.time.includes(focusDay) ? focusDay : null;

  return (
    <div className="mx-auto flex min-h-screen max-w-5xl flex-col gap-4 px-4 pb-4 pt-6 sm:px-6">
      {/* Title bar */}
      <header className="flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="font-display text-3xl crt-glow glow-breathe sm:text-4xl">
          $ homebrew-weather
          <span className="blink">_</span>
        </h1>
        <nav className="flex items-center gap-4 text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          <span>v{APP_VERSION}</span>
          <Link to="/settings" className="hover:text-[color:var(--phosphor)]">
            ./settings
          </Link>
          <Link to="/about" className="hover:text-[color:var(--phosphor)]">
            ./about
          </Link>
          {/* A development tool (widget mockups against design tokens), not a
              user-facing page — linked only in dev builds. The route itself
              still resolves if typed in. */}
          {import.meta.env.DEV && (
            <Link to="/mockups" className="hover:text-[color:var(--phosphor)]">
              ./mockups
            </Link>
          )}
        </nav>
      </header>

      {booted && !coords && !locating ? (
        <Welcome onLocate={locate} onPick={pickCity} locating={locating} />
      ) : (
        <LocationBar
          location={coords?.name ?? ""}
          onLocate={locate}
          onPick={pickCity}
          locating={locating}
          cities={cities}
          current={coords}
          onSelectCity={selectCity}
          onRemoveCity={removeCity}
        />
      )}

      {geoError && (
        <div className="terminal-box p-3 text-sm text-[color:var(--crimson)]">
          <span className="text-[color:var(--phosphor-dim)]">stderr:</span> {t.geoError(geoError)}
        </div>
      )}

      {locating && (
        <div className="terminal-box p-4 text-sm">
          <span className="blink">▓</span> {t.locating}
        </div>
      )}

      {isLoading && coords && (
        <div className="terminal-box p-4 text-sm">
          <span className="blink">▓</span> GET api.open-meteo.com …
        </div>
      )}

      {error && !data && (
        <div className="terminal-box p-4 text-sm text-[color:var(--crimson)]">
          {t.fetchFailed((error as Error).message)}
        </div>
      )}

      {view && coords && (
        <>
          <HeroPanel data={view} />
          <StatusLine
            online={online}
            fromCache={fromCache}
            isStale={isStale}
            updatedAt={dataUpdatedAt}
            interval={interval}
            timezone={view.timezone}
          />
          {shownDay && <DayDetail data={view} date={shownDay} onClose={() => setFocusDay(null)} />}
          <HourlyStrip data={view} />
          <div className="grid gap-4 lg:grid-cols-2">
            <DailyForecast data={view} focusDay={shownDay} onFocusDay={setFocusDay} />
            <TerminalOutput data={view} joke={joke} />
          </div>
        </>
      )}

      <div className="mt-auto">
        <p className="mt-2 border-t border-[color:var(--phosphor-dim)] pt-2 text-center text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          {t.footer}
        </p>
      </div>
    </div>
  );
}
