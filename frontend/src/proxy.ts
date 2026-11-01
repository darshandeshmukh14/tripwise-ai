import { NextResponse, type NextRequest } from "next/server";

import { clearSessionCookies, isSessionValid, REFRESH_COOKIE, SESSION_COOKIE } from "@/lib/server/bff";

/**
 * Optimistic redirects only; Spring enforces authentication and ownership on every API call. A refresh cookie
 * counts as signed in: the BFF renews the short-lived access token on the next API call.
 */
export function proxy(request: NextRequest) {
  const signedIn = isSessionValid(request.cookies.get(SESSION_COOKIE)?.value)
    || Boolean(request.cookies.get(REFRESH_COOKIE)?.value);
  const { pathname } = request.nextUrl;

  if (pathname.startsWith("/trips") && !signedIn) {
    const response = NextResponse.redirect(new URL("/login", request.url));
    clearSessionCookies(response);
    return response;
  }
  if ((pathname === "/login" || pathname === "/register") && signedIn) {
    return NextResponse.redirect(new URL("/trips", request.url));
  }
  return NextResponse.next();
}

export const config = {
  matcher: ["/trips/:path*", "/login", "/register"],
};
