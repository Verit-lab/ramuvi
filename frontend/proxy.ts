import { NextResponse, type NextRequest } from "next/server";
import { legacyTeacherRedirect } from "./lib/legacy-routes";
import { contentSecurityPolicy } from "./lib/security-headers";

/**
 * Sets the Content-Security-Policy per request so connect-src can name the
 * host that served the page (see lib/security-headers.ts). /api and /ws are
 * excluded: they are rewritten to the backend and WebSocket upgrades must not
 * pass through the proxy.
 *
 * v0.12: teacher deep links that still point at "/" (the teacher area before
 * the public landing page) are sent to "/teach" with the same query. The
 * redirect is temporary (307) while the new entry points are in homologation.
 */
export function proxy(request: NextRequest) {
  const legacyTarget = legacyTeacherRedirect(request.nextUrl.pathname, request.nextUrl.search);
  let response: NextResponse;
  if (legacyTarget) {
    // Same origin by construction: only the path and query are replaced.
    const target = request.nextUrl.clone();
    const [pathname, query = ""] = legacyTarget.split("?");
    target.pathname = pathname;
    target.search = query ? `?${query}` : "";
    response = NextResponse.redirect(target, 307);
  } else {
    response = NextResponse.next();
  }
  response.headers.set("Content-Security-Policy", contentSecurityPolicy(
    process.env.NEXT_PUBLIC_API_URL,
    process.env.NODE_ENV !== "production",
    request.headers.get("host"),
  ));
  return response;
}

export const config = {
  matcher: ["/((?!api/|ws/|_next/static|_next/image).*)"],
};
