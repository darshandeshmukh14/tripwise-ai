import { describe, expect, it } from "vitest";

import type { TripDetail } from "./api/types";
import { todayIso } from "./format";
import { emptyTripForm, fromTrip, registerSchema, toTripRequest, tripSchema, type TripFormValues } from "./validation";

function isoPlus(days: number): string {
  const d = new Date(`${todayIso()}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
}

const valid: TripFormValues = {
  ...emptyTripForm,
  destination: " Goa ",
  origin: "Pune",
  startDate: isoPlus(10),
  endDate: isoPlus(13),
  budgetAmount: "40000",
  travelStyle: "relaxed",
  transportModes: ["TRAIN"],
  preferences: ["Food", " food ", "Beaches"],
};

const issues = (values: TripFormValues) => {
  const result = tripSchema.safeParse(values);
  return result.success ? {} : Object.fromEntries(result.error.issues.map((i) => [i.path.join("."), i.message]));
};

describe("tripSchema", () => {
  it("accepts a valid trip", () => {
    expect(issues(valid)).toEqual({});
  });

  it("rejects past start dates, reversed ranges and trips over 60 days", () => {
    expect(issues({ ...valid, startDate: isoPlus(-1) })).toHaveProperty("startDate");
    expect(issues({ ...valid, endDate: isoPlus(9) })).toHaveProperty("endDate", "End date must be on or after the start date");
    expect(issues({ ...valid, endDate: isoPlus(70) })).toHaveProperty("endDate", "Trips can be at most 60 days");
  });

  it("validates travellers and budget", () => {
    expect(issues({ ...valid, travellersCount: "0" })).toHaveProperty("travellersCount");
    expect(issues({ ...valid, travellersCount: "51" })).toHaveProperty("travellersCount");
    expect(issues({ ...valid, budgetAmount: "12.345" })).toHaveProperty("budgetAmount");
    expect(issues({ ...valid, budgetAmount: "" })).toEqual({});
  });

  it("checks multi-city stops: 2 to 6 stops whose nights cover the trip", () => {
    // valid trip: 4 days = 3 nights
    const stop = (city: string, nights: string) => ({ city, countryCode: "", nights });
    expect(issues({ ...valid, stops: [stop("Goa", "1"), stop("Gokarna", "2")] })).toEqual({});
    expect(issues({ ...valid, stops: [stop("Goa", "3")] })).toHaveProperty("stops", "A multi-city trip needs at least 2 stops");
    expect(issues({ ...valid, stops: [stop("Goa", "1"), stop("Gokarna", "1")] })).toHaveProperty(
      "stops",
      "Stop nights add up to 2, but your dates cover 3 nights",
    );
    expect(issues({ ...valid, stops: [stop("Goa", "0"), stop("Gokarna", "3")] })).toHaveProperty("stops.0.nights");
    expect(issues({ ...valid, stops: Array.from({ length: 7 }, (_, i) => stop(`C${i}`, "1")) })).toHaveProperty("stops");
  });

  it("rejects unsupported currencies", () => {
    expect(issues({ ...valid, currency: "XYZ" as TripFormValues["currency"] })).toHaveProperty("currency");
  });
});

describe("toTripRequest", () => {
  it("trims, converts numbers and dedupes preferences case-insensitively", () => {
    const request = toTripRequest(valid);
    expect(request).toMatchObject({ destination: "Goa", travellersCount: 2, budgetAmount: 40000, travelStyle: "relaxed" });
    expect(request.preferences).toEqual(["Food", "Beaches"]);
  });

  it("omits empty optional fields", () => {
    const request = toTripRequest({ ...valid, budgetAmount: "", travelStyle: " " });
    expect(request.budgetAmount).toBeUndefined();
    expect(request.travelStyle).toBeUndefined();
    expect(request.stops).toEqual([]);
    expect(request.optimizeRouteOrder).toBeUndefined();
    expect(request.currency).toBe("INR");
  });
});

describe("fromTrip", () => {
  it("round-trips a saved trip through the edit form", () => {
    const trip = {
      id: 7, destination: "Goa", origin: null, startDate: isoPlus(10), endDate: isoPlus(13), travellersCount: 3,
      budgetAmount: 45000.5, travelStyle: null, comfortLevel: "PREMIUM", transportModes: ["FLIGHT"],
      preferences: ["Food"], status: "DRAFT", itinerary: null, createdAt: "", updatedAt: "", currency: "THB",
      stops: [
        { seq: 2, city: "Krabi", countryCode: "TH", nights: 1 },
        { seq: 1, city: "Phuket", countryCode: "TH", nights: 2 },
      ],
    } as unknown as TripDetail;

    const values = fromTrip(trip);

    expect(values).toMatchObject({ origin: "", travellersCount: "3", budgetAmount: "45000.5", travelStyle: "", comfortLevel: "PREMIUM", currency: "THB" });
    expect(values.stops.map((s) => s.city)).toEqual(["Phuket", "Krabi"]);
    expect(toTripRequest(values)).toMatchObject({
      destination: "Goa", travellersCount: 3, budgetAmount: 45000.5, transportModes: ["FLIGHT"], currency: "THB",
      stops: [{ city: "Phuket", countryCode: "TH", nights: 2 }, { city: "Krabi", countryCode: "TH", nights: 1 }],
    });
  });

  it("falls back to INR for an unknown currency", () => {
    const trip = { ...emptyTripForm, travellersCount: 1, currency: "XYZ", stops: [], transportModes: [], preferences: [] };
    expect(fromTrip(trip as unknown as TripDetail).currency).toBe("INR");
  });
});

describe("registerSchema", () => {
  it("enforces the backend password length (8-72)", () => {
    const base = { fullName: "Asha", email: "asha@example.com" };
    expect(registerSchema.safeParse({ ...base, password: "short" }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, password: "x".repeat(73) }).success).toBe(false);
    expect(registerSchema.safeParse({ ...base, password: "long-enough" }).success).toBe(true);
  });
});
