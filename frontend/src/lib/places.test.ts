import { describe, expect, it } from "vitest";

import { searchPlaces } from "./places";

describe("searchPlaces", () => {
  it("returns nothing for an empty query", () => {
    expect(searchPlaces("  ")).toEqual([]);
  });

  it("predicts cities by prefix", () => {
    expect(searchPlaces("ban")[0]).toMatchObject({ city: "Bangkok", countryCode: "TH" });
    expect(searchPlaces("ban").map((p) => p.city)).toContain("Banff");
  });

  it("lists popular cities of a typed country, most popular first", () => {
    const res = searchPlaces("japan");
    expect(res.map((p) => p.city).slice(0, 3)).toEqual(["Tokyo", "Kyoto", "Osaka"]);
    expect(res.every((p) => p.countryCode === "JP")).toBe(true);
  });

  it("matches country aliases, codes and accents", () => {
    expect(searchPlaces("uae")[0].city).toBe("Dubai");
    expect(searchPlaces("cancun")[0].city).toBe("Canc?n");
  });
});
