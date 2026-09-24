import { useEffect, useState } from "react";
import type { WeatherResponse } from "@/lib/weather-api";
import { wmoToKind } from "@/lib/wmo";

const ASCII: Record<string, string[]> = {
  sun: [
    "     \\   |   /     ",
    "      .-\"\"\"-.      ",
    "   -- (  o  o ) -- ",
    "      \\  ---  /    ",
    "      /`-...-`\\    ",
    "     /   |   \\     ",
  ],
  partly: [
    "    \\  |  /   .--.  ",
    "    .-\"\"-.  .-(    ). ",
    "   ( o  o )(___.__)__)",
    "    `----'           ",
  ],
  cloud: [
    "        .--.        ",
    "     .-(    ).      ",
    "    (___.__)__)     ",
    "                    ",
  ],
  fog: [
    "  _ - _ - _ - _ - _ ",
    " _ - _ - _ - _ - _ -",
    "  _ - _ - _ - _ - _ ",
    " _ - _ - _ - _ - _ -",
  ],
  rain: [
    "        .--.        ",
    "     .-(    ).      ",
    "    (___.__)__)     ",
    "     ' ' ' ' '      ",
    "    ' ' ' ' '       ",
  ],
  snow: [
    "        .--.        ",
    "     .-(    ).      ",
    "    (___.__)__)     ",
    "     *  *  *  *     ",
    "    *  *  *  *      ",
  ],
  thunder: [
    "        .--.        ",
    "     .-(    ).      ",
    "    (___.__)__)     ",
    "       /_ZZZ        ",
    "        /           ",
  ],
};

/**
 * The full current-conditions readout, as a terminal session.
 *
 * It used to print location, condition and temperature too — the same values
 * the key/value panel beside it printed, both below a location bar that already
 * named the place. With the hero now carrying temperature and condition and the
 * location bar carrying the place, this is where *everything else* lives: the
 * secondary readings, always, in full, including surface pressure (which only
 * the removed table used to show). Nothing that was on the dashboard is gone;
 * each reading just appears once.
 *
 * [joke] comes from the caller so it follows the app-wide tone setting and
 * stays stable between re-renders.
 */
export function TerminalOutput({ data, joke }: { data: WeatherResponse; joke: string }) {
  const kind = wmoToKind(data.current.weather_code);
  const art = ASCII[kind] ?? ASCII.cloud;
  const c = data.current;

  const lines: string[] = [
    `user@homebrew-weather:~$ weather --details`,
    ``,
    ...art,
    ``,
    `feels      : ${c.apparent_temperature.toFixed(1)}°C`,
    `wind       : ${c.wind_speed_10m.toFixed(1)} km/h`,
    `humidity   : ${c.relative_humidity_2m}%`,
    `pressure   : ${Math.round(c.surface_pressure)} hPa`,
    `sunrise    : ${data.daily.sunrise[0].split("T")[1]?.slice(0, 5) ?? "--:--"}`,
    `sunset     : ${data.daily.sunset[0].split("T")[1]?.slice(0, 5) ?? "--:--"}`,
    ``,
    `# ${joke}`,
    `user@homebrew-weather:~$ `,
  ];

  const [shown, setShown] = useState(0);
  useEffect(() => {
    setShown(0);
    const id = setInterval(() => {
      setShown((s) => {
        if (s >= lines.length) {
          clearInterval(id);
          return s;
        }
        return s + 1;
      });
    }, 55);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data.current.time]);

  return (
    <div className="terminal-box p-4 font-mono text-[13px] leading-tight">
      <div className="mb-2 flex items-center gap-2 text-xs text-[color:var(--phosphor-dim)]">
        <span className="inline-block h-3 w-3 rounded-full bg-[color:var(--crimson)]" />
        <span className="inline-block h-3 w-3 rounded-full bg-[color:var(--amber)]" />
        <span className="inline-block h-3 w-3 rounded-full bg-[color:var(--phosphor)]" />
        <span className="ml-2">— bash — 80×24</span>
      </div>
      <pre className="whitespace-pre-wrap break-words text-[color:var(--phosphor)]">
        {lines.slice(0, shown).join("\n")}
        {shown >= lines.length && <span className="blink">_</span>}
      </pre>
    </div>
  );
}
