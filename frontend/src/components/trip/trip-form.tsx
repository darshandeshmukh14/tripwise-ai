"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Plus, Trash2 } from "lucide-react";
import { useEffect, useState, useSyncExternalStore } from "react";
import { Controller, useFieldArray, useForm, useWatch } from "react-hook-form";

import { PlaceAutocomplete } from "@/components/trip/place-autocomplete";
import { Alert } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { FieldError, Input, Label, Select } from "@/components/ui/form-controls";
import { ApiError, errorMessage, guestApi } from "@/lib/api/client";
import {
  COMFORT_OPTIONS,
  CURRENCY_OPTIONS,
  formatDate,
  formatMoney,
  todayIso,
  TRANSPORT_OPTIONS,
  tripDays,
} from "@/lib/format";
import { countryName, type PlaceSuggestion } from "@/lib/places";
import { cn } from "@/lib/utils";
import { emptyTripForm, MAX_STOPS, toTripRequest, tripSchema, type TripFormValues } from "@/lib/validation";

const STEPS = ["Where & when", "Budget & style", "Review"] as const;
const STEP_FIELDS: (keyof TripFormValues)[][] = [
  ["destination", "origin", "startDate", "endDate", "travellersCount", "stops"],
  ["budgetAmount", "currency", "comfortLevel", "transportModes", "travelStyle", "preferences"],
];
/** Backend class-level validation keys (@AssertTrue) mapped to the form field they describe. */
const SERVER_FIELD_ALIASES: Record<string, keyof TripFormValues> = {
  currencySupported: "currency",
  stopsConsistent: "stops",
  endDateOnOrAfterStartDate: "endDate",
  durationWithinLimit: "endDate",
};
const PACES = ["relaxed", "balanced", "packed"];
const INTERESTS = ["Beaches", "Mountains", "Heritage & history", "Food", "Nightlife", "Adventure sports", "Wildlife", "Shopping"];

const chip = (selected: boolean) =>
  cn(
    "rounded-full border px-3 py-1 text-sm transition-colors",
    selected ? "border-primary bg-primary text-primary-foreground" : "border-border hover:bg-muted",
  );

// "Today" depends on the visitor's clock: read it on the client only (empty during prerender).
const noSubscribe = () => () => {};
const useToday = () => useSyncExternalStore(noSubscribe, todayIso, () => "");

/**
 * Most popular places within a day's drive of the last chosen stop (public, nothing stored), debounced.
 * Planned and origin cities are excluded.
 */
