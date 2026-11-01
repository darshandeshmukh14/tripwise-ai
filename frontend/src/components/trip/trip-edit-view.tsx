"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";

import { TripForm } from "@/components/trip/trip-form";
import { Alert } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { ApiError, errorMessage, tripsApi } from "@/lib/api/client";
import { fromTrip, toTripRequest, type TripFormValues } from "@/lib/validation";

export function TripEditView() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const validId = Number.isSafeInteger(id) && id > 0;
  const router = useRouter();
  const queryClient = useQueryClient();
  const trip = useQuery({ queryKey: ["trip", id], queryFn: () => tripsApi.get(id), enabled: validId });

  const update = useMutation({
    mutationFn: (v: TripFormValues) => tripsApi.update(id, toTripRequest(v)),
    onSuccess: async (saved) => {
      queryClient.setQueryData(["trip", id], saved);
      await queryClient.invalidateQueries({ queryKey: ["trips"] });
      router.push(`/trips/${id}`);
    },
  });

  const back = (
    <Link href={`/trips/${id}`} className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
      <ArrowLeft className="h-4 w-4" aria-hidden /> Back to trip
    </Link>
  );

  if (!validId || (trip.error instanceof ApiError && trip.error.status === 404)) {
    return <Alert variant="destructive">Trip not found.</Alert>;
  }
  if (trip.isLoading) return <p className="text-muted-foreground">Loading trip…</p>;
  if (trip.error || !trip.data) return <Alert variant="destructive">{errorMessage(trip.error)}</Alert>;
  if (trip.data.status === "GENERATING") {
    return (
      <div className="space-y-4">
        {back}
        <Alert>This trip is being planned right now. You can edit it once planning finishes.</Alert>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      {back}
      <Card>
        <CardHeader>
          <CardTitle>Edit trip to {trip.data.destination}</CardTitle>
          {trip.data.itinerary && (
            <CardDescription>Saving changes keeps your current plan but marks it as outdated until you regenerate.</CardDescription>
          )}
        </CardHeader>
        <CardContent>
          <TripForm
            initialValues={fromTrip(trip.data)}
            submitLabel="Save changes"
            onSubmit={(v) => update.mutate(v)}
            pending={update.isPending}
            error={update.error}
          />
        </CardContent>
      </Card>
    </div>
  );
}
