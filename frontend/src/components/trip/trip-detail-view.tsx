"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ArrowLeft, Loader2, Pencil, Trash2 } from "lucide-react";
import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { useEffect } from "react";

import { BookingLinks } from "@/components/trip/booking-links";
import { ChatPanel } from "@/components/trip/chat-panel";
import { CostBreakdownTable } from "@/components/trip/cost-breakdown-table";
import { CostSummary } from "@/components/trip/cost-summary";
import { DayByDay } from "@/components/trip/day-by-day";
import { PlanGenerator } from "@/components/trip/plan-generator";
import { RouteTimeline } from "@/components/trip/route-timeline";
import { StayCard } from "@/components/trip/stay-card";
import { StatusBadge } from "@/components/trip/status-badge";
import { StopSuggestions } from "@/components/trip/stop-suggestions";
import { TransportComparison } from "@/components/trip/transport-comparison";
import { WebHints } from "@/components/trip/web-hints";
import { Alert } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ApiError, errorMessage, tripsApi } from "@/lib/api/client";
import { formatDate, titleCase } from "@/lib/format";

const POLL_MS = 2_000;

export function TripDetailView() {
  const params = useParams<{ id: string }>();
  const id = Number(params.id);
  const validId = Number.isSafeInteger(id) && id > 0;
  const router = useRouter();
  const queryClient = useQueryClient();

  const trip = useQuery({ queryKey: ["trip", id], queryFn: () => tripsApi.get(id), enabled: validId });
  const generating = trip.data?.status === "GENERATING";

  const status = useQuery({
    queryKey: ["trip", id, "status"],
    queryFn: () => tripsApi.status(id),
    enabled: validId && generating,
    refetchInterval: POLL_MS,
  });

  useEffect(() => {
    if (generating && status.data && status.data.status !== "GENERATING") {
      void queryClient.invalidateQueries({ queryKey: ["trip", id], exact: true });
      void queryClient.invalidateQueries({ queryKey: ["trips"] });
    }
  }, [generating, status.data, id, queryClient]);

  const remove = useMutation({
    mutationFn: () => tripsApi.remove(id),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ["trip", id] });
      await queryClient.invalidateQueries({ queryKey: ["trips"] });
      router.push("/trips");
    },
  });

  if (!validId || (trip.error instanceof ApiError && trip.error.status === 404)) {
    return <NotFound />;
  }
  if (trip.isLoading) return <p className="text-muted-foreground">Loading trip…</p>;
  if (trip.error || !trip.data) return <Alert variant="destructive">{errorMessage(trip.error)}</Alert>;

  const t = trip.data;
  const itinerary = t.itinerary;
  // Editing a trip resets it to DRAFT but keeps the last itinerary.
  const stale = !!itinerary && t.status === "DRAFT";
  const plan = itinerary?.plan;
  const currency = itinerary?.currency || plan?.currency || "INR";
  const planStops = plan?.stops ?? [];
  const stopNames = Object.fromEntries(planStops.map((s) => [s.seq, s.city]));
  const sortedStops = [...t.stops].sort((a, b) => a.seq - b.seq);
  return (
    <div className="space-y-6">
      <Link href="/trips" className="inline-flex items-center gap-1 text-sm text-muted-foreground hover:text-foreground">
        <ArrowLeft className="h-4 w-4" aria-hidden /> All trips
      </Link>

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-2xl font-bold">{t.destination}</h1>
            <StatusBadge status={t.status} />
          </div>
          <p className="text-sm text-muted-foreground">
            {t.origin ? `From ${t.origin} · ` : ""}
            {formatDate(t.startDate)} – {formatDate(t.endDate)} · {t.travellersCount} traveller
            {t.travellersCount === 1 ? "" : "s"}
            {t.comfortLevel ? ` · ${titleCase(t.comfortLevel)}` : ""}
            {t.currency && t.currency !== "INR" ? ` · ${t.currency}` : ""}
          </p>
          {sortedStops.length > 0 && (
            <p className="text-sm" data-testid="trip-stops">
              {sortedStops.map((s) => `${s.city} (${s.nights}n)`).join(" → ")}
            </p>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" disabled={generating} onClick={() => router.push(`/trips/${id}/edit`)}>
            <Pencil className="h-4 w-4" aria-hidden /> Edit
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={generating || remove.isPending}
            onClick={() => {
              if (window.confirm("Delete this trip and its itinerary?")) remove.mutate();
            }}
          >
            <Trash2 className="h-4 w-4" aria-hidden /> Delete
          </Button>
        </div>
      </div>
      {remove.error && <Alert variant="destructive">{errorMessage(remove.error)}</Alert>}

      {stale && (
        <Alert>
          <p className="mb-3">Your trip details changed after this plan was made, so its costs and days may be outdated.</p>
          <PlanGenerator trip={t} label="Update plan" />
        </Alert>
      )}

      {generating && (
        <Card>
          <CardContent className="flex items-center gap-3 pt-5" role="status">
            <Loader2 className="h-5 w-5 animate-spin text-primary" aria-hidden />
            Our agents are comparing transport, pricing stays and drafting your days…
          </CardContent>
        </Card>
      )}

      {t.status === "FAILED" && (
        <Alert variant="destructive">
          <p className="mb-3">We couldn&apos;t build this itinerary. The planning service may be busy — please try again.</p>
          <PlanGenerator trip={t} label="Try again" />
        </Alert>
      )}

      {t.status === "DRAFT" && !itinerary && (
        <Card>
          <CardContent className="space-y-3 pt-5">
            <p className="text-muted-foreground">No itinerary yet.</p>
            <PlanGenerator trip={t} />
          </CardContent>
        </Card>
      )}

      {itinerary && plan && (
        <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
          <div className="space-y-6">
            <CostSummary itinerary={itinerary} budget={t.budgetAmount} budgetCurrency={t.currency || "INR"} />
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
            {t.status === "COMPLETED" && <StopSuggestions tripId={t.id} />}
            {t.status === "COMPLETED" && (
              <div className="rounded-lg border border-dashed border-border p-4">
                <p className="mb-3 text-sm text-muted-foreground">Changed your trip details? Rebuild the plan from scratch.</p>
                <PlanGenerator trip={t} label="Regenerate itinerary" />
              </div>
            )}
          </div>
          <div className="lg:sticky lg:top-6 lg:self-start">
            {t.status === "COMPLETED" ? (
              <ChatPanel tripId={t.id} itineraryVersion={itinerary.version} />
            ) : (
              <p className="text-sm text-muted-foreground">
                {stale ? "Update the plan to keep refining it with chat." : "Chat is available once the itinerary is ready."}
              </p>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function NotFound() {
  return (
    <div className="space-y-3 py-12 text-center">
      <h1 className="text-xl font-semibold">Trip not found</h1>
      <Link href="/trips" className="text-primary hover:underline">
        Back to your trips
      </Link>
    </div>
  );
}
