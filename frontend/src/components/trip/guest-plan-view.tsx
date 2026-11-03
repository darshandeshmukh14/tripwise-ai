"use client";

import { useMutation } from "@tanstack/react-query";
import { Loader2, RotateCcw, Save, Send } from "lucide-react";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { ClarifyForm } from "@/components/trip/clarify-form";
import { ItinerarySections } from "@/components/trip/itinerary-sections";
import { StopSuggestions } from "@/components/trip/stop-suggestions";
import { TripForm } from "@/components/trip/trip-form";
import { Alert } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/form-controls";
import { ApiError, errorMessage, guestApi } from "@/lib/api/client";
import type { ClarifyQuestion } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import {
  claimPendingGuestPlan,
  clearGuestPlan,
  GUEST_HISTORY_LIMIT,
  type GuestPlan,
  loadGuestPlan,
  saveGuestPlan,
} from "@/lib/guest-plan";
import { cn } from "@/lib/utils";
import { emptyTripForm, toTripRequest, type TripFormValues } from "@/lib/validation";

const MAX_LENGTH = 2000;

/** Plan a trip without an account. Nothing is stored on the server; the user can save it by signing up/in. */
export function GuestPlanView() {
  const router = useRouter();
  const [plan, setPlan] = useState<GuestPlan | null>(null);
  const [form, setForm] = useState<TripFormValues | null>(null);
  const [questions, setQuestions] = useState<ClarifyQuestion[] | null>(null);
  const [ready, setReady] = useState(false);

  // sessionStorage only exists in the browser, so restore after hydration.
  useEffect(() => {
    const stored = loadGuestPlan();
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setPlan(stored);
    setForm(stored?.form ?? null);
    setReady(true);
  }, []);

  function update(next: GuestPlan | null) {
    setPlan(next);
    if (next) saveGuestPlan(next);
    else clearGuestPlan();
  }

  const generate = useMutation({
    mutationFn: ({ values, answers }: { values: TripFormValues; answers: Record<string, string> }) =>
      guestApi.generate(toTripRequest(values), answers),
    onSuccess: (trip, { values, answers }) => {
      setQuestions(null);
      update({ form: values, answers, trip, messages: [] });
    },
  });

  const clarify = useMutation({
    mutationFn: (values: TripFormValues) => guestApi.clarify(toTripRequest(values)),
    onSuccess: ({ questions: qs }, values) => (qs.length ? setQuestions(qs) : generate.mutate({ values, answers: {} })),
  });

  const save = useMutation({
    mutationFn: async () => {
      if (plan) update({ ...plan, saveRequested: true });
      return claimPendingGuestPlan();
    },
    onSuccess: (id) => {
      if (id) router.push(`/trips/${id}`);
    },
    onError: (error) => {
      // Not signed in: the sign-up/sign-in page saves the plan right after authenticating.
      if (error instanceof ApiError && error.status === 401) router.push("/register");
    },
  });

  if (!ready) return <p className="text-muted-foreground">Loading?</p>;

  const busy = clarify.isPending || generate.isPending;
  const trip = plan?.trip;

  if (!plan || !trip) {
    return (
      <Card className="mx-auto max-w-2xl">
        <CardHeader>
          <CardTitle>Plan a trip</CardTitle>
          <CardDescription>
            No account needed. Your plan isn&apos;t saved ? sign up any time to keep it in your trip history.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {(clarify.error || generate.error) && (
            <Alert variant="destructive">{errorMessage(clarify.error ?? generate.error)}</Alert>
          )}
          {generate.isPending ? (
            <p className="flex items-center gap-3" role="status">
              <Loader2 className="h-5 w-5 animate-spin text-primary" aria-hidden />
              Our agents are comparing transport, pricing stays and drafting your days?
            </p>
          ) : questions && form ? (
            <div className="rounded-lg border border-border p-4">
              <h3 className="mb-4 font-semibold">A few quick questions</h3>
              <ClarifyForm
                questions={questions}
                onSubmit={(answers) => generate.mutate({ values: form, answers })}
                submitting={generate.isPending}
              />
            </div>
          ) : (
            <TripForm
              initialValues={form ?? emptyTripForm}
              submitLabel="Plan my trip"
              pendingLabel="Checking your trip?"
              onSubmit={(values) => {
                setForm(values);
                clarify.mutate(values);
              }}
              pending={busy}
              error={null}
            />
          )}
        </CardContent>
      </Card>
    );
  }

  const request = toTripRequest(plan.form);
  const sortedStops = [...trip.stops].sort((a, b) => a.seq - b.seq);
  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">{trip.destination}</h1>
          <p className="text-sm text-muted-foreground">
            {trip.origin ? `From ${trip.origin} ? ` : ""}
            {formatDate(trip.startDate)} ? {formatDate(trip.endDate)} ? {trip.travellersCount} traveller
            {trip.travellersCount === 1 ? "" : "s"}
          </p>
          {sortedStops.length > 0 && (
            <p className="text-sm">{sortedStops.map((s) => `${s.city} (${s.nights}n)`).join(" ? ")}</p>
          )}
        </div>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => update({ ...plan, trip: null, messages: [] })}>
            Edit details
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              if (window.confirm("Discard this plan and start over?")) {
                update(null);
                setForm(null);
              }
            }}
          >
            <RotateCcw className="h-4 w-4" aria-hidden /> Start over
          </Button>
        </div>
      </div>

      <Alert>
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p>This plan isn&apos;t saved and disappears when you close this tab.</p>
          <div className="flex gap-2">
            <Button size="sm" onClick={() => save.mutate()} disabled={save.isPending}>
              <Save className="h-4 w-4" aria-hidden /> {save.isPending ? "Saving?" : "Save to my trips"}
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => {
                update({ ...plan, saveRequested: true });
                router.push("/login");
              }}
            >
              I have an account
            </Button>
          </div>
        </div>
      </Alert>
      {save.error && !(save.error instanceof ApiError && save.error.status === 401) && (
        <Alert variant="destructive">{errorMessage(save.error)}</Alert>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_380px]">
        <div className="space-y-6">
          <ItinerarySections itinerary={trip.itinerary} budget={trip.budgetAmount} budgetCurrency={trip.currency || "INR"} />
          <StopSuggestions load={() => guestApi.suggestStops(request, plan.answers)} />
        </div>
        <div className="lg:sticky lg:top-6 lg:self-start">
          <GuestChat plan={plan} onChange={update} />
        </div>
      </div>
    </div>
  );
}

