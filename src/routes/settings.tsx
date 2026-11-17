import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import {
  loadRefreshInterval,
  saveRefreshInterval,
  loadNotificationSettings,
  saveNotificationSettings,
  loadTone,
  saveTone,
  loadFooterSource,
  saveFooterSource,
  loadHighContrastChoice,
  saveHighContrast,
  DEFAULT_NOTIFICATION_SETTINGS,
  DEFAULT_TONE,
  DEFAULT_FOOTER_SOURCE,
  type RefreshInterval,
  type NotificationSettings,
  type Tone,
  type FooterSource,
} from "@/lib/settings";
import { applyContrast, resolveContrast } from "@/lib/contrast";
import { AddWidgetButton } from "@/components/AddWidget";
import { useWidgetStatus } from "@/hooks/useWidgetStatus";
import { useI18n, type Lang } from "@/lib/i18n";
import {
  ensureNotificationPermission,
  isNative,
  refreshWidgets,
  sendTestNotifications,
} from "@/lib/native";

const TONES: Tone[] = ["clean", "sigma", "rude"];
const FOOTER_SOURCES: FooterSource[] = ["joke", "wellbeing", "history"];
const LANGS: Array<{ id: Lang; label: string }> = [
  { id: "pl", label: "polski" },
  { id: "en", label: "english" },
];

/** Settings keys that turn a notification on — enabling one asks for permission. */
const NOTIFYING: Array<keyof NotificationSettings> = [
  "rainEnabled",
  "briefEnabled",
  "eveningEnabled",
  "highEnabled",
  "lowEnabled",
  "swingEnabled",
  "aqiEnabled",
  "uvEnabled",
  "gustEnabled",
  "severeEnabled",
];

export const Route = createFileRoute("/settings")({
  head: () => ({
    meta: [
      { title: "settings — Homebrew Weather" },
      {
        name: "description",
        content: "Configure refresh interval, notifications, tone and language.",
      },
    ],
  }),
  component: Settings,
});

function Toggle({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (v: boolean) => void;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      className="flex w-full items-center justify-between gap-3 border border-[color:var(--phosphor-dim)]/40 px-3 py-2 text-left text-sm hover:border-[color:var(--phosphor)]"
    >
      <span>{label}</span>
      <span
        className={
          "border px-2 py-0.5 text-[10px] uppercase tracking-widest " +
          (checked
            ? "border-[color:var(--phosphor)] bg-[color:var(--phosphor)] text-black"
            : "border-[color:var(--phosphor-dim)] text-[color:var(--phosphor-dim)]")
        }
      >
        {checked ? "on" : "off"}
      </span>
    </button>
  );
}

function ThresholdInput({
  label,
  value,
  onChange,
  suffix = "°C",
  disabled,
  min,
  max,
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  suffix?: string;
  disabled?: boolean;
  min?: number;
  max?: number;
}) {
  return (
    <label className="flex items-center justify-between gap-3 border border-[color:var(--phosphor-dim)]/40 px-3 py-2 text-sm">
      <span className={disabled ? "text-[color:var(--phosphor-dim)]" : ""}>{label}</span>
      <span className="flex items-center gap-1">
        <input
          type="number"
          value={value}
          min={min}
          max={max}
          disabled={disabled}
          onChange={(e) => onChange(Number(e.target.value))}
          // Typing "45" passes through "4"; only a finished entry is held to the range.
          onBlur={() => {
            if (min == null || max == null) return;
            const v = Number.isFinite(value) ? Math.min(max, Math.max(min, value)) : min;
            if (v !== value) onChange(v);
          }}
          className="w-16 bg-transparent text-right outline-none disabled:opacity-40"
        />
        <span className="text-[color:var(--phosphor-dim)]">{suffix}</span>
      </span>
    </label>
  );
}

