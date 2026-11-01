// @vitest-environment node
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fakeToken } from "@/test/fixtures";

import { DELETE, GET, POST } from "./route";

const fetchMock = vi.fn<typeof fetch>();
const TOKEN = fakeToken(Math.floor(Date.now() / 1000) + 3600);
const EXPIRED = fakeToken(Math.floor(Date.now() / 1000) - 10);
const NEW_TOKEN = fakeToken(Math.floor(Date.now() / 1000) + 900);
const refreshed = (refreshToken = "refresh-2") =>
  Response.json({
    token: NEW_TOKEN,
    refreshToken,
    refreshTokenExpiresAt: new Date(Date.now() + 86_400_000).toISOString(),
    userId: 1,
    email: "a@b.co",
    fullName: "A",
  });

function request(
  path: string,
  init: { method?: string; cookie?: boolean; access?: string; refresh?: string; origin?: string; body?: string } = {},
) {
  const headers = new Headers({ host: "localhost:3000" });
  const cookies = [];
  if (init.cookie !== false) cookies.push(`tw_session=${init.access ?? TOKEN}`);
  if (init.refresh) cookies.push(`tw_refresh=${init.refresh}`);
  if (cookies.length) headers.set("cookie", cookies.join("; "));
  if (init.origin) headers.set("origin", init.origin);
  if (init.body) headers.set("content-type", "application/json");
  return new NextRequest(`http://localhost:3000/api/backend/${path}`, { method: init.method ?? "GET", headers, body: init.body });
}
const ctx = (path: string) => ({ params: Promise.resolve({ path: path.split("?")[0].split("/") }) });

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("backend proxy route", () => {
  it("forwards allowed paths with the bearer token and query string", async () => {
    fetchMock.mockResolvedValue(Response.json({ content: [] }, { headers: { "set-cookie": "x=1", "x-internal": "y" } }));

    const res = await GET(request("trips?page=1&size=5"), ctx("trips"));

    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/trips?page=1&size=5");
    expect(new Headers(init?.headers).get("authorization")).toBe(`Bearer ${TOKEN}`);
    expect(res.headers.get("set-cookie")).toBeNull();
    expect(res.headers.get("x-internal")).toBeNull();
  });

  it("forwards JSON bodies on POST", async () => {
    fetchMock.mockResolvedValue(Response.json({ ok: true }, { status: 202 }));
    const res = await POST(request("trips/7/generate", { method: "POST", body: '{"answers":{}}', origin: "http://localhost:3000" }), ctx("trips/7/generate"));
    expect(res.status).toBe(202);
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "POST", body: '{"answers":{}}' });
  });

  it.each(["actuator/health", "auth/login", "v3/api-docs", "trips/../actuator", "trips/1%2F..%2Fx"])(
    "returns 404 for disallowed path %s without calling the backend",
    async (path) => {
      const res = await GET(request(path), { params: Promise.resolve({ path: path.split("/") }) });
      expect(res.status).toBe(404);
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it("returns 401 without a session cookie", async () => {
    const res = await GET(request("trips", { cookie: false }), ctx("trips"));
    expect(res.status).toBe(401);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects cross-origin writes", async () => {
    const res = await DELETE(request("trips/1", { method: "DELETE", origin: "https://evil.example" }), ctx("trips/1"));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("clears the session cookie when the backend returns 401", async () => {
    fetchMock.mockResolvedValue(Response.json({ title: "Unauthorized" }, { status: 401 }));
    const res = await GET(request("trips/1"), ctx("trips/1"));
    expect(res.status).toBe(401);
    expect(res.headers.get("set-cookie")).toMatch(/tw_session=;.*(Max-Age=0|Expires=Thu, 01 Jan 1970)/i);
  });

  it("maps an unreachable backend to 502", async () => {
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    const res = await GET(request("trips"), ctx("trips"));
    expect(res.status).toBe(502);
    expect(await res.json()).toMatchObject({ title: "Backend unavailable" });
  });

  it("refreshes an expired access token before forwarding and stores the new pair", async () => {
    fetchMock.mockResolvedValueOnce(refreshed()).mockResolvedValueOnce(Response.json({ content: [] }));

    const res = await GET(request("trips", { access: EXPIRED, refresh: "refresh-1" }), ctx("trips"));

    expect(res.status).toBe(200);
    const [refreshUrl, refreshInit] = fetchMock.mock.calls[0];
    expect(refreshUrl).toBe("http://localhost:8080/api/v1/auth/refresh");
    expect(refreshInit?.body).toBe('{"refreshToken":"refresh-1"}');
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get("authorization")).toContain(NEW_TOKEN);
    const cookies = res.headers.getSetCookie();
    expect(cookies.some((c) => c.startsWith(`tw_session=${NEW_TOKEN}`))).toBe(true);
    expect(cookies.some((c) => c.startsWith("tw_refresh=refresh-2"))).toBe(true);
  });

  it("refreshes and retries once with the same body when the backend rejects the access token", async () => {
    fetchMock
      .mockResolvedValueOnce(Response.json({ title: "Unauthorized" }, { status: 401 }))
      .mockResolvedValueOnce(refreshed())
      .mockResolvedValueOnce(Response.json({ ok: true }, { status: 202 }));

    const res = await POST(
      request("trips/7/generate", { method: "POST", body: '{"answers":{}}', refresh: "refresh-1" }),
      ctx("trips/7/generate"),
    );

    expect(res.status).toBe(202);
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[2][1]).toMatchObject({ method: "POST", body: '{"answers":{}}' });
  });

  it("signs out when the refresh token is rejected", async () => {
    fetchMock.mockResolvedValueOnce(Response.json({ title: "Session expired" }, { status: 401 }));

    const res = await GET(request("trips", { access: EXPIRED, refresh: "revoked" }), ctx("trips"));

    expect(res.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const cookies = res.headers.getSetCookie();
    expect(cookies.some((c) => /^tw_session=;/.test(c))).toBe(true);
    expect(cookies.some((c) => /^tw_refresh=;/.test(c))).toBe(true);
  });

  it("keeps the session when the refresh call fails for a temporary reason", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("fetch failed"));

    const res = await GET(request("trips", { access: EXPIRED, refresh: "refresh-1" }), ctx("trips"));

    expect(res.status).toBe(502);
    expect(res.headers.get("set-cookie")).toBeNull();
  });

  it("rotates the refresh token only once for parallel requests", async () => {
    fetchMock.mockImplementation(async (url) =>
      String(url).endsWith("/auth/refresh") ? refreshed() : Response.json({ content: [] }),
    );

    const responses = await Promise.all([
      GET(request("trips", { access: EXPIRED, refresh: "shared" }), ctx("trips")),
      GET(request("trips/1", { access: EXPIRED, refresh: "shared" }), ctx("trips/1")),
    ]);

    expect(responses.map((r) => r.status)).toEqual([200, 200]);
    expect(fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/auth/refresh"))).toHaveLength(1);
  });
});
