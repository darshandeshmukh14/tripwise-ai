import { BedDouble } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { ItineraryPlan } from "@/lib/api/types";
import { formatMoney, formatRange, titleCase } from "@/lib/format";

export function StayCard({
  stay,
  currency = "INR",
  title = "Where you'll stay",
}: {
  stay: NonNullable<ItineraryPlan["stay"]>;
  currency?: string;
  title?: string;
}) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <BedDouble className="h-5 w-5 text-primary" aria-hidden /> {title}
        </CardTitle>
      </CardHeader>
      <CardContent className="space-y-1 text-sm">
        <p className="font-medium">{titleCase(stay.tier)} hotel</p>
        <p>
          {formatMoney(stay.nightlyRate.expected, currency)} per room / night{" "}
          <span className="text-muted-foreground">
            ({formatRange(stay.nightlyRate.min, stay.nightlyRate.max, currency)})
          </span>
        </p>
        <p className="text-muted-foreground">
          {stay.nights} night{stay.nights === 1 ? "" : "s"} · {stay.rooms} room{stay.rooms === 1 ? "" : "s"}
        </p>
        {stay.areaSuggestion && <p>Suggested area: {stay.areaSuggestion}</p>}
      </CardContent>
    </Card>
  );
}
