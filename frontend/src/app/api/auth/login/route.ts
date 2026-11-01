import type { NextRequest } from "next/server";

import { authenticate } from "@/lib/server/auth-route";

export async function POST(request: NextRequest) {
  return authenticate(request, "/api/v1/auth/login");
}
