import { z } from "zod";

import type { CreateTripRequest, TripDetail } from "./api/types";
import { CURRENCY_OPTIONS, todayIso, tripDays } from "./format";

export const MAX_TRIP_DAYS = 60;
export const MAX_STOPS = 6;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export const loginSchema = z.object({
  email: z.email({ error: "Enter a valid email" }),
  password: z.string().min(1, { error: "Enter your password" }),
});

export const registerSchema = z.object({
  fullName: z.string().trim().min(1, { error: "Enter your name" }).max(150),
  email: z.email({ error: "Enter a valid email" }).max(255),
  password: z
    .string()
    .min(8, { error: "Use at least 8 characters" })
    .max(72, { error: "Use at most 72 characters" }),
});

export type LoginValues = z.infer<typeof loginSchema>;
export type RegisterValues = z.infer<typeof registerSchema>;

/** Form values are strings (HTML inputs); `toTripRequest` converts them to the API shape. */
export const tripSchema = z
  .object({
    destination: z.string().trim().min(1, { error: "Where are you going?" }).max(255),
    origin: z.string().trim().min(1, { error: "Where are you starting from?" }).max(255),
    startDate: z.string().regex(ISO_DATE, { error: "Pick a start date" }),
    endDate: z.string().regex(ISO_DATE, { error: "Pick an end date" }),
    travellersCount: z.string().regex(/^\d+$/, { error: "Enter a number" }),
    budgetAmount: z.string().regex(/^(\d{1,10}(\.\d{1,2})?)?$/, { error: "Enter a valid amount" }),
    currency: z.enum(CURRENCY_OPTIONS),
    travelStyle: z.string().max(50),
    comfortLevel: z.enum(["BUDGET", "STANDARD", "PREMIUM", "LUXURY"]),
    transportModes: z.array(z.enum(["TRAIN", "FLIGHT", "BUS", "CAR"])).max(4),
    preferences: z.array(z.string().trim().min(1).max(100)).max(50),
    stops: z
      .array(
        z.object({
          city: z.string().trim().min(1, { error: "Enter a city" }).max(255),
          countryCode: z.string().regex(/^([A-Za-z]{2})?$/, { error: "2-letter code" }),
          nights: z.string().regex(/^[1-9]\d*$/, { error: "At least 1 night" }),
        }),
      )
      .max(MAX_STOPS, { error: `At most ${MAX_STOPS} stops` }),
    optimizeRouteOrder: z.boolean(),
  })
  .superRefine((v, ctx) => {
    const travellers = Number(v.travellersCount);
    if (v.travellersCount && (travellers < 1 || travellers > 50)) {
      ctx.addIssue({ code: "custom", path: ["travellersCount"], message: "Between 1 and 50 travellers" });
    }
    if (ISO_DATE.test(v.startDate) && v.startDate < todayIso()) {
      ctx.addIssue({ code: "custom", path: ["startDate"], message: "Start date can't be in the past" });
    }
    if (ISO_DATE.test(v.startDate) && ISO_DATE.test(v.endDate)) {
      if (v.endDate < v.startDate) {
        ctx.addIssue({ code: "custom", path: ["endDate"], message: "End date must be on or after the start date" });
      } else if (tripDays(v.startDate, v.endDate) > MAX_TRIP_DAYS) {
        ctx.addIssue({ code: "custom", path: ["endDate"], message: `Trips can be at most ${MAX_TRIP_DAYS} days` });
      } else if (v.stops.length) {
        const nights = tripDays(v.startDate, v.endDate) - 1;
        const planned = v.stops.reduce((sum, s) => sum + (Number(s.nights) || 0), 0);
        if (v.stops.length < 2) {
          ctx.addIssue({ code: "custom", path: ["stops"], message: "A multi-city trip needs at least 2 stops" });
        } else if (planned !== nights) {
          ctx.addIssue({
            code: "custom",
            path: ["stops"],
            message: `Stop nights add up to ${planned}, but your dates cover ${nights} night${nights === 1 ? "" : "s"}`,
          });
        }
      }
    }
  });

export type TripFormValues = z.infer<typeof tripSchema>;
export type StopFormValues = TripFormValues["stops"][number];

export const emptyTripForm: TripFormValues = {
  destination: "",
  origin: "",
  startDate: "",
  endDate: "",
  travellersCount: "2",
  budgetAmount: "",
  currency: "INR",
  travelStyle: "",
  comfortLevel: "STANDARD",
  transportModes: [],
  preferences: [],
  stops: [],
  optimizeRouteOrder: false,
};

function asCurrency(code: string | null | undefined): TripFormValues["currency"] {
  return (CURRENCY_OPTIONS as readonly string[]).includes(code ?? "") ? (code as TripFormValues["currency"]) : "INR";
}

/** Pre-fills the edit form from a saved trip. */
export function fromTrip(trip: TripDetail): TripFormValues {
  return {
    destination: trip.destination,
    origin: trip.origin ?? "",
    startDate: trip.startDate,
    endDate: trip.endDate,
    travellersCount: String(trip.travellersCount),
    budgetAmount: trip.budgetAmount == null ? "" : String(trip.budgetAmount),
    currency: asCurrency(trip.currency),
    travelStyle: trip.travelStyle ?? "",
    comfortLevel: trip.comfortLevel ?? "STANDARD",
    transportModes: trip.transportModes,
    preferences: trip.preferences,
    stops: [...trip.stops]
      .sort((a, b) => a.seq - b.seq)
      .map((s) => ({ city: s.city, countryCode: s.countryCode ?? "", nights: String(s.nights) })),
    optimizeRouteOrder: false,
  };
}

export function toTripRequest(v: TripFormValues): CreateTripRequest {
  const seen = new Set<string>();
  const preferences = v.preferences
    .map((p) => p.trim())
    .filter((p) => p && !seen.has(p.toLowerCase()) && seen.add(p.toLowerCase()));
  return {
    destination: v.destination.trim(),
    origin: v.origin.trim(),
    startDate: v.startDate,
    endDate: v.endDate,
    travellersCount: Number(v.travellersCount),
    budgetAmount: v.budgetAmount ? Number(v.budgetAmount) : undefined,
    currency: v.currency,
    travelStyle: v.travelStyle.trim() || undefined,
    comfortLevel: v.comfortLevel,
    transportModes: v.transportModes,
    preferences,
    stops: v.stops.map((s) => ({
      city: s.city.trim(),
      countryCode: s.countryCode.trim().toUpperCase() || undefined,
      nights: Number(s.nights),
    })),
    optimizeRouteOrder: v.stops.length ? v.optimizeRouteOrder : undefined,
  };
}
