import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";

import type { PlaceSuggestion } from "@/lib/places";

import { PlaceAutocomplete } from "./place-autocomplete";

const NEARBY: PlaceSuggestion[] = [
  { city: "Gokarna", countryCode: "IN", country: "India", hint: "140 km from Goa" },
  { city: "Dandeli", countryCode: "IN", country: "India", hint: "110 km from Goa" },
];

function Harness({ onSelect, nearby }: { onSelect: (p: PlaceSuggestion) => void; nearby?: PlaceSuggestion[] }) {
  const [value, setValue] = useState("");
  return <PlaceAutocomplete aria-label="Stop" value={value} onChange={setValue} onSelect={onSelect} nearby={nearby} />;
}

describe("PlaceAutocomplete nearby", () => {
  it("shows popular place choices when an empty field is focused", async () => {
    render(<Harness onSelect={vi.fn()} />);
    await userEvent.click(screen.getByRole("combobox"));
    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options[0]).toHaveTextContent("Goa");
    expect(options[1]).toHaveTextContent("Mumbai");
  });

  it("lists nearby places when empty and selects one", async () => {
    const onSelect = vi.fn();
    render(<Harness onSelect={onSelect} nearby={NEARBY} />);
    await userEvent.click(screen.getByRole("combobox"));
    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options[0]).toHaveTextContent("Gokarna");
    expect(options[0]).toHaveTextContent("140 km from Goa");
    await userEvent.click(options[1]);
    expect(onSelect).toHaveBeenCalledWith(NEARBY[1]);
    expect(screen.getByRole("combobox")).toHaveValue("Dandeli");
  });

  it("ranks matching nearby places before catalogue matches while typing", async () => {
    render(<Harness onSelect={vi.fn()} nearby={NEARBY} />);
    await userEvent.type(screen.getByRole("combobox"), "Go");
    const options = within(screen.getByRole("listbox")).getAllByRole("option");
    expect(options[0]).toHaveTextContent("Gokarna");
    expect(options.some((o) => o.textContent?.includes("Goa"))).toBe(true);
  });
});
