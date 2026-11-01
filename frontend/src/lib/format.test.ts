import { describe, expect, it } from "vitest";

import { formatDate, formatHours, formatINR, formatMoney, formatRange, titleCase, todayIso, tripDays } from "./format";

describe("format", () => {
  it("formats INR without decimals using Indian grouping", () => {
    expect(formatINR(1234567)).toBe("₹12,34,567");
    expect(formatINR(null)).toBe("—");
    expect(formatRange(1000, 2500)).toBe("₹1,000 – ₹2,500");
  });

  it("formats other currencies and falls back to INR for unknown codes", () => {
    expect(formatMoney(1500, "USD")).toBe("$1,500");
    expect(formatMoney(2000, "THB")).toContain("2,000");
    expect(formatMoney(2000, "THB")).not.toContain("₹");
    expect(formatRange(10, 20, "EUR")).toBe("€10 – €20");
    expect(formatMoney(100, "??")).toBe("₹100");
    expect(formatMoney(undefined, "USD")).toBe("—");
  });

  it("formats calendar dates without time-zone shifts", () => {
    expect(formatDate("2026-12-01")).toBe("1 Dec 2026");
    expect(formatDate(null)).toBe("—");
  });

  it("counts trip days inclusively", () => {
    expect(tripDays("2026-03-01", "2026-03-01")).toBe(1);
    expect(tripDays("2026-03-30", "2026-04-02")).toBe(4);
  });

  it("returns the local calendar date as ISO", () => {
    expect(todayIso(new Date(2026, 0, 5, 23, 30))).toBe("2026-01-05");
  });

  it("formats hours and enum labels", () => {
    expect(formatHours(11.5)).toBe("11h 30m");
    expect(formatHours(2)).toBe("2h");
    expect(titleCase("LOCAL_TRANSPORT")).toBe("Local Transport");
  });
});
