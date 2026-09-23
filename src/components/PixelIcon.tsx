import type { WeatherKind } from "@/lib/wmo";

// 16x16 pixel icons — generated from the Kotlin source of truth
// (widget/PixelIcons.kt); keep the two byte-identical. Uppercase is the lit
// tone of a material, lowercase its shaded tone: W/w cloud, D/d storm cloud,
// Y/o sun, B/b rain, G/g fog. "." is transparent.
const ICONS: Record<WeatherKind | "moon", string[]> = {
  sun: [
    "................",
    ".......YY.......",
    ".......YY.......",
    "..YY........YY..",
    "..YY........YY..",
    ".....YYYYYY.....",
    "....YYYYYYYY....",
    ".YY.YYYYYYYY.YY.",
    ".YY.YYYYYYYY.YY.",
    "....oooooooo....",
    ".....oooooo.....",
    "..YY........YY..",
    "..YY........YY..",
    ".......YY.......",
    ".......YY.......",
    "................",
  ],
  moon: [
    "................",
    "......WWWW......",
    "....WWWWWWW.....",
    "...WWWWww....C..",
    "..WWWWWw........",
    "..WWWWw.........",
    ".WWWWWw.........",
    ".WWWWWw.........",
    ".WWWWWw.....C...",
    ".WWWWWw.........",
    "..WWWWw.........",
    "..WWWWWw........",
    "...WWWWww.......",
    "....WWWWWWW.....",
    "......WWWW......",
    "................",
  ],
  partly: [
    "................",
    ".....YYYY.......",
    "..Y..YYYY..Y....",
    "...YYYYYYYY.....",
    "..YYYYYYYYYY....",
    "..YYYYY.WWWW....",
    "...YYY.WWWWWWW..",
    ".....WWWWWWWWWW.",
    "...WWWWWWWWWWWWW",
    "..WWWWWWWWWWWWWW",
    "..wwwwwwwwwwwwww",
    "...wwwwwwwwwwww.",
    "................",
    "................",
    "................",
    "................",
  ],
  cloud: [
    "................",
    "................",
    "................",
    ".......WWWW.....",
    ".....WWWWWWWW...",
    "....WWWWWWWWWW..",
    "..WWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWWW",
    ".WWWWWWWWWWWWWWW",
    ".WWWWWWWWWWWWWWW",
    ".wwwwwwwwwwwwwww",
    "..wwwwwwwwwwwww.",
    "................",
    "................",
    "................",
    "................",
  ],
  fog: [
    "................",
    "................",
    "..GGGGGGGGGG....",
    "....gggggggggg..",
    "................",
    ".GGGGGGGGGGGG...",
    "...gggggggggggg.",
    "................",
    "GGGGGGGGGGGGGG..",
    "..gggggggggggggg",
    "................",
    ".GGGGGGGGGGGG...",
    "...gggggggggg...",
    "................",
    "..GGGGGGGGGG....",
    "................",
  ],
  rain: [
    "................",
    ".......WWWW.....",
    ".....WWWWWWWW...",
    "....WWWWWWWWWW..",
    "..WWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWWW",
    ".WWWWWWWWWWWWWWW",
    ".wwwwwwwwwwwwwww",
    "..wwwwwwwwwwwww.",
    "..B...B...B...B.",
    "..B...B...B...B.",
    "................",
    "....b...b...b...",
    "....b...b...b...",
    "................",
    "................",
  ],
  snow: [
    "................",
    ".......WWWW.....",
    ".....WWWWWWWW...",
    "....WWWWWWWWWW..",
    "..WWWWWWWWWWWWW.",
    ".WWWWWWWWWWWWWWW",
    ".WWWWWWWWWWWWWWW",
    ".wwwwwwwwwwwwwww",
    "..wwwwwwwwwwwww.",
    "..S...S...S...S.",
    "................",
    "....S...S...S...",
    "................",
    "..S...S...S...S.",
    "................",
    "................",
  ],
  thunder: [
    "................",
    ".......DDDD.....",
    ".....DDDDDDDD...",
    "....DDDDDDDDDD..",
    "..DDDDDDDDDDDDD.",
    ".DDDDDDDDDDDDDDD",
    ".DDDDDDDDDDDDDDD",
    ".ddddddddddddddd",
    "..ddddddddddddd.",
    "........LLL.....",
    ".......LLL......",
    "......LLL.......",
    "....LLLLLLL.....",
    "......LLL.......",
    ".....LLL........",
    "....LL..........",
  ],
};

const COLORS: Record<string, string> = {
  Y: "#ffd23f", // sun, lit
  o: "#f2a81c", // sun, shaded underside
  W: "#e8eeea", // cloud, lit
  w: "#9fb0aa", // cloud, shaded underside
  D: "#6b7c84", // storm cloud, lit
  d: "#45545b", // storm cloud, shaded underside
  B: "#6fbaff", // rain, near streaks
  b: "#3d86cc", // rain, far streaks
  S: "#eaf6ff", // snow
  G: "#9aada4", // fog, near bars
  g: "#5d6f66", // fog, far bars
  L: "#ffb000", // lightning, struck (widget amber)
  l: "#7a5300", // lightning, between strikes
  C: "#55ffff", // stars (widget cyan)
  K: "#000000",
};

export function PixelIcon({
  kind,
  size = 128,
  className = "",
}: {
  kind: WeatherKind | "moon";
  size?: number;
  className?: string;
}) {
  const grid = ICONS[kind] ?? ICONS.cloud;
  const px = Math.floor(size / 16);
  return (
    <div
      key={kind}
      className={`pixel-materialize ${className}`}
      style={{
        display: "grid",
        gridTemplateColumns: `repeat(16, ${px}px)`,
        gridTemplateRows: `repeat(16, ${px}px)`,
        width: px * 16,
        height: px * 16,
        imageRendering: "pixelated",
        filter: "drop-shadow(0 0 6px rgba(51,255,102,0.15))",
      }}
      aria-hidden
    >
      {grid.flatMap((row, y) =>
        row.split("").map((ch, x) => (
          <div
            key={`${x}-${y}`}
            style={{
              width: px,
              height: px,
              background: ch === "." ? "transparent" : (COLORS[ch] ?? "#33ff66"),
            }}
          />
        )),
      )}
    </div>
  );
}
