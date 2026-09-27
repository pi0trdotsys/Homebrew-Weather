import type { GeoResult } from "@/lib/weather-api";
import { useI18n } from "@/lib/i18n";
import { CitySearch } from "./LocationBar";

/**
 * First launch: pick a place, one of two ways.
 *
 * The app used to start locating on its own the moment it opened with no
 * saved city — so the very first thing a new user saw was the system asking
 * for their location, before the app had said a word about why. Now the
 * permission prompt appears only if they choose "use my location"; typing a
 * city never asks for anything.
 */
export function Welcome({
  onLocate,
  onPick,
  locating,
}: {
  onLocate: () => void;
  onPick: (r: GeoResult) => void;
  locating: boolean;
}) {
  const { t } = useI18n();
  return (
    <div className="terminal-box space-y-3 p-5">
      <p className="font-display text-2xl crt-glow">{t.welcomeTitle}</p>
      <p className="text-sm text-[color:var(--phosphor-dim)]">{t.welcomeBody}</p>
      <CitySearch
        onLocate={onLocate}
        onPick={onPick}
        locating={locating}
        locateLabel={t.useMyLocation}
      />
    </div>
  );
}
