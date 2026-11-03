// @vitest-environment node
import { NextRequest } from "next/server";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { POST } from "./route";

const fetchMock = vi.fn<typeof fetch>();

function request(path: string, init: { origin?: string; cookie?: string } = {}) {
  const headers = new Headers({ host: "localhost:3000", "content-type": "application/json" });
  if (init.origin) headers.set("origin", init.origin);
  if (init.cookie) headers.set("cookie", init.cookie);
  return new NextRequest(`http://localhost:3000/api/guest/${path}`, { method: "POST", headers, body: '{"trip":{}}' });
}
const ctx = (path: string) => ({ params: Promise.resolve({ path: path.split("/") }) });

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

describe("guest proxy route", () => {
  it("forwards allowed guest paths without a token", async () => {
    fetchMock.mockResolvedValue(Response.json({ questions: [] }));

    const res = await POST(request("stops/suggest", { cookie: "tw_session=abc" }), ctx("stops/suggest"));

    expect(res.status).toBe(200);
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("http://localhost:8080/api/v1/guest/stops/suggest");
    expect(new Headers(init?.headers).get("authorization")).toBeNull();
    expect(init?.body).toBe('{"trip":{}}');
  });

  it("rejects unknown paths without calling the backend", async () => {
    const res = await POST(request("trips"), ctx("../trips"));
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("rejects cross-origin requests", async () => {
    const res = await POST(request("generate", { origin: "https://evil.example" }), ctx("generate"));
    expect(res.status).toBe(403);
    expect(fetchMock).not.toHaveBeenCalled();
  });
});
