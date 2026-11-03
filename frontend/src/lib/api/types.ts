import type { components } from "./schema";

type S = components["schemas"];
/** springdoc marks every response field optional; these make present fields required and nullable ones explicit. */
type Strict<T, Nullable extends keyof T = never> = Required<Omit<T, Nullable>> & { [P in Nullable]-?: Exclude<T[P], undefined> | null };

export type ComfortLevel = NonNullable<S["CreateTripRequest"]["comfortLevel"]>;
export type TransportMode = NonNullable<S["CreateTripRequest"]["transportModes"]>[number];
export type TripStatus = NonNullable<S["TripDetailResponse"]["status"]>;
export type BudgetStatus = NonNullable<S["CostBreakdownResponse"]["budgetStatus"]>;
export type Confidence = NonNullable<S["Item"]["confidence"]>;
export type Season = NonNullable<S["Item"]["season"]>;
export type CostCategory = NonNullable<S["Item"]["category"]>;

export type CostSource = NonNullable<S["Item"]["source"]>;

export type CreateTripRequest = Omit<
  S["CreateTripRequest"],
  "endDateOnOrAfterStartDate" | "durationWithinLimit" | "currencySupported" | "stopsConsistent"
>;
export type TripStopRequest = S["TripStopRequest"];
export type AuthResponse = Strict<S["AuthResponse"]>;
export type Me = Strict<S["MeResponse"]>;
export type TripSummary = Strict<S["TripSummaryResponse"], "budgetAmount">;
export type CostItem = Strict<S["Item"], "mode" | "assumptions" | "stopSeq" | "legIndex" | "sourceUrl">;
export type CostBreakdown = Omit<Strict<S["CostBreakdownResponse"]>, "items"> & { items: CostItem[] };
export type Itinerary = Omit<
  Strict<S["ItineraryResponse"], "costBreakdown" | "fxRate" | "fxAsOf">,
  "plan" | "costBreakdown"
> & {
  plan: ItineraryPlan;
  costBreakdown: CostBreakdown | null;
};
export type TripStop = Strict<S["TripStopResponse"]>;
export type TripDetail = Omit<
  Strict<S["TripDetailResponse"], "origin" | "budgetAmount" | "travelStyle" | "comfortLevel" | "itinerary">,
  "itinerary" | "stops"
> & { itinerary: Itinerary | null; stops: TripStop[] };
export type StopSuggestion = Strict<S["Suggestion"]>;
export type StopSuggestions = { suggestions: StopSuggestion[] };
export type TripStatusResponse = Strict<S["TripStatusResponse"]>;
export type ClarifyQuestion = Strict<S["Question"]>;
export type ClarifyResponse = { questions: ClarifyQuestion[] };
export type ChatMessage = Strict<S["ChatMessageResponse"]>;
export type ChatReply = Omit<Strict<S["ChatReplyResponse"], "itineraryVersion">, "userMessage" | "assistantMessage"> & {
  userMessage: ChatMessage;
  assistantMessage: ChatMessage;
};

/** Planning without an account: nothing is stored, so the trip has no id. Hand-written until `npm run gen:api`. */
export type GuestTrip = Omit<TripDetail, "id" | "createdAt" | "updatedAt" | "itinerary"> & { itinerary: Itinerary };
export type GuestTurn = { role: "USER" | "ASSISTANT"; content: string };
export type GuestChatReply = { assistantMessage: string; itineraryUpdated: boolean; trip: GuestTrip };

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** Stored itinerary JSON (`plan`), produced by the AI service contract (ai-service/app/schemas.py). */
export interface AmountRange {
  min: number;
  expected: number;
  max: number;
}

export interface TransportOption {
  mode: TransportMode;
  travelClass: string;
  distanceKm: number;
  durationHours: number;
  cost: AmountRange;
  comfortScore: number;
  recommended: boolean;
  notes: string | null;
}

export interface Activity {
  timeOfDay: "MORNING" | "AFTERNOON" | "EVENING";
  title: string;
  description: string;
}

export interface DayPlan {
  day: number;
  date: string | null;
  title: string;
  summary: string;
  activities: Activity[];
}

export interface Stay {
  tier: ComfortLevel;
  nightlyRate: AmountRange;
  nights: number;
  rooms: number;
  areaSuggestion: string | null;
}

export interface FxInfo {
  base: string;
  quote: string;
  rate: number;
  asOf: string;
  source: string;
}

export interface StopPlan {
  seq: number;
  city: string;
  countryCode: string;
  nights: number;
  arrivalDate: string;
  departureDate: string;
  season: Season;
  stay?: Stay | null;
}

export interface LegPlan {
  index: number;
  fromCity: string;
  toCity: string;
  international?: boolean;
  options?: TransportOption[];
}

export interface BookingLink {
  provider: string;
  label: string;
  url: string;
  kind: string;
  legIndex?: number | null;
  stopSeq?: number | null;
  sponsored?: boolean;
}

export interface WebHint {
  label: string;
  sourceUrl: string;
  sourceTitle?: string | null;
  searchSuggestionsHtml?: string | null;
}

export interface ItineraryPlan {
  tripTitle: string;
  destination: string;
  origin: string | null;
  comfortLevel: ComfortLevel;
  /** v1 plans only carry INR totals; v2 plans add `currency` and `totalEstimatedCost`. */
  totalEstimatedCostInr?: number;
  totalEstimatedCost?: number | null;
  currency?: string;
  fx?: FxInfo | null;
  totalCost?: AmountRange;
  budgetStatus: BudgetStatus;
  season?: { type: Season; multiplier: number; notes: string | null };
  transportOptions?: TransportOption[];
  stay?: Stay | null;
  warnings?: string[];
  days: DayPlan[];
  stops?: StopPlan[];
  legs?: LegPlan[];
  bookingLinks?: BookingLink[];
  webHints?: WebHint[];
  webHintsSkipped?: boolean;
}