function HourSelect({
  label,
  value,
  hours,
  disabled,
  onChange,
}: {
  label: string;
  value: number;
  hours: number[];
  disabled: boolean;
  onChange: (h: number) => void;
}) {
  return (
    <label className="flex items-center justify-between gap-3 border border-[color:var(--phosphor-dim)]/40 px-3 py-2 text-sm">
      <span className={disabled ? "text-[color:var(--phosphor-dim)]" : ""}>{label}</span>
      <select
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))}
        className="bg-transparent border border-[color:var(--phosphor-dim)] px-2 py-0.5 text-[color:var(--phosphor)] focus:outline-none focus:border-[color:var(--phosphor)] disabled:opacity-40"
      >
        {hours.map((h) => (
          <option key={h} value={h}>
            {String(h).padStart(2, "0")}:00
          </option>
        ))}
      </select>
    </label>
  );
}

function Settings() {
  const { lang, t, setLang } = useI18n();
  const [interval, setInterval] = useState<RefreshInterval>(30);
  const [notif, setNotif] = useState<NotificationSettings>(DEFAULT_NOTIFICATION_SETTINGS);
  const [tone, setTone] = useState<Tone>(DEFAULT_TONE);
  const [footerSource, setFooterSource] = useState<FooterSource>(DEFAULT_FOOTER_SOURCE);
  // null = never chosen: the system's "more contrast" setting decides.
  const [contrastChoice, setContrastChoice] = useState<boolean | null>(null);
  const { status: widget } = useWidgetStatus();
  const [loaded, setLoaded] = useState(false);
  const [testState, setTestState] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([
      loadRefreshInterval(),
      loadNotificationSettings(),
      loadTone(),
      loadFooterSource(),
      loadHighContrastChoice(),
    ]).then(([i, n, t, f, c]) => {
      setInterval(i);
      setNotif(n);
      setTone(t);
      setFooterSource(f);
      setContrastChoice(c);
      setLoaded(true);
    });
  }, []);

  // Language and tone show on the home screen too, so widgets are re-rendered
  // as soon as either changes rather than at their next scheduled refresh.
  const firstRender = useRef(true);
  useEffect(() => {
    if (!loaded) return;
    saveTone(tone).then(() => {
      if (firstRender.current) firstRender.current = false;
      else refreshWidgets();
    });
  }, [tone, loaded]);

  // The widget's footer and colours follow these two as well, so it is
  // re-rendered once the new value is stored.
  const changeFooterSource = (id: FooterSource) => {
    setFooterSource(id);
    void saveFooterSource(id).then(refreshWidgets);
  };

  const contrastOn = resolveContrast(contrastChoice) === "high";
  const changeContrast = (on: boolean) => {
    setContrastChoice(on);
    applyContrast(on);
    void saveHighContrast(on).then(refreshWidgets);
  };

  const changeLang = (l: Lang) => {
    setLang(l);
    // setLang saves asynchronously; give it a moment before widgets re-read it.
    window.setTimeout(refreshWidgets, 300);
  };

  useEffect(() => {
    if (loaded) saveRefreshInterval(interval);
  }, [interval, loaded]);

  useEffect(() => {
    if (loaded) saveNotificationSettings(notif);
  }, [notif, loaded]);

  const patch = (p: Partial<NotificationSettings>) => {
    setNotif((prev) => ({ ...prev, ...p }));
    // Turning a notification on is the moment permission is obviously needed —
    // previously it was only ever asked for when adding a widget.
    const enabling = NOTIFYING.some((k) => p[k] === true);
    if (enabling && isNative()) {
      void ensureNotificationPermission().then((granted) => {
        if (!granted) setTestState(t.testNoPermission);
      });
    }
  };

  const sendTest = async () => {
    if (!isNative()) {
      setTestState(t.testWebOnly);
      return;
    }
    setTestState(t.testSending);
    const granted = await ensureNotificationPermission();
    if (!granted) {
      setTestState(t.testNoPermission);
      return;
    }
    try {
      const r = await sendTestNotifications();
      setTestState(r.granted ? t.testSent(r.sent) : t.testNoPermission);
    } catch (e) {
      setTestState(t.testFailed((e as Error).message));
    }
  };

  return (
    <div className="mx-auto max-w-2xl px-4 py-8 sm:px-6">
      <Link
        to="/"
        className="text-xs uppercase tracking-widest text-[color:var(--phosphor-dim)] hover:text-[color:var(--phosphor)]"
      >
        ← cd ~/
      </Link>
      <h1 className="mt-4 font-display text-4xl crt-glow">$ vim /etc/homebrew-weather.conf</h1>

      <div className="terminal-box mt-6 space-y-4 p-5 text-sm">
        <div>
          <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.language}
          </div>
          <div className="grid grid-cols-2 gap-2">
            {LANGS.map((l) => (
              <button
                key={l.id}
                type="button"
                onClick={() => changeLang(l.id)}
                className={
                  "border px-3 py-2 text-sm uppercase tracking-widest " +
                  (lang === l.id
                    ? "border-[color:var(--phosphor)] bg-[color:var(--phosphor)] text-black"
                    : "border-[color:var(--phosphor-dim)]/40 hover:border-[color:var(--phosphor)]")
                }
              >
                {l.label}
              </button>
            ))}
          </div>
          <p className="px-1 pt-2 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
            {t.languageHint}
          </p>
        </div>

        <div>
          <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.refresh}
          </div>
          <label className="flex items-center justify-between gap-3 border border-[color:var(--phosphor-dim)]/40 px-3 py-2">
            <span>{t.refreshLabel}</span>
            <select
              value={interval}
              onChange={(e) => setInterval(Number(e.target.value) as RefreshInterval)}
              className="bg-transparent border border-[color:var(--phosphor-dim)] px-2 py-0.5 text-[color:var(--phosphor)] focus:outline-none focus:border-[color:var(--phosphor)]"
            >
              <option value={15}>15m</option>
              <option value={30}>30m</option>
              <option value={60}>1h</option>
              <option value={180}>3h</option>
              <option value={360}>6h</option>
            </select>
          </label>
        </div>

        <div>
          <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.notifications}
          </div>
          <div className="space-y-2">
            <Toggle
              label={t.rainSoon}
              checked={notif.rainEnabled}
              onChange={(v) => patch({ rainEnabled: v })}
            />
            <Toggle
              label={t.briefToggle}
              checked={notif.briefEnabled}
              onChange={(v) => patch({ briefEnabled: v })}
            />
            <HourSelect
              label={t.from}
              value={notif.briefHour}
              hours={[5, 6, 7, 8, 9, 10, 11]}
              disabled={!notif.briefEnabled}
              onChange={(h) => patch({ briefHour: h })}
            />
            <Toggle
              label={t.eveningToggle}
              checked={notif.eveningEnabled}
              onChange={(v) => patch({ eveningEnabled: v })}
            />
            <HourSelect
              label={t.from}
              value={notif.eveningHour}
              hours={[17, 18, 19, 20, 21, 22]}
              disabled={!notif.eveningEnabled}
              onChange={(h) => patch({ eveningHour: h })}
            />
            <p className="px-1 pt-1 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
              {notif.briefEnabled ? t.briefOn : t.briefOff}
            </p>
            <Toggle
              label={t.high}
              checked={notif.highEnabled}
              onChange={(v) => patch({ highEnabled: v })}
            />
            <ThresholdInput
              label={t.threshold}
              value={notif.highThreshold}
              disabled={!notif.highEnabled}
              onChange={(v) => patch({ highThreshold: v })}
            />
            <Toggle
              label={t.low}
              checked={notif.lowEnabled}
              onChange={(v) => patch({ lowEnabled: v })}
            />
            <ThresholdInput
              label={t.threshold}
              value={notif.lowThreshold}
              disabled={!notif.lowEnabled}
              onChange={(v) => patch({ lowThreshold: v })}
            />
            <Toggle
              label={t.swing}
              checked={notif.swingEnabled}
              onChange={(v) => patch({ swingEnabled: v })}
            />
            <ThresholdInput
              label={t.minDelta}
              value={notif.swingThreshold}
              disabled={!notif.swingEnabled}
              onChange={(v) => patch({ swingThreshold: v })}
            />
            <Toggle
              label={t.air}
              checked={notif.aqiEnabled}
              onChange={(v) => patch({ aqiEnabled: v })}
            />
            <ThresholdInput
              label={t.aqiThreshold}
              value={notif.aqiThreshold}
              disabled={!notif.aqiEnabled}
              onChange={(v) => patch({ aqiThreshold: v })}
              suffix="AQI"
            />
            <Toggle
              label={t.uvToggle}
              checked={notif.uvEnabled}
              onChange={(v) => patch({ uvEnabled: v })}
            />
            <ThresholdInput
              label={t.uvThreshold}
              value={notif.uvThreshold}
              disabled={!notif.uvEnabled}
              onChange={(v) => patch({ uvThreshold: v })}
              suffix="UV"
              min={3}
              max={11}
            />
            <Toggle
              label={t.gustToggle}
              checked={notif.gustEnabled}
              onChange={(v) => patch({ gustEnabled: v })}
            />
            <ThresholdInput
              label={t.gustThreshold}
              value={notif.gustThreshold}
              disabled={!notif.gustEnabled}
              onChange={(v) => patch({ gustThreshold: v })}
              suffix="km/h"
              min={30}
              max={120}
            />
            <p className="px-1 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
              {t.uvGustNote}
            </p>
            <Toggle
              label={t.severeToggle}
              checked={notif.severeEnabled}
              onChange={(v) => patch({ severeEnabled: v })}
            />
            <p className="px-1 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
              {t.severeSettingsNote}
            </p>
            <button
              type="button"
              onClick={() => void sendTest()}
              className="mt-1 w-full border border-[color:var(--phosphor)] px-3 py-2 text-xs uppercase tracking-widest hover:bg-[color:var(--phosphor)] hover:text-black"
            >
              {t.testNotifications}
            </button>
            {testState && (
              <p className="px-1 text-[10px] uppercase tracking-widest text-[color:var(--amber)]">
                {testState}
              </p>
            )}
          </div>
        </div>

        <div>
          <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.tone}
          </div>
          <div className="grid grid-cols-3 gap-2">
            {TONES.map((id) => (
              <button
                key={id}
                type="button"
                onClick={() => setTone(id)}
                className={
                  "border px-3 py-2 text-sm uppercase tracking-widest " +
                  (tone === id
                    ? "border-[color:var(--phosphor)] bg-[color:var(--phosphor)] text-black"
                    : "border-[color:var(--phosphor-dim)]/40 hover:border-[color:var(--phosphor)]")
                }
              >
                {id}
              </button>
            ))}
          </div>
          <p className="px-1 pt-2 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
            {"// "}
            {t.toneHints[tone]} · {t.toneApplies}
          </p>
        </div>

        <div>
          <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.footerSource}
          </div>
          <div className="grid grid-cols-3 gap-2">
            {FOOTER_SOURCES.map((id) => (
              <button
                key={id}
                type="button"
                onClick={() => changeFooterSource(id)}
                aria-pressed={footerSource === id}
                className={
                  "border px-2 py-2 text-xs uppercase tracking-widest sm:text-sm " +
                  (footerSource === id
                    ? "border-[color:var(--phosphor)] bg-[color:var(--phosphor)] text-black"
                    : "border-[color:var(--phosphor-dim)]/40 hover:border-[color:var(--phosphor)]")
                }
              >
                {t.footerSourceNames[id]}
              </button>
            ))}
          </div>
          <p className="px-1 pt-2 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
            {"// "}
            {t.footerSourceHints[footerSource]} · {t.footerSourceApplies}
          </p>
        </div>

        <div>
          <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.displaySection}
          </div>
          <Toggle label={t.contrastToggle} checked={contrastOn} onChange={changeContrast} />
          <p className="px-1 pt-2 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
            {t.contrastHint}
          </p>
        </div>

        <div>
          <div className="mb-2 text-[color:var(--phosphor-dim)] uppercase tracking-widest">
            {t.widgetSection}
          </div>
          {widget?.pinSupported ? (
            <AddWidgetButton />
          ) : (
            <p className="px-1 text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
              {t.widgetManualHint}
            </p>
          )}
        </div>

        <p className="text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          {t.nativeNote1}
          <br />
          {t.nativeNote2}
        </p>
      </div>
    </div>
  );
}
