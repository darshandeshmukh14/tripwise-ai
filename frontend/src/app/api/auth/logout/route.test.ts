// @vitest-environment node
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { POST } from "./route";

const fetchMock = vi.fn<typeof fetch>();

const logout = (cookie?: string, origin = "http://localhost:3000") => {
  const headers = new Headers({ host: "localhost:3000", origin });
  if (cookie) headers.set("cookie", cookie);
  return new NextRequest("http://localhost:3000/api/auth/logout", { method: "POST", headers });
};

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("logout route", () => {
  it("revokes the refresh token in the backend and clears both cookies", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

    const res = await POST(logout("tw_session=a; tw_refresh=refresh-1"));

    expect(res.status).toBe(204);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/auth/logout");
    expect(init?.body).toBe('{"refreshToken":"refresh-1"}');
    const cookies = res.headers.getSetCookie();
    expect(cookies.some((c) => /^tw_session=;/.test(c))).toBe(true);
    expect(cookies.some((c) => /^tw_refresh=;/.test(c))).toBe(true);
  });

  it("still clears cookies when the backend is unreachable", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const res = await POST(logout("tw_refresh=refresh-1"));
    expect(res.status).toBe(204);
    expect(res.headers.getSetCookie().some((c) => /^tw_refresh=;/.test(c))).toBe(true);
  });

  it("does not call the backend without a refresh cookie", async () => {
    const res = await POST(logout());
    expect(res.status).toBe(204);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects cross-origin logout", async () => {
    const res = await POST(logout("tw_refresh=refresh-1", "https://evil.example"));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
