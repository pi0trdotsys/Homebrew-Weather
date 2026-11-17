import { Capacitor, registerPlugin } from "@capacitor/core";

// The native half of the app (android/.../HbwPlugin.kt), reachable only inside
// the Android app. In a browser every call here is a no-op that says so —
// callers check `isNative()` or handle `null` rather than catching errors.

type HbwPlugin = {
  reverseGeocode(opts: { lat: number; lon: number }): Promise<{ name: string | null }>;
  notificationStatus(): Promise<{ granted: boolean }>;
  requestNotifications(): Promise<{ granted: boolean }>;
  sendTestNotifications(): Promise<{ granted: boolean; sent: number }>;
  refreshWidgets(): Promise<{ count: number }>;
  widgetStatus(): Promise<{ pinSupported: boolean; count: number }>;
  requestPinWidget(): Promise<{ requested: boolean }>;
};

const Hbw = registerPlugin<HbwPlugin>("Hbw");

export const isNative = () => Capacitor.isNativePlatform();

/** Place name from the device's own geocoder, or null (browser, no match). */
export async function nativePlaceName(lat: number, lon: number): Promise<string | null> {
  if (!isNative()) return null;
  try {
    return (await Hbw.reverseGeocode({ lat, lon })).name ?? null;
  } catch {
    return null;
  }
}

/** Asks for notification permission if it isn't granted yet; true if granted. */
export async function ensureNotificationPermission(): Promise<boolean> {
  if (!isNative()) return false;
  try {
    const status = await Hbw.notificationStatus();
    if (status.granted) return true;
    return (await Hbw.requestNotifications()).granted;
  } catch {
    return false;
  }
}

export async function sendTestNotifications(): Promise<{ granted: boolean; sent: number }> {
  return Hbw.sendTestNotifications();
}

/** Re-render home-screen widgets now, e.g. after a language or tone change. */
export function refreshWidgets(): void {
  if (!isNative()) return;
  Hbw.refreshWidgets().catch(() => {});
}

export type WidgetStatus = { pinSupported: boolean; count: number };

/**
 * Whether the launcher lets the app pin a widget, and how many are placed.
 * Never throws: in a browser — or an older build of the app that lacks the
 * method — it answers "not supported, none".
 */
export async function widgetStatus(): Promise<WidgetStatus> {
  if (!isNative()) return { pinSupported: false, count: 0 };
  try {
    const s = await Hbw.widgetStatus();
    return { pinSupported: s.pinSupported === true, count: Number(s.count) || 0 };
  } catch {
    return { pinSupported: false, count: 0 };
  }
}

/**
 * Asks the launcher to pin a widget (it shows its own confirmation, then opens
 * the widget's setup screen). True if the request was handed over; never throws.
 */
export async function requestPinWidget(): Promise<boolean> {
  if (!isNative()) return false;
  try {
    return (await Hbw.requestPinWidget()).requested === true;
  } catch {
    return false;
  }
}
