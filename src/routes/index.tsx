import { createFileRoute, Link } from "@tanstack/react-router";
import { useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState } from "react";

import { HeroPanel } from "@/components/HeroPanel";
import { StatusLine } from "@/components/StatusLine";
import { HourlyStrip } from "@/components/HourlyStrip";
import { DailyForecast } from "@/components/DailyForecast";
import { TerminalOutput } from "@/components/TerminalOutput";
import { LocationBar } from "@/components/LocationBar";
import { pickJoke } from "@/lib/dev-jokes";
import { pickSigma } from "@/lib/sigma-jokes";
import { wmoToKind } from "@/lib/wmo";
import { APP_VERSION } from "@/lib/version";
import { fetchWeather, reverseGeocode, type GeoResult } from "@/lib/weather-api";
import { loadWeatherCache, loadLastWeatherCache, saveWeatherCache } from "@/lib/weather-cache";
import { useOnlineStatus } from "@/hooks/useOnlineStatus";
import {
  loadRefreshInterval,
  saveRefreshInterval,
  loadCoords,
  saveCoords,
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
  const [coords, setCoords] = useState<Coords>(null);
  const [locating, setLocating] = useState(false);
  const [geoError, setGeoError] = useState<string | null>(null);
  const [booted, setBooted] = useState(false);

  useEffect(() => {
    loadCoords().then((c) => {
      if (c) setCoords(c);
      setBooted(true);
    });
  }, []);

  useEffect(() => {
    if (coords) saveCoords(coords);
  }, [coords]);

  // Tapping a home-screen widget while the app is already running: the native
  // side (MainActivity.onNewIntent) has written the widget's city to storage,
  // but this page read storage long ago, so it's told directly as well. A cold
  // start needs none of this — the city is already in storage when we boot.
  useEffect(() => {
    const onOpenCity = (e: Event) => {
      const c = (e as CustomEvent<SavedCoords>).detail;
      if (c && Number.isFinite(c.lat) && Number.isFinite(c.lon)) {
        setGeoError(null);
        setCoords(c);
      }
    };
    window.addEventListener("hbw:open-city", onOpenCity);
    return () => window.removeEventListener("hbw:open-city", onOpenCity);
  }, []);

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
        const name = await reverseGeocode(lat, lon);
        setCoords({ lat, lon, name });
        setLocating(false);
      },
      (err) => {
        setGeoError(err.message);
        setLocating(false);
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 5 * 60 * 1000 },
    );
  }, []);

  const pickCity = useCallback((r: GeoResult) => {
    // A city chosen by hand answers the geolocation error, so stop showing it.
    setGeoError(null);
    setCoords({
      lat: r.latitude,
      lon: r.longitude,
      name: `${r.name}, ${r.country}`,
    });
  }, []);

  // Auto-locate on first visit if nothing saved
  useEffect(() => {
    if (booted && !coords) locate();
  }, [booted, coords, locate]);

  const [interval, setInterval] = useState<RefreshInterval>(30);
  useEffect(() => {
    loadRefreshInterval().then(setInterval);
  }, []);
  useEffect(() => {
    saveRefreshInterval(interval);
  }, [interval]);

  const online = useOnlineStatus();

  const { data, isLoading, isFetching, error, refetch, dataUpdatedAt } = useQuery({
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

  const isStale = !!dataUpdatedAt && Date.now() - dataUpdatedAt > interval * 60 * 1000;
  const fromCache = !online && !!data;

  // One joke on the dashboard, stable per forecast refresh, in the app-wide
  // tone. There used to be two at once — a scrolling ticker in the footer and
  // a line in the terminal panel — competing with each other and the weather.
  const [tone, setTone] = useState<Tone>(DEFAULT_TONE);
  useEffect(() => {
    loadTone().then(setTone);
  }, []);
  const joke = useMemo(() => {
    if (!data) return "";
    const kind = wmoToKind(data.current.weather_code);
    const night = data.current.is_day === 0;
    if (tone === "clean") return pickJoke(kind, night);
    // Hourly seed, same as the widget footer, so the line doesn't reshuffle
    // on every re-render.
    return pickSigma(kind, night, Math.floor(Date.now() / 3_600_000));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data?.current.time, data?.current.weather_code, tone]);

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

      <LocationBar
        location={coords?.name ?? ""}
        onLocate={locate}
        onPick={pickCity}
        locating={locating}
      />

      {geoError && (
        <div className="terminal-box p-3 text-sm text-[color:var(--crimson)]">
          <span className="text-[color:var(--phosphor-dim)]">stderr:</span> {geoError}. Try
          [locate()] again or grep a city above.
        </div>
      )}

      {!coords && !geoError && !locating && (
        <div className="terminal-box p-4 text-sm">
          <p>{"// no coords in memory"}</p>
          <p className="text-[color:var(--phosphor-dim)]">{"> allow location or type a city_"}</p>
        </div>
      )}

      {locating && (
        <div className="terminal-box p-4 text-sm">
          <span className="blink">▓</span> polling GPS…
        </div>
      )}

      {isLoading && coords && (
        <div className="terminal-box p-4 text-sm">
          <span className="blink">▓</span> GET api.open-meteo.com …
        </div>
      )}

      {error && !data && (
        <div className="terminal-box p-4 text-sm text-[color:var(--crimson)]">
          fetch failed: {(error as Error).message}
        </div>
      )}

      {data && coords && (
        <>
          <HeroPanel data={data} />
          <StatusLine
            online={online}
            fromCache={fromCache}
            isStale={isStale}
            updatedAt={dataUpdatedAt}
            interval={interval}
            timezone={data.timezone}
          />
          <HourlyStrip data={data} />
          <div className="grid gap-4 lg:grid-cols-2">
            <DailyForecast data={data} />
            <TerminalOutput data={data} joke={joke} />
          </div>
        </>
      )}

      <div className="mt-auto">
        <p className="mt-2 border-t border-[color:var(--phosphor-dim)] pt-2 text-center text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          weather via open-meteo · no cookies · no tracking · brewed with ♥ in the terminal
        </p>
      </div>
    </div>
  );
}
