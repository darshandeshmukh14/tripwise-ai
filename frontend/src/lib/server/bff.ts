/** Server-only helpers for the BFF route handlers and proxy. Never import from client components. */

import type { NextResponse } from "next/server";

export const SESSION_COOKIE = "tw_session";
export const REFRESH_COOKIE = "tw_refresh";
export const BACKEND_URL = (process.env.BACKEND_URL ?? "http://localhost:8080").replace(/\/+$/, "");
// Chat refinement can wait on the AI service (Spring read timeout 60s); keep a margin above it.
const BACKEND_TIMEOUT_MS = Number(process.env.BACKEND_TIMEOUT_MS ?? 75_000);
const TRUST_PROXY_HEADERS = process.env.TRUST_PROXY_HEADERS === "true";
const FORWARDED_RESPONSE_HEADERS = ["content-type", "retry-after", "x-ratelimit-remaining"];
// Refresh slightly before expiry so the token does not lapse while the request is in flight.
const ACCESS_EXPIRY_MARGIN_MS = 30_000;

/** Spring's AuthResponse (login, register and refresh). */
export interface BackendAuth {
  token: string;
  refreshToken: string;
  refreshTokenExpiresAt: string;
  userId: number;
  email: string;
  fullName: string;
}

/** Reads `exp` (seconds) from a JWT payload without verifying it; Spring verifies the signature. */
export function tokenExpiry(token: string): number | null {
  try {
    const payload = JSON.parse(Buffer.from(token.split(".")[1] ?? "", "base64url").toString("utf8"));
    return typeof payload.exp === "number" ? payload.exp : null;
  } catch {
    return null;
  }
}

export function isSessionValid(token: string | undefined, nowMs = Date.now()): boolean {
  if (!token) return false;
  const exp = tokenExpiry(token);
  return exp !== null && exp * 1000 > nowMs;
}

/** True when the access token is missing, expired or about to expire. */
export function needsRefresh(token: string | undefined, nowMs = Date.now()): boolean {
  return !isSessionValid(token, nowMs + ACCESS_EXPIRY_MARGIN_MS);
}

export function sessionCookie(token: string, nowMs = Date.now()) {
  const exp = tokenExpiry(token);
  const maxAge = exp ? Math.max(0, Math.floor(exp - nowMs / 1000)) : 60 * 60;
  return {
    name: SESSION_COOKIE,
    value: token,
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

/** Path "/" so proxy.ts can see that a session exists while the short-lived access token is expired. */
export function refreshCookie(token: string, expiresAt: string, nowMs = Date.now()) {
  const expiresMs = Date.parse(expiresAt);
  const maxAge = Number.isNaN(expiresMs) ? 60 * 60 : Math.max(0, Math.floor((expiresMs - nowMs) / 1000));
  return {
    name: REFRESH_COOKIE,
    value: token,
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax" as const,
    path: "/",
    maxAge,
  };
}

export function setSessionCookies(response: NextResponse, auth: BackendAuth): void {
  response.cookies.set(sessionCookie(auth.token));
  response.cookies.set(refreshCookie(auth.refreshToken, auth.refreshTokenExpiresAt));
}

export function clearSessionCookies(response: NextResponse): void {
  response.cookies.delete(SESSION_COOKIE);
  response.cookies.delete(REFRESH_COOKIE);
}

export function isBackendAuth(value: unknown): value is BackendAuth {
  const auth = value as Partial<BackendAuth> | null;
  return typeof auth?.token === "string" && typeof auth.refreshToken === "string"
    && typeof auth.refreshTokenExpiresAt === "string";
}

// Parallel requests from one browser share the same refresh cookie; rotate it only once per process.
const inflightRefreshes = new Map<string, Promise<BackendAuth | number>>();

/**
 * Exchanges the refresh token for a new token pair. Returns the HTTP status on failure:
 * 401/400 means the session is over; anything else is a temporary backend problem.
 */
export function refreshSession(request: Request, refreshToken: string): Promise<BackendAuth | number> {
  const pending = inflightRefreshes.get(refreshToken);
  if (pending) return pending;

  const headers = new Headers({ "content-type": "application/json" });
  const forwardedFor = request.headers.get("x-forwarded-for");
  if (forwardedFor) headers.set("x-forwarded-for", forwardedFor);
  const refreshRequest = new Request(request.url, {
    method: "POST",
    headers,
    body: JSON.stringify({ refreshToken }),
  });
  const result = forwardToBackend(refreshRequest, "/api/v1/auth/refresh")
    .then(async (res) => {
      if (!res.ok) return res.status;
      const auth: unknown = await res.json().catch(() => null);
      return isBackendAuth(auth) ? auth : 502;
    })
    .finally(() => inflightRefreshes.delete(refreshToken));
  inflightRefreshes.set(refreshToken, result);
  return result;
}

export function problem(status: number, title: string, detail: string): Response {
  return Response.json({ type: "about:blank", title, status, detail }, {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

/** CSRF defence in depth (cookie is SameSite=Lax): reject cross-origin state-changing requests. */
export function rejectCrossOrigin(request: Request): Response | null {
  if (request.method === "GET" || request.method === "HEAD") return null;
  const origin = request.headers.get("origin");
  if (!origin) return null;
  try {
    if (new URL(origin).host === request.headers.get("host")) return null;
  } catch {
    // malformed Origin header
  }
  return problem(403, "Forbidden", "Cross-origin request rejected.");
}

/**
 * Forwards the request body to Spring and returns a sanitised response (only safe headers copied).
 * Pass `body` when the request stream was already read (e.g. to retry after a token refresh).
 */
export async function forwardToBackend(
  request: Request,
  backendPath: string,
  token?: string,
  body?: string,
): Promise<Response> {
  const headers = new Headers({ accept: "application/json" });
  const contentType = request.headers.get("content-type");
  if (contentType) headers.set("content-type", contentType);
  if (token) headers.set("authorization", `Bearer ${token}`);
  const forwardedFor = request.headers.get("x-forwarded-for");
  if (TRUST_PROXY_HEADERS && forwardedFor) headers.set("x-forwarded-for", forwardedFor);

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const payload = hasBody ? (body ?? (await request.text())) : "";

  let upstream: Response;
  try {
    upstream = await fetch(`${BACKEND_URL}${backendPath}`, {
      method: request.method,
      headers,
      body: payload || undefined,
      cache: "no-store",
      redirect: "manual",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "TimeoutError") {
      return problem(504, "Backend timeout", "The server took too long to respond. Please try again.");
    }
    return problem(502, "Backend unavailable", "The server is unreachable. Please try again later.");
  }

  const out = new Headers();
  for (const name of FORWARDED_RESPONSE_HEADERS) {
    const value = upstream.headers.get(name);
    if (value) out.set(name, value);
  }
  const noBody = upstream.status === 204 || upstream.status === 304;
  return new Response(noBody ? null : await upstream.arrayBuffer(), { status: upstream.status, headers: out });
}