function useNearbyPlaces(values: TripFormValues, enabled: boolean): PlaceSuggestion[] {
  const filled = values.stops.map((s) => s.city.trim()).filter(Boolean);
  const anchor = filled.at(-1) ?? values.destination.trim();
  const datesOk = !!values.startDate && !!values.endDate && values.endDate >= values.startDate;
  const key = enabled && anchor && datesOk ? [anchor, values.origin, values.startDate, values.endDate].join("|") : "";
  const [result, setResult] = useState<{ key: string; places: PlaceSuggestion[] }>({ key: "", places: [] });

  useEffect(() => {
    if (!key) return;
    let cancelled = false;
    const timer = setTimeout(() => {
      const trip = {
        ...toTripRequest({ ...values, destination: anchor, stops: [] }),
        budgetAmount: undefined,
        travellersCount: Number(values.travellersCount) || 1,
      };
      guestApi
        .suggestStops(trip, {})
        .then((r) => {
          if (cancelled) return;
          setResult({
            key,
            places: r.suggestions.map((s) => ({
              city: s.city,
              countryCode: s.countryCode,
              country: countryName(s.countryCode),
              hint: `${s.distanceKm} km from ${s.nearStop}`,
            })),
          });
        })
        .catch(() => {});
    }, 600);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
    // Only refetch when the anchor city or dates change, not on every keystroke elsewhere.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  if (result.key !== key) return [];
  const taken = new Set([...filled, values.origin.trim()].map((c) => c.toLowerCase()));
  return result.places.filter((p) => !taken.has(p.city.toLowerCase()));
}

/** Three-step trip wizard shared by the create and edit pages. */
export function TripForm({
  initialValues = emptyTripForm,
  submitLabel,
  pendingLabel = "Saving…",
  onSubmit,
  pending,
  error,
}: {
  initialValues?: TripFormValues;
  submitLabel: string;
  pendingLabel?: string;
  onSubmit: (values: TripFormValues) => void;
  pending: boolean;
  error: unknown;
}) {
  const [step, setStep] = useState(0);
  const form = useForm<TripFormValues>({ resolver: zodResolver(tripSchema), defaultValues: initialValues, mode: "onTouched" });
  const { errors } = form.formState;
  const values = useWatch({ control: form.control }) as TripFormValues;
  const stops = useFieldArray({ control: form.control, name: "stops" });
  const today = useToday();

  // Server-side field errors (400 ProblemDetail `errors`) map onto the matching inputs.
  useEffect(() => {
    if (!(error instanceof ApiError)) return;
    for (const [field, message] of Object.entries(error.fieldErrors)) {
      const target = SERVER_FIELD_ALIASES[field] ?? field;
      if (target in emptyTripForm) form.setError(target as keyof TripFormValues, { message });
    }
  }, [error, form]);

  const tripNights =
    values.startDate && values.endDate && values.endDate >= values.startDate ? tripDays(values.startDate, values.endDate) - 1 : 0;
  const plannedNights = values.stops.reduce((sum, s) => sum + (Number(s.nights) || 0), 0);
  const stopsError = errors.stops?.message ?? errors.stops?.root?.message;
  const nearby = useNearbyPlaces(values, step === 0 && stops.fields.length > 0);

  function addNearby(place: PlaceSuggestion) {
    const empty = values.stops.findIndex((s) => !s.city.trim());
    const stop = { city: place.city, countryCode: place.countryCode, nights: "1" };
    if (empty >= 0) {
      form.setValue(`stops.${empty}.city`, place.city, { shouldValidate: true });
      form.setValue(`stops.${empty}.countryCode`, place.countryCode, { shouldValidate: true });
    } else if (stops.fields.length < MAX_STOPS) {
      stops.append(stop);
    }
  }

  function enableMultiCity() {
    const destination = values.destination.trim();
    stops.replace([
      { city: destination, countryCode: "", nights: String(Math.max(1, tripNights - 1)) },
      { city: "", countryCode: "", nights: "1" },
    ]);
  }

  async function next() {
    if (await form.trigger(STEP_FIELDS[step])) setStep((s) => s + 1);
  }

  function toggle(field: "transportModes" | "preferences", value: string) {
    const current = form.getValues(field) as string[];
    const nextValue = current.includes(value) ? current.filter((v) => v !== value) : [...current, value];
    form.setValue(field, nextValue as never, { shouldValidate: true });
  }

  return (
    <>
      <ol className="flex gap-2 pb-4 text-xs">
        {STEPS.map((label, i) => (
          <li
            key={label}
            aria-current={i === step ? "step" : undefined}
            className={cn("rounded-full px-3 py-1", i === step ? "bg-primary text-primary-foreground" : "bg-muted text-muted-foreground")}
          >
            {i + 1}. {label}
          </li>
        ))}
      </ol>
      <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5" noValidate>
        {step === 0 && (
          <>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="From" id="origin" error={errors.origin?.message}>
                <Controller
                  control={form.control}
                  name="origin"
                  render={({ field }) => (
                    <PlaceAutocomplete id="origin" placeholder="City or country, e.g. Pune" aria-invalid={!!errors.origin} {...field} />
                  )}
                />
              </Field>
              <Field label="To" id="destination" error={errors.destination?.message}>
                <Controller
                  control={form.control}
                  name="destination"
                  render={({ field }) => (
                    <PlaceAutocomplete id="destination" placeholder="City or country, e.g. Goa" aria-invalid={!!errors.destination} {...field} />
                  )}
                />
              </Field>
            </div>
            <div className="grid gap-4 sm:grid-cols-3">
              <Field label="Start date" id="startDate" error={errors.startDate?.message}>
                <Input id="startDate" type="date" min={today || undefined} aria-invalid={!!errors.startDate} {...form.register("startDate")} />
              </Field>
              <Field label="End date" id="endDate" error={errors.endDate?.message}>
                <Input
                  id="endDate"
                  type="date"
                  min={values.startDate || today || undefined}
                  aria-invalid={!!errors.endDate}
                  {...form.register("endDate")}
                />
              </Field>
              <Field label="Travellers" id="travellersCount" error={errors.travellersCount?.message}>
                <Input
                  id="travellersCount"
                  type="number"
                  min={1}
                  max={50}
                  aria-invalid={!!errors.travellersCount}
                  {...form.register("travellersCount")}
                />
              </Field>
            </div>
            <fieldset className="space-y-3 rounded-lg border border-border p-4">
              <legend className="px-1 text-sm font-medium">Multi-city trip (optional)</legend>
              {stops.fields.length === 0 ? (
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="text-sm text-muted-foreground">
                    Visit 2 to {MAX_STOPS} places, each within 400 km by road of the previous one.
                  </p>
                  <Button variant="outline" size="sm" onClick={enableMultiCity}>
                    <Plus className="h-4 w-4" aria-hidden /> Add stops
                  </Button>
                </div>
              ) : (
                <>
                  <ol className="space-y-2">
                    {stops.fields.map((f, i) => {
                      const e = errors.stops?.[i];
                      return (
                        <li key={f.id} className="grid grid-cols-[1fr_5rem_5rem_auto] items-start gap-2" data-testid="stop-row">
                          <div>
                            <Label htmlFor={`stops.${i}.city`} className="sr-only">
                              Stop {i + 1} city
                            </Label>
                            <Controller
                              control={form.control}
                              name={`stops.${i}.city`}
                              render={({ field }) => (
                                <PlaceAutocomplete
                                  id={`stops.${i}.city`}
                                  placeholder={`Stop ${i + 1} city`}
                                  aria-invalid={!!e?.city}
                                  {...field}
                                  nearby={nearby}
                                  onSelect={(p) => form.setValue(`stops.${i}.countryCode`, p.countryCode, { shouldValidate: true })}
                                />
                              )}
                            />
                            <FieldError message={e?.city?.message} />
                          </div>
                          <div>
                            <Label htmlFor={`stops.${i}.countryCode`} className="sr-only">
                              Stop {i + 1} country code
                            </Label>
                            <Input
                              id={`stops.${i}.countryCode`}
                              placeholder="IN"
                              maxLength={2}
                              title="Optional 2-letter country code, e.g. TH"
                              aria-invalid={!!e?.countryCode}
                              {...form.register(`stops.${i}.countryCode`)}
                            />
                            <FieldError message={e?.countryCode?.message} />
                          </div>
                          <div>
                            <Label htmlFor={`stops.${i}.nights`} className="sr-only">
                              Stop {i + 1} nights
                            </Label>
                            <Input
                              id={`stops.${i}.nights`}
                              type="number"
                              min={1}
                              aria-invalid={!!e?.nights}
                              {...form.register(`stops.${i}.nights`)}
                            />
                            <FieldError message={e?.nights?.message} />
                          </div>
                          <Button variant="ghost" size="sm" aria-label={`Remove stop ${i + 1}`} onClick={() => stops.remove(i)}>
                            <Trash2 className="h-4 w-4" aria-hidden />
                          </Button>
                        </li>
                      );
                    })}
                  </ol>
                  {nearby.length > 0 && (
                    <div className="space-y-1" data-testid="nearby-places">
                      <p className="text-xs text-muted-foreground">Popular places nearby</p>
                      <div className="flex flex-wrap gap-2">
                        {nearby.slice(0, 6).map((p) => (
                          <button
                            key={p.city}
                            type="button"
                            className={chip(false)}
                            title={p.hint}
                            disabled={stops.fields.length >= MAX_STOPS && values.stops.every((s) => s.city.trim())}
                            onClick={() => addNearby(p)}
                          >
                            + {p.city} <span className="text-xs text-muted-foreground">({p.hint})</span>
                          </button>
                        ))}
                      </div>
                    </div>
                  )}
                  <div className="flex flex-wrap items-center justify-between gap-2 text-sm">
                    <span className={cn(plannedNights === tripNights ? "text-muted-foreground" : "text-amber-700 dark:text-amber-300")}>
                      {plannedNights} of {tripNights} night{tripNights === 1 ? "" : "s"} planned
                    </span>
                    <div className="flex gap-2">
                      {stops.fields.length < MAX_STOPS && (
                        <Button variant="outline" size="sm" onClick={() => stops.append({ city: "", countryCode: "", nights: "1" })}>
                          <Plus className="h-4 w-4" aria-hidden /> Add stop
                        </Button>
                      )}
                      <Button variant="ghost" size="sm" onClick={() => stops.replace([])}>
                        Single destination
                      </Button>
                    </div>
                  </div>
                  <label className="flex items-center gap-2 text-sm">
                    <input type="checkbox" {...form.register("optimizeRouteOrder")} />
                    Reorder stops for the shortest route
                  </label>
                </>
              )}
              <FieldError message={stopsError} />
            </fieldset>
          </>
        )}

        {step === 1 && (
          <>
            <div className="grid gap-4 sm:grid-cols-[1fr_10rem]">
              <Field label={`Total budget (${values.currency}, optional)`} id="budgetAmount" error={errors.budgetAmount?.message}>
                <Input
                  id="budgetAmount"
                  inputMode="decimal"
                  placeholder="e.g. 40000"
                  aria-invalid={!!errors.budgetAmount}
                  {...form.register("budgetAmount")}
                />
              </Field>
              <Field label="Currency" id="currency" error={errors.currency?.message}>
                <Select id="currency" aria-invalid={!!errors.currency} {...form.register("currency")}>
                  {CURRENCY_OPTIONS.map((c) => (
                    <option key={c} value={c}>
                      {c}
                    </option>
                  ))}
                </Select>
              </Field>
            </div>
            <fieldset className="space-y-2">
              <legend className="mb-2 text-sm font-medium">Comfort level</legend>
              <div className="grid gap-2 sm:grid-cols-2">
                {COMFORT_OPTIONS.map((o) => (
                  <label
                    key={o.value}
                    className={cn(
                      "cursor-pointer rounded-lg border p-3 text-sm",
                      values.comfortLevel === o.value ? "border-primary ring-1 ring-primary" : "border-border",
                    )}
                  >
                    <input type="radio" value={o.value} className="sr-only" {...form.register("comfortLevel")} />
                    <span className="font-medium">{o.label}</span>
                    <span className="block text-xs text-muted-foreground">{o.hint}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <ChipGroup
              legend="How would you like to travel? (leave empty to compare all)"
              options={TRANSPORT_OPTIONS.map((o) => [o.value, o.label])}
              selected={values.transportModes}
              onToggle={(v) => toggle("transportModes", v)}
            />
            <ChipGroup
              legend="Pace"
              options={PACES.map((p) => [p, p[0].toUpperCase() + p.slice(1)])}
              selected={[values.travelStyle]}
              onToggle={(v) => form.setValue("travelStyle", values.travelStyle === v ? "" : v)}
            />
            <ChipGroup
              legend="Interests"
              options={[...new Set([...INTERESTS, ...values.preferences])].map((i) => [i, i])}
              selected={values.preferences}
              onToggle={(v) => toggle("preferences", v)}
            />
          </>
        )}

        {step === 2 && (
          <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-sm">
            <dt className="text-muted-foreground">Route</dt>
            <dd>
              {values.origin} →{" "}
              {values.stops.length
                ? values.stops.map((s) => `${s.city} (${s.nights}n)`).join(" → ")
                : values.destination}
              {values.stops.length > 0 && values.optimizeRouteOrder && (
                <span className="text-muted-foreground"> · order may be optimised</span>
              )}
            </dd>
            <dt className="text-muted-foreground">Dates</dt>
            <dd>
              {formatDate(values.startDate)} – {formatDate(values.endDate)} ({tripDays(values.startDate, values.endDate)} days)
            </dd>
            <dt className="text-muted-foreground">Travellers</dt>
            <dd>{values.travellersCount}</dd>
            <dt className="text-muted-foreground">Budget</dt>
            <dd>{values.budgetAmount ? formatMoney(Number(values.budgetAmount), values.currency) : "Not set"}</dd>
            <dt className="text-muted-foreground">Currency</dt>
            <dd>{values.currency}</dd>
            <dt className="text-muted-foreground">Comfort</dt>
            <dd>{COMFORT_OPTIONS.find((o) => o.value === values.comfortLevel)?.label}</dd>
            <dt className="text-muted-foreground">Transport</dt>
            <dd>{values.transportModes.length ? values.transportModes.join(", ") : "Compare all"}</dd>
            <dt className="text-muted-foreground">Pace</dt>
            <dd>{values.travelStyle || "Not set"}</dd>
            <dt className="text-muted-foreground">Interests</dt>
            <dd>{values.preferences.join(", ") || "Not set"}</dd>
          </dl>
        )}

        {!!error && (
          <Alert variant="destructive">
            {errorMessage(error)}
            {error instanceof ApiError && error.violations.length > 0 && (
              <ul className="mt-2 list-disc pl-5" data-testid="route-violations">
                {error.violations.map((v) => (
                  <li key={v}>{v}</li>
                ))}
              </ul>
            )}
          </Alert>
        )}

        <div className="flex justify-between">
          <Button variant="ghost" disabled={step === 0} onClick={() => setStep((s) => s - 1)}>
            Back
          </Button>
          {step < STEPS.length - 1 ? (
            <Button onClick={next}>Continue</Button>
          ) : (
            <Button type="submit" disabled={pending}>
              {pending ? pendingLabel : submitLabel}
            </Button>
          )}
        </div>
      </form>
    </>
  );
}

function Field({ label, id, error, children }: { label: string; id: string; error?: string; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <Label htmlFor={id}>{label}</Label>
      {children}
      <FieldError message={error} />
    </div>
  );
}

function ChipGroup({
  legend,
  options,
  selected,
  onToggle,
}: {
  legend: string;
  options: [string, string][];
  selected: string[];
  onToggle: (value: string) => void;
}) {
  return (
    <fieldset className="space-y-2">
      <legend className="mb-2 text-sm font-medium">{legend}</legend>
      <div className="flex flex-wrap gap-2">
        {options.map(([value, label]) => (
          <button key={value} type="button" aria-pressed={selected.includes(value)} className={chip(selected.includes(value))} onClick={() => onToggle(value)}>
            {label}
          </button>
        ))}
      </div>
    </fieldset>
  );
}
