import { createFileRoute, Link } from "@tanstack/react-router";
import { useEffect, useRef, useState } from "react";
import {
  loadRefreshInterval,
  saveRefreshInterval,
  loadNotificationSettings,
  saveNotificationSettings,
  loadTone,
  saveTone,
  DEFAULT_NOTIFICATION_SETTINGS,
  DEFAULT_TONE,
  type RefreshInterval,
  type NotificationSettings,
  type Tone,
} from "@/lib/settings";
import { useI18n, type Lang } from "@/lib/i18n";
import {
  ensureNotificationPermission,
  isNative,
  refreshWidgets,
  sendTestNotifications,
} from "@/lib/native";

const TONES: Tone[] = ["clean", "sigma", "rude"];
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
}: {
  label: string;
  value: number;
  onChange: (v: number) => void;
  suffix?: string;
  disabled?: boolean;
}) {
  return (
    <label className="flex items-center justify-between gap-3 border border-[color:var(--phosphor-dim)]/40 px-3 py-2 text-sm">
      <span className={disabled ? "text-[color:var(--phosphor-dim)]" : ""}>{label}</span>
      <span className="flex items-center gap-1">
        <input
          type="number"
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(Number(e.target.value))}
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
  const [loaded, setLoaded] = useState(false);
  const [testState, setTestState] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([loadRefreshInterval(), loadNotificationSettings(), loadTone()]).then(
      ([i, n, t]) => {
        setInterval(i);
        setNotif(n);
        setTone(t);
        setLoaded(true);
      },
    );
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

        <p className="text-[10px] uppercase tracking-widest text-[color:var(--phosphor-dim)]">
          {t.nativeNote1}
          <br />
          {t.nativeNote2}
        </p>
      </div>
    </div>
  );
}
