// @vitest-environment node
import { describe, expect, it } from "vitest";

import { fakeToken } from "@/test/fixtures";

import { isSessionValid, needsRefresh, refreshCookie, rejectCrossOrigin, sessionCookie, tokenExpiry } from "./bff";

const NOW = Date.UTC(2026, 0, 1);
const nowSec = NOW / 1000;

describe("session helpers", () => {
  it("reads exp from the token payload", () => {
    expect(tokenExpiry(fakeToken(nowSec + 60))).toBe(nowSec + 60);
    expect(tokenExpiry("not-a-jwt")).toBeNull();
  });

  it("treats missing, malformed and expired tokens as invalid", () => {
    expect(isSessionValid(undefined, NOW)).toBe(false);
    expect(isSessionValid("garbage", NOW)).toBe(false);
    expect(isSessionValid(fakeToken(nowSec - 1), NOW)).toBe(false);
    expect(isSessionValid(fakeToken(nowSec + 3600), NOW)).toBe(true);
  });

  it("issues an httpOnly lax cookie that expires with the token", () => {
    const cookie = sessionCookie(fakeToken(nowSec + 3600), NOW);
    expect(cookie).toMatchObject({ name: "tw_session", httpOnly: true, sameSite: "lax", path: "/", maxAge: 3600 });
  });

  it("refreshes tokens that are missing, expired or about to expire", () => {
    expect(needsRefresh(undefined, NOW)).toBe(true);
    expect(needsRefresh(fakeToken(nowSec + 10), NOW)).toBe(true);
    expect(needsRefresh(fakeToken(nowSec + 900), NOW)).toBe(false);
  });

  it("issues a refresh cookie that expires with the refresh token", () => {
    const cookie = refreshCookie("r", new Date(NOW + 7 * 86_400_000).toISOString(), NOW);
    expect(cookie).toMatchObject({ name: "tw_refresh", value: "r", httpOnly: true, sameSite: "lax", maxAge: 604_800 });
  });
});

describe("rejectCrossOrigin", () => {
  const req = (method: string, headers: Record<string, string>) => new Request("http://localhost:3000/api/x", { method, headers });

  it("allows same-origin and Origin-less requests", () => {
    expect(rejectCrossOrigin(req("POST", { origin: "http://localhost:3000", host: "localhost:3000" }))).toBeNull();
    expect(rejectCrossOrigin(req("POST", { host: "localhost:3000" }))).toBeNull();
    expect(rejectCrossOrigin(req("GET", { origin: "https://evil.example", host: "localhost:3000" }))).toBeNull();
  });

  it("rejects cross-origin state-changing requests", () => {
    const res = rejectCrossOrigin(req("POST", { origin: "https://evil.example", host: "localhost:3000" }));
    expect(res?.status).toBe(403);
  });
});
