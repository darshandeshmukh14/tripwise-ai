import type { CostBreakdown, TransportOption } from "@/lib/api/types";

/** Builds an unsigned JWT-shaped token with the given `exp` (seconds); the BFF never verifies signatures. */
export function fakeToken(expSeconds: number): string {
  const encode = (o: object) => Buffer.from(JSON.stringify(o)).toString("base64url");
  return `${encode({ alg: "HS256" })}.${encode({ sub: "1", email: "a@b.co", exp: expSeconds })}.sig`;
}

export const transportOptions: TransportOption[] = [
  {
    mode: "TRAIN",
    travelClass: "AC 3-tier",
    distanceKm: 590,
    durationHours: 11.5,
    cost: { min: 4000, expected: 5200, max: 6500 },
    comfortScore: 3,
    recommended: true,
    notes: "Overnight journey",
  },
  {
    mode: "FLIGHT",
    travelClass: "Economy",
    distanceKm: 450,
    durationHours: 1.25,
    cost: { min: 9000, expected: 12000, max: 16000 },
    comfortScore: 4,
    recommended: false,
    notes: null,
  },
];

export const costBreakdown: CostBreakdown = {
  currency: "INR",
  totalMin: 30000,
  totalExpected: 38000,
  totalMax: 47000,
  budgetStatus: "NEAR",
  items: [
    {
      category: "STAY",
      mode: null,
      label: "Hotel (3 nights)",
      amountMin: 9000,
      amountExpected: 12000,
      amountMax: 15000,
      season: "PEAK",
      source: "ESTIMATE",
      confidence: "MEDIUM",
      assumptions: "1 room, peak season",
      stopSeq: null,
      legIndex: null,
      sourceUrl: null,
    },
    {
      category: "TRANSPORT",
      mode: "TRAIN",
      label: "Train return",
      amountMin: 4000,
      amountExpected: 5200,
      amountMax: 6500,
      season: "PEAK",
      source: "ESTIMATE",
      confidence: "LOW",
      assumptions: null,
      stopSeq: null,
      legIndex: null,
      sourceUrl: null,
    },
  ],
};
