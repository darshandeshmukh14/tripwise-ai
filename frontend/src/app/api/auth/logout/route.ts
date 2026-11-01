import { NextResponse, type NextRequest } from "next/server";

import { clearSessionCookies, forwardToBackend, REFRESH_COOKIE, rejectCrossOrigin } from "@/lib/server/bff";

/** Revokes the refresh-token session in Spring (best effort) and clears both cookies. */
export async function POST(request: NextRequest) {
  const rejected = rejectCrossOrigin(request);
  if (rejected) return rejected;

  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;
  if (refreshToken) {
    const body = JSON.stringify({ refreshToken });
    const logout = new Request(request.url, { method: "POST", headers: { "content-type": "application/json" }, body });
    await forwardToBackend(logout, "/api/v1/auth/logout");
  }
  const response = new NextResponse(null, { status: 204 });
  clearSessionCookies(response);
  return response;
}
