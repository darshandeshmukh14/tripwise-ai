import { Bus, Car, Plane, Star, TrainFront } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { TransportMode, TransportOption } from "@/lib/api/types";
import { formatHours, formatMoney, formatRange, titleCase } from "@/lib/format";
import { cn } from "@/lib/utils";

const ICONS: Record<TransportMode, typeof Plane> = { FLIGHT: Plane, TRAIN: TrainFront, BUS: Bus, CAR: Car };

export function TransportComparison({
  options,
  currency = "INR",
  title = "Getting there",
  description = "Return-trip estimates for your group, compared side by side.",
}: {
  options: TransportOption[];
  currency?: string;
  title?: string;
  description?: string;
}) {
  if (!options.length) return null;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{title}</CardTitle>
        <CardDescription>{description}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {options.map((o) => {
          const Icon = ICONS[o.mode];
          return (
            <div
              key={`${o.mode}-${o.travelClass}`}
              data-testid={`transport-${o.mode}`}
              className={cn("space-y-2 rounded-lg border p-4", o.recommended ? "border-primary ring-1 ring-primary" : "border-border")}
            >
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-2 font-semibold">
                  <Icon className="h-4 w-4" aria-hidden /> {titleCase(o.mode)}
                </span>
                {o.recommended && <Badge>Recommended</Badge>}
              </div>
              <p className="text-sm text-muted-foreground">{o.travelClass}</p>
              <p className="text-xl font-bold">{formatMoney(o.cost.expected, currency)}</p>
              <p className="text-xs text-muted-foreground">{formatRange(o.cost.min, o.cost.max, currency)}</p>
              <dl className="grid grid-cols-2 gap-1 text-xs">
                <dt className="text-muted-foreground">Duration</dt>
                <dd>{formatHours(o.durationHours)}</dd>
                <dt className="text-muted-foreground">Distance</dt>
                <dd>{Math.round(o.distanceKm)} km</dd>
                <dt className="text-muted-foreground">Comfort</dt>
                <dd className="flex" aria-label={`${o.comfortScore} out of 5`}>
                  {Array.from({ length: 5 }, (_, i) => (
                    <Star
                      key={i}
                      aria-hidden
                      className={cn("h-3 w-3", i < o.comfortScore ? "fill-amber-400 text-amber-400" : "text-muted-foreground/40")}
                    />
                  ))}
                </dd>
              </dl>
              {o.notes && <p className="text-xs text-muted-foreground">{o.notes}</p>}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}
