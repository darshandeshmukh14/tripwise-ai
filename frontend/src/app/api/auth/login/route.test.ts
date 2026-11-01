// @vitest-environment node
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fakeToken } from "@/test/fixtures";

import { POST } from "./route";

const fetchMock = vi.fn<typeof fetch>();

const login = (origin = "http://localhost:3000") =>
  new NextRequest("http://localhost:3000/api/auth/login", {
    method: "POST",
    headers: { host: "localhost:3000", origin, "content-type": "application/json" },
    body: JSON.stringify({ email: "a@b.co", password: "password1" }),
  });

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("login route", () => {
  it("stores both tokens in httpOnly cookies and never returns them", async () => {
    const token = fakeToken(Math.floor(Date.now() / 1000) + 900);
    const refreshTokenExpiresAt = new Date(Date.now() + 7 * 86_400_000).toISOString();
    fetchMock.mockResolvedValue(Response.json({
      token, refreshToken: "refresh-1", refreshTokenExpiresAt, userId: 1, email: "a@b.co", fullName: "A",
    }));

    const res = await POST(login());

    expect(fetchMock.mock.calls[0][0]).toBe("http://localhost:8080/api/v1/auth/login");
    const body = await res.json();
    expect(body).toEqual({ userId: 1, email: "a@b.co", fullName: "A" });
    const cookies = res.headers.getSetCookie();
    const access = cookies.find((c) => c.startsWith("tw_session=")) ?? "";
    const refresh = cookies.find((c) => c.startsWith("tw_refresh=")) ?? "";
    expect(access).toContain(`tw_session=${token}`);
    expect(access).toMatch(/HttpOnly/i);
    expect(access).toMatch(/SameSite=lax/i);
    expect(refresh).toContain("tw_refresh=refresh-1");
    expect(refresh).toMatch(/HttpOnly/i);
    expect(refresh).toMatch(/Max-Age=(604800|60479\d)/);
  });

  it("rejects a backend response without a refresh token", async () => {
    fetchMock.mockResolvedValue(Response.json({ token: fakeToken(Math.floor(Date.now() / 1000) + 900) }));
    const res = await POST(login());
    expect(res.status).toBe(502);
    expect(res.headers.get("set-cookie")).toBeNull();
  });

  it("passes backend errors through without setting a cookie", async () => {
    fetchMock.mockResolvedValue(Response.json({ title: "Invalid credentials", status: 401 }, { status: 401 }));
    const res = await POST(login());
    expect(res.status).toBe(401);
    expect(res.headers.get("set-cookie")).toBeNull();
  });

  it("rejects cross-origin login attempts", async () => {
    const res = await POST(login("https://evil.example"));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
