import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { TripForm } from "./trip-form";

describe("TripForm place autocomplete", () => {
  it("shows typed suggestions for origin and destination", async () => {
    const user = userEvent.setup();
    render(<TripForm submitLabel="Plan trip" onSubmit={vi.fn()} pending={false} error={null} />);

    await user.type(screen.getByRole("combobox", { name: "From" }), "Mumb");
    expect(within(await screen.findByRole("listbox")).getByRole("option", { name: /Mumbai/ })).toBeInTheDocument();

    await user.clear(screen.getByRole("combobox", { name: "From" }));
    await user.type(screen.getByRole("combobox", { name: "To" }), "Bang");
    expect(within(await screen.findByRole("listbox")).getByRole("option", { name: /Bangkok/ })).toBeInTheDocument();
  });

  it("shows typed suggestions in an added multi-city stop", async () => {
    const user = userEvent.setup();
    render(<TripForm submitLabel="Plan trip" onSubmit={vi.fn()} pending={false} error={null} />);

    await user.click(screen.getByRole("button", { name: "Add stops" }));
    const stop = screen.getByRole("combobox", { name: "Stop 2 city" });
    await user.type(stop, "Gok");

    expect(within(await screen.findByRole("listbox")).getByRole("option", { name: /Gokarna/ })).toBeInTheDocument();
  });
});
