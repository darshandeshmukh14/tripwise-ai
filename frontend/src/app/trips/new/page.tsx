"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { PlanGenerator } from "@/components/trip/plan-generator";
import { TripForm } from "@/components/trip/trip-form";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { tripsApi } from "@/lib/api/client";
import type { TripDetail } from "@/lib/api/types";
import { toTripRequest, type TripFormValues } from "@/lib/validation";

export default function NewTripPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [created, setCreated] = useState<TripDetail | null>(null);

  const create = useMutation({
    mutationFn: (v: TripFormValues) => tripsApi.create(toTripRequest(v)),
    onSuccess: async (trip) => {
      setCreated(trip);
      await queryClient.invalidateQueries({ queryKey: ["trips"] });
    },
  });

  if (created) {
    return (
      <Card className="mx-auto max-w-2xl">
        <CardHeader>
          <CardTitle>Trip to {created.destination} saved</CardTitle>
          <CardDescription>Let our planning agents build your itinerary and cost estimate.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-wrap items-start gap-3">
          <PlanGenerator trip={created} onStarted={() => router.push(`/trips/${created.id}`)} />
          <Button variant="ghost" onClick={() => router.push(`/trips/${created.id}`)}>
            Later
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="mx-auto max-w-2xl">
      <CardHeader>
        <CardTitle>Plan a new trip</CardTitle>
      </CardHeader>
      <CardContent>
        <TripForm
          submitLabel="Save trip"
          onSubmit={(v) => create.mutate(v)}
          pending={create.isPending}
          error={create.error}
        />
      </CardContent>
    </Card>
  );
}
