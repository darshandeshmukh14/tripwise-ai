import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { BookingLink, ClarifyQuestion, LegPlan, StopPlan } from "@/lib/api/types";
import { costBreakdown, transportOptions } from "@/test/fixtures";

import { BookingLinks } from "./booking-links";
import { chronological } from "./chat-panel";
import { ClarifyForm } from "./clarify-form";
import { CostBreakdownTable } from "./cost-breakdown-table";
import { RouteTimeline } from "./route-timeline";
import { TransportComparison } from "./transport-comparison";

describe("TransportComparison", () => {
  it("shows each mode with cost, duration and the recommended badge", () => {
    render(<TransportComparison options={transportOptions} />);
    const train = screen.getByTestId("transport-TRAIN");
    expect(train).toHaveTextContent("Recommended");
    expect(train).toHaveTextContent("₹5,200");
    expect(train).toHaveTextContent("11h 30m");
    expect(screen.getByTestId("transport-FLIGHT")).not.toHaveTextContent("Recommended");
  });

  it("formats prices in the plan currency", () => {
    render(<TransportComparison options={transportOptions} currency="USD" />);
    expect(screen.getByTestId("transport-TRAIN")).toHaveTextContent("$5,200");
  });
});

describe("CostBreakdownTable", () => {
  it("labels every line as an estimate with confidence and shows the total range", () => {
    render(<CostBreakdownTable breakdown={costBreakdown} />);
    expect(screen.getAllByText("Estimate")).toHaveLength(2);
    expect(screen.getByText("Medium")).toBeInTheDocument();
    expect(screen.getByText("Low")).toBeInTheDocument();
    expect(screen.getByText("₹30,000 – ₹47,000")).toBeInTheDocument();
  });

  it("shows fare-rule and web-hint sources with their link and the stop name", () => {
    const [stay, train] = costBreakdown.items;
    render(
      <CostBreakdownTable
        stopNames={{ 1: "Phuket" }}
        breakdown={{
          ...costBreakdown,
          currency: "THB",
          items: [
            { ...stay, source: "WEB_HINT", stopSeq: 1, sourceUrl: "https://example.com/hotel" },
            { ...train, source: "FARE_RULE" },
          ],
        }}
      />,
    );
    expect(screen.getByText("Web hint")).toBeInTheDocument();
    expect(screen.getByText("Fare rule")).toBeInTheDocument();
    expect(screen.queryByText("Estimate")).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Price source" })).toHaveAttribute("href", "https://example.com/hotel");
    expect(screen.getByText(/· Phuket/)).toBeInTheDocument();
    expect(screen.queryByText(/₹/)).not.toBeInTheDocument();
  });
});

describe("BookingLinks", () => {
  const link = (over: Partial<BookingLink>): BookingLink => ({
    provider: "x", label: "Search", url: "https://example.com", kind: "TRANSPORT", sponsored: false, ...over,
  });

  it("marks affiliate links as sponsored and discloses them", () => {
    render(
      <BookingLinks
        links={[
          link({ label: "Trains on IRCTC", url: "https://irctc.example" }),
          link({ label: "Hotels on Booking", url: "https://booking.example", kind: "STAY", sponsored: true }),
        ]}
      />,
    );
    expect(screen.getByRole("link", { name: /Trains on IRCTC/ })).toHaveAttribute("rel", "noopener noreferrer");
    const hotel = screen.getByRole("link", { name: /Hotels on Booking/ });
    expect(hotel).toHaveAttribute("rel", "sponsored noopener noreferrer");
    expect(hotel).toHaveAttribute("target", "_blank");
    expect(screen.getByTestId("affiliate-disclosure")).toBeInTheDocument();
  });

  it("omits the disclosure without sponsored links", () => {
    render(<BookingLinks links={[link({})]} />);
    expect(screen.queryByTestId("affiliate-disclosure")).not.toBeInTheDocument();
  });
});

describe("RouteTimeline", () => {
  const stop = (seq: number, city: string, nights: number): StopPlan => ({
    seq, city, countryCode: "IN", nights, arrivalDate: "2026-12-01", departureDate: "2026-12-03", season: "PEAK",
  });
  const leg = (index: number, fromCity: string, toCity: string): LegPlan => ({
    index, fromCity, toCity, options: [{ ...transportOptions[0], distanceKm: 100 + index }],
  });

  it("orders stops and places the outbound, between-stop and return legs", () => {
    render(
      <RouteTimeline
        currency="INR"
        stops={[stop(2, "Gokarna", 1), stop(1, "Goa", 2)]}
        legs={[leg(2, "Gokarna", "Pune"), leg(0, "Pune", "Goa"), leg(1, "Goa", "Gokarna")]}
      />,
    );
    const stops = screen.getAllByTestId("route-stop");
    expect(stops[0]).toHaveTextContent("1. Goa");
    expect(within(stops[0]).getByText(/Goa → Gokarna · 101 km/)).toBeInTheDocument();
    expect(within(stops[1]).getByText(/Gokarna → Pune · 102 km/)).toBeInTheDocument();
    expect(screen.getAllByTestId("route-leg")[0]).toHaveTextContent("Pune → Goa · 100 km");
    expect(screen.getByText("2 stops · 3 nights")).toBeInTheDocument();
  });
});

describe("chronological", () => {
  const msg = (id: number, senderRole: "USER" | "ASSISTANT" | "SYSTEM") =>
    ({ id, senderRole, content: `m${id}`, createdAt: "2026-01-01T00:00:00Z" });

  it("orders newest-first pages oldest to newest, dropping duplicates and system messages", () => {
    const pages = [{ content: [msg(6, "ASSISTANT"), msg(5, "USER")] }, { content: [msg(5, "USER"), msg(4, "SYSTEM"), msg(3, "ASSISTANT")] }];
    expect(chronological(pages).map((m) => m.id)).toEqual([3, 5, 6]);
  });
});

describe("ClarifyForm", () => {
  const questions: ClarifyQuestion[] = [
    { id: "budget", text: "What is your total budget?", type: "number", options: [] },
    { id: "interests", text: "Interests?", type: "multi_choice", options: ["Food", "Beaches", "Wildlife"] },
    { id: "pace", text: "Pace?", type: "single_choice", options: ["relaxed", "packed"] },
  ];

  it("submits typed, multi and single choice answers", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<ClarifyForm questions={questions} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText("What is your total budget?"), "45000");
    await user.click(screen.getByRole("button", { name: "Food" }));
    await user.click(screen.getByRole("button", { name: "Wildlife" }));
    await user.click(screen.getByRole("button", { name: "relaxed" }));
    await user.click(screen.getByRole("button", { name: "packed" }));
    await user.click(screen.getByRole("button", { name: "Plan my trip" }));

    expect(onSubmit).toHaveBeenCalledWith({ budget: "45000", interests: "Food,Wildlife", pace: "packed" });
  });

  it("allows skipping questions and blocks invalid amounts", async () => {
    const onSubmit = vi.fn();
    const user = userEvent.setup();
    render(<ClarifyForm questions={questions} onSubmit={onSubmit} />);

    await user.type(screen.getByLabelText("What is your total budget?"), "lots");
    await user.click(screen.getByRole("button", { name: "Plan my trip" }));
    expect(onSubmit).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("Enter a valid amount");

    await user.clear(screen.getByLabelText("What is your total budget?"));
    await user.click(screen.getByRole("button", { name: "Plan my trip" }));
    expect(onSubmit).toHaveBeenCalledWith({});
  });
});
