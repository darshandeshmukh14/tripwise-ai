"use client";

import { useQuery } from "@tanstack/react-query";
import { MapPin, Plus } from "lucide-react";
import Link from "next/link";
import { useState } from "react";

import { StatusBadge } from "@/components/trip/status-badge";
import { Alert } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { errorMessage, tripsApi } from "@/lib/api/client";
import { formatDate, formatMoney } from "@/lib/format";

export default function TripsPage() {
  const [page, setPage] = useState(0);
  const trips = useQuery({ queryKey: ["trips", page], queryFn: () => tripsApi.list(page), placeholderData: (prev) => prev });
  const data = trips.data;

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">Your trips</h1>
        <Link
          href="/trips/new"
          className="inline-flex h-10 items-center gap-2 rounded-md bg-primary px-4 text-sm font-medium text-primary-foreground hover:bg-primary/90"
        >
          <Plus className="h-4 w-4" aria-hidden /> New trip
        </Link>
      </div>

      {trips.isLoading && <p className="text-muted-foreground">Loading trips…</p>}
      {trips.error && <Alert variant="destructive">{errorMessage(trips.error)}</Alert>}
      {data && data.content.length === 0 && (
        <Card>
          <CardContent className="py-12 text-center text-muted-foreground">
            No trips yet.{" "}
            <Link href="/trips/new" className="font-medium text-primary hover:underline">
              Plan your first one
            </Link>
            .
          </CardContent>
        </Card>
      )}

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {data?.content.map((t) => (
          <Link key={t.id} href={`/trips/${t.id}`} className="rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring">
            <Card className="h-full transition-colors hover:border-primary">
              <CardHeader>
                <div className="flex items-start justify-between gap-2">
                  <CardTitle className="flex items-center gap-1 text-base">
                    <MapPin className="h-4 w-4 text-primary" aria-hidden /> {t.destination}
                  </CardTitle>
                  <StatusBadge status={t.status} />
                </div>
              </CardHeader>
              <CardContent className="space-y-1 text-sm text-muted-foreground">
                <p>
                  {formatDate(t.startDate)} – {formatDate(t.endDate)}
                </p>
                <p>Budget {formatMoney(t.budgetAmount, t.currency)}</p>
              </CardContent>
            </Card>
          </Link>
        ))}
      </div>

      {data && data.totalPages > 1 && (
        <nav className="flex items-center justify-center gap-3" aria-label="Pagination">
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage((p) => p - 1)}>
            Previous
          </Button>
          <span className="text-sm text-muted-foreground">
            Page {data.page + 1} of {data.totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>
            Next
          </Button>
        </nav>
      )}
    </div>
  );
}
