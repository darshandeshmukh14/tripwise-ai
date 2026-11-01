import type { BadgeVariant } from "@/components/ui/badge";
import type { BudgetStatus, ComfortLevel, Confidence, CostCategory, CostSource, TransportMode, TripStatus } from "./api/types";

const dateFmt = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric", timeZone: "UTC" });
const DAY_MS = 86_400_000;
const moneyFormats = new Map<string, Intl.NumberFormat>();

/** Same list as the backend's SupportedCurrencies (INR first = default). */
export const CURRENCY_OPTIONS = [
  "INR", "USD", "EUR", "GBP", "CHF", "AED", "OMR", "NPR", "BTN", "LKR", "MVR", "THB", "VND", "SGD", "MYR",
  "IDR", "JPY", "KRW", "KZT", "AZN", "GEL", "TRY", "EGP", "KES", "TZS", "ZAR", "MUR", "SCR", "MAD",
] as const;

function moneyFormat(currency: string): Intl.NumberFormat {
  let fmt = moneyFormats.get(currency);
  if (!fmt) {
    try {
      fmt = new Intl.NumberFormat("en-IN", { style: "currency", currency, maximumFractionDigits: 0 });
    } catch {
      fmt = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
    }
    moneyFormats.set(currency, fmt);
  }
  return fmt;
}

export function formatMoney(amount: number | null | undefined, currency: string | null | undefined = "INR"): string {
  return amount === null || amount === undefined ? "—" : moneyFormat(currency || "INR").format(amount);
}

export function formatINR(amount: number | null | undefined): string {
  return formatMoney(amount, "INR");
}

export function formatRange(min: number, max: number, currency: string | null | undefined = "INR"): string {
  return `${formatMoney(min, currency)} – ${formatMoney(max, currency)}`;
}

/** `YYYY-MM-DD` dates are calendar dates: format in UTC so the day never shifts with the browser time zone. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "—";
  return dateFmt.format(new Date(`${iso}T00:00:00Z`));
}

export function tripDays(startIso: string, endIso: string): number {
  return Math.round((Date.parse(`${endIso}T00:00:00Z`) - Date.parse(`${startIso}T00:00:00Z`)) / DAY_MS) + 1;
}

export function todayIso(now = new Date()): string {
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}

export function formatHours(hours: number): string {
  const h = Math.floor(hours);
  const m = Math.round((hours - h) * 60);
  return m ? `${h}h ${m}m` : `${h}h`;
}

export function titleCase(value: string): string {
  return value.toLowerCase().replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

export const STATUS_META: Record<TripStatus, { label: string; variant: BadgeVariant }> = {
  DRAFT: { label: "Draft", variant: "muted" },
  GENERATING: { label: "Planning…", variant: "warning" },
  COMPLETED: { label: "Ready", variant: "success" },
  FAILED: { label: "Failed", variant: "danger" },
};

export const BUDGET_META: Record<BudgetStatus, { label: string; variant: BadgeVariant }> = {
  WITHIN: { label: "Within budget", variant: "success" },
  NEAR: { label: "Close to budget", variant: "warning" },
  OVER: { label: "Over budget", variant: "danger" },
  UNKNOWN: { label: "No budget set", variant: "muted" },
};

export const CONFIDENCE_VARIANT: Record<Confidence, BadgeVariant> = {
  HIGH: "success",
  MEDIUM: "warning",
  LOW: "danger",
};

export const SOURCE_META: Record<CostSource, { label: string; variant: BadgeVariant }> = {
  ESTIMATE: { label: "Estimate", variant: "muted" },
  FARE_RULE: { label: "Fare rule", variant: "success" },
  WEB_HINT: { label: "Web hint", variant: "warning" },
};

export const COMFORT_OPTIONS: { value: ComfortLevel; label: string; hint: string }[] = [
  { value: "BUDGET", label: "Budget", hint: "Hostels, sleeper class, street food" },
  { value: "STANDARD", label: "Standard", hint: "3-star hotels, AC travel" },
  { value: "PREMIUM", label: "Premium", hint: "4-star hotels, flights or AC 2-tier" },
  { value: "LUXURY", label: "Luxury", hint: "5-star stays, business comfort" },
];

export const TRANSPORT_OPTIONS: { value: TransportMode; label: string }[] = [
  { value: "TRAIN", label: "Train" },
  { value: "FLIGHT", label: "Flight" },
  { value: "BUS", label: "Bus" },
  { value: "CAR", label: "Car" },
];

export const CATEGORY_LABEL: Record<CostCategory, string> = {
  TRANSPORT: "Travel",
  STAY: "Stay",
  FOOD: "Food",
  ACTIVITY: "Activities",
  LOCAL_TRANSPORT: "Local transport",
  MISC: "Buffer",
};
