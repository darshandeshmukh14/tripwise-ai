import { NextResponse, type NextRequest } from "next/server";

import {
  type BackendAuth,
  clearSessionCookies,
  forwardToBackend,
  needsRefresh,
  problem,
  REFRESH_COOKIE,
  refreshSession,
  rejectCrossOrigin,
  SESSION_COOKIE,
  setSessionCookies,
} from "@/lib/server/bff";

// Only the user-facing Spring API is reachable through the BFF (no actuator, docs or auth endpoints).
const ALLOWED_PATH = /^(trips(\/[A-Za-z0-9_-]+)*|auth\/me)$/;

type Context = { params: Promise<{ path: string[] }> };

function signedOut(): NextResponse {
  const response = NextResponse.json(
    { type: "about:blank", title: "Not signed in", status: 401, detail: "Your session has ended. Please sign in again." },
    { status: 401, headers: { "content-type": "application/problem+json" } },
  );
  clearSessionCookies(response);
  return response;
}

async function handle(request: NextRequest, { params }: Context): Promise<Response> {
  const { path } = await params;
  const joined = path.join("/");
  if (!ALLOWED_PATH.test(joined)) {
    return problem(404, "Not found", "Unknown API path.");
  }
  const rejected = rejectCrossOrigin(request);
  if (rejected) return rejected;

  // Mutated inside refresh(); a holder object keeps TypeScript's narrowing honest across the closure.
  const session: { accessToken?: string; refreshed: BackendAuth | null } = {
    accessToken: request.cookies.get(SESSION_COOKIE)?.value,
    refreshed: null,
  };
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (!session.accessToken && !refreshToken) return problem(401, "Not signed in", "Please sign in.");

  const refresh = async (): Promise<Response | null> => {
    if (!refreshToken) return signedOut();
    const result = await refreshSession(request, refreshToken);
    if (typeof result === "number") {
      return result === 401 || result === 400
        ? signedOut()
        : problem(502, "Backend unavailable", "Could not renew your session. Please try again.");
    }
    session.refreshed = result;
    session.accessToken = result.token;
    return null;
  };

  if (needsRefresh(session.accessToken)) {
    const failure = await refresh();
    if (failure) return failure;
  }

  const backendPath = `/api/v1/${joined}${request.nextUrl.search}`;
  const body = request.method === "GET" || request.method === "HEAD" ? undefined : await request.text();
  let upstream = await forwardToBackend(request, backendPath, session.accessToken, body);
  if (upstream.status === 401 && !session.refreshed) {
    const failure = await refresh();
    if (failure) return failure;
    upstream = await forwardToBackend(request, backendPath, session.accessToken, body);
  }
  if (upstream.status === 401) return signedOut();
  if (!session.refreshed) return upstream;

  const response = new NextResponse(upstream.body, { status: upstream.status, headers: upstream.headers });
  setSessionCookies(response, session.refreshed);
  return response;
}

export { handle as DELETE, handle as GET, handle as POST, handle as PUT };
