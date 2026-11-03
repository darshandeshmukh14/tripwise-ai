import { beforeEach, describe, expect, it, vi } from "vitest";

import type { GuestTrip } from "@/lib/api/types";
import { afterSignInPath, claimPendingGuestPlan, type GuestPlan, loadGuestPlan, saveGuestPlan } from "@/lib/guest-plan";
import { emptyTripForm } from "@/lib/validation";

const fetchMock = vi.fn<typeof fetch>();

function storedPlan(saveRequested: boolean): GuestPlan {
  const trip = { itinerary: { plan: { tripTitle: "Goa" } } } as unknown as GuestTrip;
  return {
    form: { ...emptyTripForm, destination: "Goa", origin: "Mumbai", startDate: "2030-01-10", endDate: "2030-01-12" },
    answers: { pace: "relaxed" },
    trip,
    messages: [],
    saveRequested,
  };
}

beforeEach(() => {
  window.sessionStorage.clear();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("guest plan", () => {
  it("does nothing when the user did not ask to save", async () => {
    saveGuestPlan(storedPlan(false));
    expect(await claimPendingGuestPlan()).toBeNull();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("saves the requested plan to the account and clears it", async () => {
    saveGuestPlan(storedPlan(true));
    fetchMock.mockResolvedValue(Response.json({ id: 42 }, { status: 201 }));

    expect(await afterSignInPath()).toBe("/trips/42");
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/backend/trips/claim");
    const body = JSON.parse(String(init?.body));
    expect(body.plan).toEqual({ tripTitle: "Goa" });
    expect(body.answers).toEqual({ pace: "relaxed" });
    expect(body.trip.destination).toBe("Goa");
    expect(loadGuestPlan()).toBeNull();
  });

  it("keeps the plan and returns to it when saving fails", async () => {
    saveGuestPlan(storedPlan(true));
    fetchMock.mockResolvedValue(Response.json({ detail: "bad" }, { status: 400 }));

    expect(await afterSignInPath()).toBe("/plan");
    expect(loadGuestPlan()?.trip).not.toBeNull();
  });
});
