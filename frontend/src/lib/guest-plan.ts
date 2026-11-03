import { tripsApi } from "@/lib/api/client";
import type { GuestTrip, GuestTurn } from "@/lib/api/types";
import { toTripRequest, type TripFormValues } from "@/lib/validation";

/** A plan made without an account. It lives only in this browser tab (sessionStorage) until the user saves it. */
export interface GuestPlan {
  form: TripFormValues;
  answers: Record<string, string>;
  trip: GuestTrip | null;
  messages: GuestTurn[];
  /** Set when the user chose to sign up / sign in to keep the plan; the auth pages then save it. */
  saveRequested?: boolean;
}

const KEY = "tw_guest_plan";
export const GUEST_HISTORY_LIMIT = 20;

export function loadGuestPlan(): GuestPlan | null {
  try {
    const raw = window.sessionStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as GuestPlan) : null;
  } catch {
    return null;
  }
}

export function saveGuestPlan(plan: GuestPlan): void {
  try {
    window.sessionStorage.setItem(KEY, JSON.stringify(plan));
  } catch {
    // storage full or disabled: the plan just isn't kept across reloads
  }
}

export function clearGuestPlan(): void {
  try {
    window.sessionStorage.removeItem(KEY);
  } catch {
    // ignore
  }
}

/**
 * After sign-in/sign-up: saves the guest plan the user asked to keep and returns the new trip id.
 * Returns null when there is nothing to save; throws if saving failed (the plan stays in storage).
 */
export async function claimPendingGuestPlan(): Promise<number | null> {
  const plan = loadGuestPlan();
  if (!plan?.saveRequested || !plan.trip?.itinerary) return null;
  const saved = await tripsApi.claim({
    trip: toTripRequest(plan.form),
    answers: plan.answers,
    plan: plan.trip.itinerary.plan,
  });
  clearGuestPlan();
  return saved.id;
}

/** Where to go after sign-in/sign-up: the saved guest trip, back to the unsaved plan on failure, or the trip list. */
export async function afterSignInPath(): Promise<string> {
  try {
    const id = await claimPendingGuestPlan();
    return id ? `/trips/${id}` : "/trips";
  } catch {
    return "/plan";
  }
}
