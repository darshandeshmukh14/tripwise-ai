import { Suspense } from "react";

import { TripDetailView } from "@/components/trip/trip-detail-view";

export default function TripDetailPage() {
  return (
    <Suspense fallback={<p className="text-muted-foreground">Loading trip…</p>}>
      <TripDetailView />
    </Suspense>
  );
}
