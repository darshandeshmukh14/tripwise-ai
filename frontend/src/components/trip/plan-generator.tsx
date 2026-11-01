"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Sparkles } from "lucide-react";
import { useState } from "react";

import { ClarifyForm } from "@/components/trip/clarify-form";
import { Alert } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { errorMessage, tripsApi } from "@/lib/api/client";
import type { ClarifyQuestion, TripDetail } from "@/lib/api/types";

/** Clarify → (optional answers) → generate. Answers (including `budget`) are stored on the trip by the backend. */
export function PlanGenerator({
  trip,
  label = "Plan my trip",
  onStarted,
}: {
  trip: TripDetail;
  label?: string;
  onStarted?: () => void;
}) {
  const queryClient = useQueryClient();
  const [questions, setQuestions] = useState<ClarifyQuestion[] | null>(null);

  const generate = useMutation({
    mutationFn: (answers: Record<string, string>) => tripsApi.generate(trip.id, answers),
    onSuccess: async () => {
      setQuestions(null);
      await queryClient.invalidateQueries({ queryKey: ["trip", trip.id] });
      await queryClient.invalidateQueries({ queryKey: ["trips"] });
      onStarted?.();
    },
  });

  const clarify = useMutation({
    mutationFn: () => tripsApi.clarify(trip.id),
    onSuccess: ({ questions: qs }) => (qs.length ? setQuestions(qs) : generate.mutate({})),
  });

  const error = clarify.error ?? generate.error;
  return (
    <div className="space-y-4">
      {error && <Alert variant="destructive">{errorMessage(error)}</Alert>}
      {questions ? (
        <div className="rounded-lg border border-border p-4">
          <h3 className="mb-4 font-semibold">A few quick questions</h3>
          <ClarifyForm questions={questions} onSubmit={(a) => generate.mutate(a)} submitting={generate.isPending} />
        </div>
      ) : (
        <Button onClick={() => clarify.mutate()} disabled={clarify.isPending || generate.isPending}>
          <Sparkles className="h-4 w-4" aria-hidden />
          {clarify.isPending || generate.isPending ? "Checking your trip…" : label}
        </Button>
      )}
    </div>
  );
}
