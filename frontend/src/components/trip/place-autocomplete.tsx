"use client";

import { MapPin } from "lucide-react";
import { useId, useMemo, useState } from "react";

import { Input } from "@/components/ui/form-controls";
import { searchPlaces, type PlaceSuggestion } from "@/lib/places";
import { cn } from "@/lib/utils";

type Props = Omit<React.InputHTMLAttributes<HTMLInputElement>, "value" | "onChange" | "onSelect"> & {
  value: string;
  onChange: (value: string) => void;
  onSelect?: (place: PlaceSuggestion) => void;
  /** Popular places near the previous stop: listed when empty, ranked first when they match the text. */
  nearby?: PlaceSuggestion[];
  ref?: React.Ref<HTMLInputElement>;
};

const MAX_SUGGESTIONS = 8;

/** Free-text city input with predictive suggestions; typing a country lists its popular cities. */
export function PlaceAutocomplete({ value, onChange, onSelect, nearby, onBlur, onKeyDown, ...props }: Props) {
  const listId = useId();
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const suggestions = useMemo(() => {
    const q = (value ?? "").trim().toLowerCase();
    const near = (nearby ?? []).filter((p) => p.city.toLowerCase().startsWith(q));
    const seen = new Set(near.map((p) => p.city.toLowerCase()));
    const rest = searchPlaces(value ?? "").filter((p) => !seen.has(p.city.toLowerCase()));
    return [...near, ...rest].slice(0, MAX_SUGGESTIONS);
  }, [value, nearby]);
  const show = open && suggestions.length > 0;

  function choose(place: PlaceSuggestion) {
    onChange(place.city);
    onSelect?.(place);
    setOpen(false);
  }

  return (
    <div className="relative">
      <Input
        {...props}
        value={value ?? ""}
        autoComplete="off"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={show}
        aria-controls={listId}
        aria-activedescendant={show ? `${listId}-${active}` : undefined}
        onChange={(e) => {
          onChange(e.target.value);
          setActive(0);
          setOpen(true);
        }}
        onFocus={() => setOpen(true)}
        onBlur={(e) => {
          setOpen(false);
          onBlur?.(e);
        }}
        onKeyDown={(e) => {
          if (show && e.key === "ArrowDown") setActive((a) => (a + 1) % suggestions.length);
          else if (show && e.key === "ArrowUp") setActive((a) => (a - 1 + suggestions.length) % suggestions.length);
          else if (show && e.key === "Enter") choose(suggestions[active]);
          else if (e.key === "Escape") setOpen(false);
          else return onKeyDown?.(e);
          e.preventDefault();
        }}
      />
      {show && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-64 w-full min-w-56 overflow-auto rounded-md border border-border bg-background py-1 text-sm shadow-lg"
        >
          {suggestions.map((s, i) => (
            <li
              key={`${s.countryCode}-${s.city}`}
              id={`${listId}-${i}`}
              role="option"
              aria-selected={i === active}
              className={cn("flex cursor-pointer items-center gap-2 px-3 py-2", i === active && "bg-muted")}
              onMouseDown={(e) => {
                e.preventDefault();
                choose(s);
              }}
              onMouseEnter={() => setActive(i)}
            >
              <MapPin className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
              <span>{s.city}</span>
              <span className="ml-auto text-xs text-muted-foreground">{s.hint ?? s.country}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
