import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { I18nProvider } from "@/lib/i18n";
import {
  Outlet,
  Link,
  createRootRouteWithContext,
  useRouter,
  HeadContent,
  Scripts,
} from "@tanstack/react-router";
import { useEffect, type ReactNode } from "react";

import appCss from "../styles.css?url";
import { reportLovableError } from "../lib/lovable-error-reporting";
import { EasterEgg } from "../components/EasterEgg";

function NotFoundComponent() {
  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="max-w-md text-center">
        <h1 className="font-display text-7xl crt-glow">404</h1>
        <h2 className="mt-2 text-lg uppercase tracking-widest">segfault: page not found</h2>
        <p className="mt-2 text-sm text-[color:var(--phosphor-dim)]">
          {"// this route was never committed"}
        </p>
        <div className="mt-6">
          <Link
            to="/"
            className="inline-flex items-center border border-[color:var(--phosphor)] px-3 py-1.5 text-sm uppercase tracking-widest hover:bg-[color:var(--phosphor)] hover:text-black"
          >
            cd ~/
          </Link>
        </div>
      </div>
    </div>
  );
}

function ErrorComponent({ error, reset }: { error: Error; reset: () => void }) {
  console.error(error);
  const router = useRouter();
  useEffect(() => {
    reportLovableError(error, { boundary: "tanstack_root_error_component" });
  }, [error]);

  return (
    <div className="flex min-h-screen items-center justify-center px-4">
      <div className="terminal-box max-w-md p-5">
        <h1 className="font-display text-2xl text-[color:var(--crimson)] crt-glow">
          panic: stack overflow
        </h1>
        <pre className="mt-2 text-xs text-[color:var(--phosphor-dim)]">{error.message}</pre>
        <div className="mt-4 flex gap-2">
          <button
            onClick={() => {
              router.invalidate();
              reset();
            }}
            className="border border-[color:var(--phosphor)] px-3 py-1 text-sm uppercase hover:bg-[color:var(--phosphor)] hover:text-black"
          >
            retry()
          </button>
          <a
            href="/"
            className="border border-[color:var(--phosphor-dim)] px-3 py-1 text-sm uppercase hover:border-[color:var(--phosphor)]"
          >
            cd ~/
          </a>
        </div>
      </div>
    </div>
  );
}

export const Route = createRootRouteWithContext<{ queryClient: QueryClient }>()({
  head: () => ({
    meta: [
      { charSet: "utf-8" },
      {
        name: "viewport",
        content: "width=device-width, initial-scale=1, viewport-fit=cover",
      },
      { title: "Homebrew Weather — Pixel-art forecast for devs" },
      {
        name: "description",
        content:
          "Weather for developers. Pixel-art icons, green-phosphor terminal UI, programmer jokes, and an installable home-screen widget.",
      },
      { name: "theme-color", content: "#0a1a0a" },
      { name: "apple-mobile-web-app-capable", content: "yes" },
      { name: "apple-mobile-web-app-status-bar-style", content: "black" },
      { name: "apple-mobile-web-app-title", content: "brew-wx" },
      {
        property: "og:title",
        content: "Homebrew Weather — Pixel-art forecast for devs",
      },
      {
        property: "og:description",
        content:
          "Weather for developers. Pixel-art icons, green-phosphor terminal UI, programmer jokes.",
      },
      { property: "og:type", content: "website" },
      { name: "twitter:card", content: "summary_large_image" },
    ],
    links: [
      { rel: "stylesheet", href: appCss },
      {
        rel: "stylesheet",
        href: "https://fonts.googleapis.com/css2?family=VT323&family=JetBrains+Mono:wght@400;700&display=swap",
      },
      { rel: "manifest", href: "/manifest.webmanifest" },
      { rel: "icon", href: "/icon-512.png", type: "image/png" },
      { rel: "apple-touch-icon", href: "/icon-512.png" },
    ],
  }),
  shellComponent: RootShell,
  component: RootComponent,
  notFoundComponent: NotFoundComponent,
  errorComponent: ErrorComponent,
});

function RootShell({ children }: { children: ReactNode }) {
  return (
    <html lang="en">
      <head>
        <HeadContent />
      </head>
      <body>
        {children}
        <Scripts />
      </body>
    </html>
  );
}

function RootComponent() {
  const { queryClient } = Route.useRouteContext();

  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <div className="crt-flicker min-h-screen">
          <Outlet />
        </div>
        <div className="crt-scanlines" />
        <div className="crt-vignette" />
        <EasterEgg />
      </I18nProvider>
    </QueryClientProvider>
  );
}
