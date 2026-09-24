/**
 * Connection and sync status, as one quiet line.
 *
 * This used to be a full `$ cron -l` panel sitting in the best spot on the
 * page, right beside the current weather — debug information given the same
 * weight as the forecast. It's status, not weather: it only deserves
 * attention when something is wrong, so it's small and dim unless the app is
 * offline or the data is stale.
 */
export function StatusLine({
  online,
  fromCache,
  isStale,
  updatedAt,
  interval,
  timezone,
}: {
  online: boolean;
  fromCache: boolean;
  isStale: boolean;
  updatedAt: number;
  interval: number;
  timezone: string;
}) {
  const problem = !online || isStale;
  return (
    <p
      className={
        "flex flex-wrap items-center gap-x-3 gap-y-1 px-1 text-[11px] uppercase tracking-widest " +
        (problem ? "text-[color:var(--amber)]" : "text-[color:var(--phosphor-dim)]")
      }
    >
      <span className={online ? "" : "text-[color:var(--crimson)]"}>
        ● {online ? "online" : "offline"}
        {fromCache && " · serving cache"}
        {isStale && online && " · stale, refreshing"}
      </span>
      <span>sync {new Date(updatedAt).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}</span>
      <span>every {interval} min</span>
      <span>open-meteo</span>
      <span>{timezone}</span>
    </p>
  );
}
