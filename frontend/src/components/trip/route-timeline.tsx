import { MapPin } from "lucide-react";

import { StayCard } from "@/components/trip/stay-card";
import { TransportComparison } from "@/components/trip/transport-comparison";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { LegPlan, StopPlan } from "@/lib/api/types";
import { formatDate, titleCase } from "@/lib/format";

/** Multi-city route: each stop (dates, season, stay) followed by the leg to the next stop. */
export function RouteTimeline({ stops, legs, currency }: { stops: StopPlan[]; legs: LegPlan[]; currency: string }) {
  if (!stops.length) return null;
  const ordered = [...stops].sort((a, b) => a.seq - b.seq);
  const stopNames = new Set(ordered.map((s) => s.city.toLowerCase()));
  const sortedLegs = [...legs].sort((a, b) => a.index - b.index);
  const inbound = sortedLegs.filter((l) => !stopNames.has(l.fromCity.toLowerCase()));
  const outbound = (city: string) => sortedLegs.filter((l) => l.fromCity.toLowerCase() === city.toLowerCase());
  const renderLeg = (leg: LegPlan) => <LegRow key={`leg-${leg.index}`} leg={leg} currency={currency} />;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Your route</CardTitle>
        <CardDescription>
          {stops.length} stops · {stops.reduce((n, s) => n + s.nights, 0)} nights
        </CardDescription>
      </CardHeader>
      <CardContent>
        <ol className="space-y-4">
          {inbound.map(renderLeg)}
          {ordered.map((stop) => (
            <li key={stop.seq} className="space-y-3" data-testid="route-stop">
              <div className="flex flex-wrap items-center gap-2">
                <MapPin className="h-4 w-4 text-primary" aria-hidden />
                <span className="font-semibold">
                  {stop.seq}. {stop.city}
                </span>
                <Badge variant="muted">{stop.countryCode}</Badge>
                <Badge variant="muted">{titleCase(stop.season)} season</Badge>
                <span className="text-sm text-muted-foreground">
                  {formatDate(stop.arrivalDate)} – {formatDate(stop.departureDate)} · {stop.nights} night
                  {stop.nights === 1 ? "" : "s"}
                </span>
              </div>
              {stop.stay && <StayCard stay={stop.stay} currency={currency} title={`Stay in ${stop.city}`} />}
              {outbound(stop.city).map((leg) => (
                <LegRow key={`leg-${leg.index}`} leg={leg} currency={currency} nested />
              ))}
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}

function LegRow({ leg, currency, nested }: { leg: LegPlan; currency: string; nested?: boolean }) {
  const km = leg.options?.find((o) => o.recommended)?.distanceKm ?? leg.options?.[0]?.distanceKm;
  const Tag = nested ? "div" : "li";
  return (
    <Tag className="space-y-2 border-l-2 border-dashed border-border pl-4" data-testid="route-leg">
      <p className="text-xs text-muted-foreground">
        {leg.fromCity} → {leg.toCity}
        {km != null ? ` · ${Math.round(km)} km` : ""}
        {leg.international ? " · international" : ""}
      </p>
      {!!leg.options?.length && (
        <TransportComparison
          options={leg.options}
          currency={currency}
          title={`${leg.fromCity} → ${leg.toCity}`}
          description="One-way estimates for your group."
        />
      )}
    </Tag>
  );
}
