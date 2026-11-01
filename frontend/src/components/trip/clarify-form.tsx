"use client";

import { useState } from "react";

import { Button } from "@/components/ui/button";
import { FieldError, Input, Label } from "@/components/ui/form-controls";
import type { ClarifyQuestion } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface Props {
  questions: ClarifyQuestion[];
  onSubmit: (answers: Record<string, string>) => void;
  submitting?: boolean;
  submitLabel?: string;
}

const chip = (selected: boolean) =>
  cn(
    "rounded-full border px-3 py-1 text-sm transition-colors",
    selected ? "border-primary bg-primary text-primary-foreground" : "border-border hover:bg-muted",
  );

/** Renders the AI service's clarifying questions; multi-choice answers are sent comma-separated. */
export function ClarifyForm({ questions, onSubmit, submitting, submitLabel = "Plan my trip" }: Props) {
  const [values, setValues] = useState<Record<string, string[]>>({});
  const [errors, setErrors] = useState<Record<string, string>>({});

  const set = (id: string, next: string[]) => setValues((v) => ({ ...v, [id]: next }));
  const toggle = (id: string, option: string) => {
    const current = values[id] ?? [];
    set(id, current.includes(option) ? current.filter((o) => o !== option) : [...current, option]);
  };

  function submit(event: React.FormEvent) {
    event.preventDefault();
    const nextErrors: Record<string, string> = {};
    const answers: Record<string, string> = {};
    for (const q of questions) {
      const value = (values[q.id] ?? []).map((s) => s.trim()).filter(Boolean).join(",");
      if (!value) continue;
      if (q.type === "number" && !/^\d{1,10}(\.\d{1,2})?$/.test(value)) {
        nextErrors[q.id] = "Enter a valid amount";
        continue;
      }
      answers[q.id] = value.slice(0, 500);
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length === 0) onSubmit(answers);
  }

  return (
    <form onSubmit={submit} className="space-y-6" noValidate>
      {questions.map((q) => (
        <fieldset key={q.id} className="space-y-2">
          <legend className="mb-2 text-sm font-medium">{q.text}</legend>
          {q.type === "single_choice" || q.type === "multi_choice" ? (
            <div className="flex flex-wrap gap-2">
              {q.options.map((option) => {
                const selected = (values[q.id] ?? []).includes(option);
                return (
                  <button
                    key={option}
                    type="button"
                    aria-pressed={selected}
                    className={chip(selected)}
                    onClick={() => (q.type === "multi_choice" ? toggle(q.id, option) : set(q.id, selected ? [] : [option]))}
                  >
                    {option}
                  </button>
                );
              })}
            </div>
          ) : (
            <>
              <Label htmlFor={`q-${q.id}`} className="sr-only">
                {q.text}
              </Label>
              <Input
                id={`q-${q.id}`}
                inputMode={q.type === "number" ? "decimal" : undefined}
                aria-invalid={!!errors[q.id]}
                value={values[q.id]?.[0] ?? ""}
                onChange={(e) => set(q.id, [e.target.value])}
              />
            </>
          )}
          <FieldError message={errors[q.id]} />
        </fieldset>
      ))}
      <p className="text-xs text-muted-foreground">Skipped questions are fine — we&apos;ll use sensible defaults.</p>
      <Button type="submit" disabled={submitting}>
        {submitting ? "Starting…" : submitLabel}
      </Button>
    </form>
  );
}
