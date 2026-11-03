import type { NextRequest } from "next/server";

import { forwardToBackend, problem, rejectCrossOrigin } from "@/lib/server/bff";

// Planning without an account: only these public Spring endpoints, POST only, never with a token.
const ALLOWED_PATH = /^(clarify|generate|messages|stops\/suggest)$/;

type Context = { params: Promise<{ path: string[] }> };

export async function POST(request: NextRequest, { params }: Context): Promise<Response> {
  const { path } = await params;
  const joined = path.join("/");
  if (!ALLOWED_PATH.test(joined)) {
    return problem(404, "Not found", "Unknown API path.");
  }
  const rejected = rejectCrossOrigin(request);
  if (rejected) return rejected;
  return forwardToBackend(request, `/api/v1/guest/${joined}`);
}