const SUGGESTIONS = ["Make it cheaper", "Go by train instead", "Make the days more relaxed"];

/** Chat refinement without an account: the conversation is kept in the browser and sent with each message. */
function GuestChat({ plan, onChange }: { plan: GuestPlan; onChange: (next: GuestPlan) => void }) {
  const [draft, setDraft] = useState("");
  const send = useMutation({
    mutationFn: (content: string) =>
      guestApi.sendMessage({
        trip: toTripRequest(plan.form),
        answers: plan.answers,
        plan: plan.trip!.itinerary.plan,
        history: plan.messages.slice(-GUEST_HISTORY_LIMIT),
        content,
      }),
    onSuccess: (reply, content) => {
      setDraft("");
      onChange({
        ...plan,
        trip: reply.trip,
        messages: [...plan.messages, { role: "USER" as const, content }, { role: "ASSISTANT" as const, content: reply.assistantMessage }]
          .slice(-GUEST_HISTORY_LIMIT * 2),
      });
    },
  });

  function submit(content: string) {
    const text = content.trim();
    if (text && !send.isPending) send.mutate(text);
  }

  return (
    <Card className="flex flex-col">
      <CardHeader>
        <CardTitle>Refine with chat</CardTitle>
        <CardDescription>Ask for changes ? costs are re-estimated automatically.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-3">
        <div className="max-h-96 min-h-24 space-y-2 overflow-y-auto" aria-live="polite">
          {plan.messages.length === 0 && <p className="text-sm text-muted-foreground">No messages yet. Try a suggestion below.</p>}
          {plan.messages.map((m, i) => (
            <div
              key={i}
              className={cn(
                "max-w-[85%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm",
                m.role === "USER" ? "ml-auto bg-primary text-primary-foreground" : "bg-muted",
              )}
            >
              {m.content}
            </div>
          ))}
          {send.isPending && <p className="text-sm text-muted-foreground">Thinking?</p>}
        </div>
        {send.error && <Alert variant="destructive">{errorMessage(send.error)}</Alert>}
        <div className="flex flex-wrap gap-2">
          {SUGGESTIONS.map((s) => (
            <Button key={s} variant="outline" size="sm" disabled={send.isPending} onClick={() => submit(s)}>
              {s}
            </Button>
          ))}
        </div>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            submit(draft);
          }}
        >
          <Textarea
            aria-label="Message"
            className="min-h-10 flex-1"
            rows={2}
            maxLength={MAX_LENGTH}
            value={draft}
            placeholder="e.g. Add a beach day and skip the museum"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                submit(draft);
              }
            }}
          />
          <Button type="submit" size="icon" aria-label="Send" disabled={send.isPending || !draft.trim()}>
            <Send className="h-4 w-4" aria-hidden />
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
