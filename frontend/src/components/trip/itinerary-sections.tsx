import { BookingLinks } from "@/components/trip/booking-links";
import { CostBreakdownTable } from "@/components/trip/cost-breakdown-table";
import { CostSummary } from "@/components/trip/cost-summary";
import { DayByDay } from "@/components/trip/day-by-day";
import { RouteTimeline } from "@/components/trip/route-timeline";
import { StayCard } from "@/components/trip/stay-card";
import { TransportComparison } from "@/components/trip/transport-comparison";
import { WebHints } from "@/components/trip/web-hints";
import type { Itinerary } from "@/lib/api/types";

/** The generated plan (costs, route, days, links), shared by saved trips and plans made without an account. */
export function ItinerarySections({
  itinerary,
  budget,
  budgetCurrency,
}: {
  itinerary: Itinerary;
  budget: number | null;
  budgetCurrency: string;
}) {
  const plan = itinerary.plan;
  const currency = itinerary.currency || plan.currency || "INR";
  const planStops = plan.stops ?? [];
  const stopNames = Object.fromEntries(planStops.map((s) => [s.seq, s.city]));
  return (
    <>
      <CostSummary itinerary={itinerary} budget={budget} budgetCurrency={budgetCurrency} />
      {planStops.length > 0 ? (
        <RouteTimeline stops={planStops} legs={plan.legs ?? []} currency={currency} />
      ) : (
        <>
          {!!plan.transportOptions?.length && <TransportComparison options={plan.transportOptions} currency={currency} />}
          {plan.stay && <StayCard stay={plan.stay} currency={currency} />}
        </>
      )}
      <DayByDay days={plan.days} />
      {itinerary.costBreakdown && <CostBreakdownTable breakdown={itinerary.costBreakdown} stopNames={stopNames} />}
      {!!plan.bookingLinks?.length && <BookingLinks links={plan.bookingLinks} />}
      {!!plan.webHints?.length && <WebHints hints={plan.webHints} />}
    </>
  );
}
