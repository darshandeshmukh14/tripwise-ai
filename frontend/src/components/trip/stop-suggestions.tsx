"use client";

import { useMutation } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import Link from "next/link";

import { Alert, Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { errorMessage, tripsApi } from "@/lib/api/client";
import { formatRange, titleCase } from "@/lib/format";

/** "What else can I club in?": nearby places (≤400 km from a stop) with extra cost, loaded on demand. */
export function StopSuggestions({ tripId, disabled }: { tripId: number; disabled?: boolean }) {
  const suggest = useMutation({ mutationFn: () => tripsApi.suggestStops(tripId) });
  const items = suggest.data?.suggestions;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Add nearby places</CardTitle>
        <CardDescription>Places within 400 km by road that pair well with this trip.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {!items && (
          <Button variant="outline" size="sm" disabled={disabled || suggest.isPending} onClick={() => suggest.mutate()}>
            <Sparkles className="h-4 w-4" aria-hidden /> {suggest.isPending ? "Finding places…" : "Suggest places"}
          </Button>
        )}
        {suggest.error && <Alert variant="destructive">{errorMessage(suggest.error)}</Alert>}
        {items && !items.length && (
          <p className="text-sm text-muted-foreground">No nearby places to add (a trip can have at most 6 stops).</p>
        )}
        {!!items?.length && (
          <>
            <ul className="space-y-3">
              {items.map((s) => (
                <li key={`${s.city}-${s.countryCode}`} className="rounded-md border border-border p-3 text-sm" data-testid="stop-suggestion">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-semibold">{s.city}</span>
                    <Badge variant="muted">{s.countryCode}</Badge>
                    <Badge variant="muted">{titleCase(s.season)} season</Badge>
                  </div>
                  <p className="text-muted-foreground">
                    {Math.round(s.distanceKm)} km from {s.nearStop} · ~{s.travelHours}h · {s.suggestedNights} night
                    {s.suggestedNights === 1 ? "" : "s"} · +{formatRange(s.extraCostMin, s.extraCostMax, s.currency)}
                  </p>
                  {s.reason && <p>{s.reason}</p>}
                </li>
              ))}
            </ul>
            <Link href={`/trips/${tripId}/edit`} className="text-sm text-primary underline">
              Edit trip to add a stop
            </Link>
          </>
        )}
      </CardContent>
    </Card>
  );
}
