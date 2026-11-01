import { AlertTriangle, CalendarRange } from "lucide-react";

import { BudgetBadge } from "@/components/trip/status-badge";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { Itinerary } from "@/lib/api/types";
import { formatDate, formatMoney, formatRange, titleCase } from "@/lib/format";

export function CostSummary({
  itinerary,
  budget,
  budgetCurrency,
}: {
  itinerary: Itinerary;
  budget: number | null;
  budgetCurrency: string;
}) {
  const { plan, costBreakdown } = itinerary;
  const currency = itinerary.currency || plan.currency || "INR";
  const total = itinerary.totalEstimatedCost ?? plan.totalEstimatedCost ?? plan.totalEstimatedCostInr ?? 0;
  const min = costBreakdown?.totalMin ?? plan.totalCost?.min ?? total;
  const max = costBreakdown?.totalMax ?? plan.totalCost?.max ?? total;
  const expected = costBreakdown?.totalExpected ?? total;
  const fxRate = itinerary.fxRate ?? plan.fx?.rate;
  const fxAsOf = itinerary.fxAsOf ?? plan.fx?.asOf;

  return (
    <Card>
      <CardHeader>
        <div className="flex flex-wrap items-center gap-2">
          <CardTitle>{plan.tripTitle}</CardTitle>
          <BudgetBadge status={costBreakdown?.budgetStatus ?? plan.budgetStatus} />
          <Badge variant="muted">v{itinerary.version}</Badge>
        </div>
        <CardDescription>
          {plan.origin ? `${plan.origin} → ` : ""}
          {plan.destination} · {titleCase(plan.comfortLevel)} comfort
        </CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="grid gap-4 sm:grid-cols-3">
          <div>
            <p className="text-sm text-muted-foreground">Estimated total</p>
            <p className="text-2xl font-bold">{formatMoney(expected, currency)}</p>
            <p className="text-xs text-muted-foreground">Range {formatRange(min, max, currency)}</p>
          </div>
          <div>
            <p className="text-sm text-muted-foreground">Your budget</p>
            <p className="text-2xl font-bold">{formatMoney(budget, budgetCurrency)}</p>
          </div>
          {plan.season && (
            <div>
              <p className="flex items-center gap-1 text-sm text-muted-foreground">
                <CalendarRange className="h-4 w-4" aria-hidden /> Season
              </p>
              <p className="text-lg font-semibold">
                {titleCase(plan.season.type)} season{" "}
                <span className="text-sm font-normal text-muted-foreground">×{plan.season.multiplier}</span>
              </p>
              {plan.season.notes && <p className="text-xs text-muted-foreground">{plan.season.notes}</p>}
            </div>
          )}
        </div>
        {!!plan.warnings?.length && (
          <ul className="space-y-1 rounded-md border border-amber-300/60 bg-amber-50 p-3 text-sm text-amber-900 dark:bg-amber-950/30 dark:text-amber-200">
            {plan.warnings.map((w) => (
              <li key={w} className="flex gap-2">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" aria-hidden /> {w}
              </li>
            ))}
          </ul>
        )}
        <p className="text-xs text-muted-foreground">Estimates only — not live prices or bookings.</p>
        {currency !== "INR" && fxRate != null && (
          <p className="text-xs text-muted-foreground" data-testid="fx-note">
            Priced in INR and converted at 1 INR = {Number(fxRate.toPrecision(4))} {currency}
            {fxAsOf ? `, rates as of ${formatDate(fxAsOf.slice(0, 10))}` : ""}.{" "}
            {plan.fx?.source === "open.er-api.com" ? (
              <a href="https://www.exchangerate-api.com" target="_blank" rel="noopener noreferrer" className="underline">
                Rates by Exchange Rate API
              </a>
            ) : (
              "Approximate offline rates."
            )}
          </p>
        )}
        {plan.webHintsSkipped && (
          <p className="text-xs text-muted-foreground">
            Live web price hints were skipped for this plan (daily limit reached), so prices are estimates only.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
