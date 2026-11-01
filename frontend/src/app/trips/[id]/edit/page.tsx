import { Suspense } from "react";

import { TripEditView } from "@/components/trip/trip-edit-view";

export default function EditTripPage() {
  return (
    <Suspense fallback={<p className="text-muted-foreground">Loading trip…</p>}>
      <TripEditView />
    </Suspense>
  );
}
