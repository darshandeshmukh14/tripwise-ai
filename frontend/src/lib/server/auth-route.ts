import { NextResponse, type NextRequest } from "next/server";

import { forwardToBackend, isBackendAuth, rejectCrossOrigin, setSessionCookies } from "@/lib/server/bff";

/**
 * Shared by /api/auth/login and /api/auth/register: the access and refresh tokens go into httpOnly cookies,
 * never to the browser JS.
 */
export async function authenticate(request: NextRequest, backendPath: string): Promise<Response> {
  const rejected = rejectCrossOrigin(request);
  if (rejected) return rejected;

  const upstream = await forwardToBackend(request, backendPath);
  if (!upstream.ok) return upstream;

  const auth: unknown = await upstream.json().catch(() => null);
  if (!isBackendAuth(auth)) {
    return Response.json({ title: "Authentication failed", status: 502 }, { status: 502 });
  }
  const response = NextResponse.json(
    { userId: auth.userId, email: auth.email, fullName: auth.fullName },
    { status: upstream.status },
  );
  setSessionCookies(response, auth);
  return response;
}
