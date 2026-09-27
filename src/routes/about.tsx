import { createFileRoute, Link } from "@tanstack/react-router";
import { useI18n } from "@/lib/i18n";

export const Route = createFileRoute("/about")({
  head: () => ({
    meta: [
      { title: "about — Homebrew Weather" },
      {
        name: "description",
        content: "About Homebrew Weather: a pixel-art, terminal-themed weather app for developers.",
      },
      { property: "og:title", content: "about — Homebrew Weather" },
      {
        property: "og:description",
        content: "Pixel-art weather for devs. Powered by Open-Meteo.",
      },
    ],
  }),
  component: About,
});

function About() {
  const { t } = useI18n();
  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:px-6">
      <Link
        to="/"
        className="text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)] hover:text-[color:var(--phosphor)]"
      >
        ← cd ~/
      </Link>
      <h1 className="mt-4 font-display text-4xl crt-glow">$ man homebrew-weather</h1>
      <div className="terminal-box mt-6 space-y-4 p-5 text-sm leading-relaxed">
        <p>
          <span className="text-[color:var(--phosphor-dim)]">NAME</span>
          <br />
          {t.aboutName}
        </p>
        <p>
          <span className="text-[color:var(--phosphor-dim)]">SYNOPSIS</span>
          <br />
          {t.aboutSynopsis}
        </p>
        <p>
          <span className="text-[color:var(--phosphor-dim)]">DATA</span>
          <br />
          {t.aboutData1}{" "}
          <a
            href="https://open-meteo.com/"
            target="_blank"
            rel="noreferrer"
            className="underline hover:text-[color:var(--amber)]"
          >
            open-meteo.com
          </a>{" "}
          {t.aboutData2}
        </p>
        <p>
          <span className="text-[color:var(--phosphor-dim)]">BUGS</span>
          <br />
          {t.aboutBugs}
        </p>
        <p className="text-[color:var(--phosphor-dim)]">
          {"# EOF"}
          <span className="blink">_</span>
        </p>
      </div>
    </div>
  );
}
