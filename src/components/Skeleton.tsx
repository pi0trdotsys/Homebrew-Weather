import { useI18n } from "@/lib/i18n";

const block = "skeleton-block";

/** A line of text: a dim block of the given width. */
const Line = ({ w, h = "h-3" }: { w: string; h?: string }) => (
  <div className={`${block} ${h}`} style={{ width: w }} />
);

/**
 * The dashboard while its first forecast is on the way: the real layout in dim
 * phosphor blocks — the hero, the 24-hour chart, the forecast rows — so the page
 * doesn't jump when the data lands, with one slow scanline sweeping down it.
 * Under reduced motion the blocks just sit there (the sweep is in the
 * no-preference block of styles.css).
 */
export function ForecastSkeleton() {
  const { t } = useI18n();
  return (
    <div
      role="status"
      aria-busy="true"
      className="skeleton-scan relative flex flex-col gap-4 overflow-hidden"
    >
      <span className="sr-only">{t.loadingForecast}</span>
      <div aria-hidden="true" className="flex flex-col gap-4">
        {/* hero */}
        <div className="terminal-box flex items-center gap-5 p-5">
          <div className={`${block} h-[88px] w-[88px] shrink-0`} />
          <div className="flex-1">
            <div className={`${block} h-[60px] w-[7.5rem] sm:h-[72px]`} />
            <div className="mt-3 flex flex-col gap-2">
              <Line w="min(16rem, 80%)" />
              <Line w="min(9rem, 50%)" h="h-2" />
            </div>
          </div>
        </div>

        {/* status line */}
        <Line w="min(20rem, 70%)" h="h-2" />

        {/* the 24-hour chart: a heading, the rain line, a plot of bars */}
        <div className="terminal-box p-4">
          <Line w="9rem" h="h-2" />
          <div className="mt-3">
            <Line w="min(14rem, 60%)" h="h-2" />
          </div>
          <div className="mt-3 flex h-[200px] items-end gap-[3%]">
            {[34, 40, 46, 52, 58, 62, 66, 64, 60, 54, 48, 42, 38, 36, 40, 46].map((h, i) => (
              <div key={i} className={`${block} flex-1`} style={{ height: `${h}%` }} />
            ))}
          </div>
        </div>

        {/* forecast rows and the terminal panel */}
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="terminal-box flex flex-col gap-3 p-4">
            <Line w="8rem" h="h-2" />
            {Array.from({ length: 7 }, (_, i) => (
              <div key={i} className="flex items-center gap-3">
                <Line w="2.5rem" />
                <div className={`${block} h-4 w-4`} />
                <Line w="min(12rem, 45%)" />
                <div className="flex-1" />
                <Line w="3rem" />
              </div>
            ))}
          </div>
          <div className="terminal-box hidden flex-col gap-3 p-4 lg:flex">
            <Line w="8rem" h="h-2" />
            {Array.from({ length: 8 }, (_, i) => (
              <Line key={i} w={`${55 + ((i * 17) % 40)}%`} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
