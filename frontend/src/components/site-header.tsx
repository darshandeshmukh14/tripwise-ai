"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { LogOut, Plane } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { authApi } from "@/lib/api/client";

export function SiteHeader() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const me = useQuery({ queryKey: ["me"], queryFn: authApi.me, staleTime: 5 * 60_000 });
  const [signingOut, setSigningOut] = useState(false);

  async function signOut() {
    setSigningOut(true);
    try {
      await authApi.logout();
    } finally {
      queryClient.clear();
      router.push("/login");
    }
  }

  return (
    <header className="border-b border-border bg-card">
      <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
        <Link href="/trips" className="flex items-center gap-2 font-semibold">
          <Plane className="h-5 w-5 text-primary" aria-hidden />
          TripWise AI
        </Link>
        <div className="flex items-center gap-3">
          {me.data && <span className="hidden text-sm text-muted-foreground sm:inline">{me.data.fullName}</span>}
          <Button variant="ghost" size="sm" onClick={signOut} disabled={signingOut}>
            <LogOut className="h-4 w-4" aria-hidden />
            Sign out
          </Button>
        </div>
      </div>
    </header>
  );
}
