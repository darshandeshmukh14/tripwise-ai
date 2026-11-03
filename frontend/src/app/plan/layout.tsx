import { Plane } from "lucide-react";
import Link from "next/link";

/** Public layout: planning without an account, so no session-dependent header. */
export default function PlanLayout({ children }: LayoutProps<"/plan">) {
  return (
    <>
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex h-14 max-w-6xl items-center justify-between px-4">
          <Link href="/" className="flex items-center gap-2 font-semibold">
            <Plane className="h-5 w-5 text-primary" aria-hidden />
            TripWise AI
          </Link>
          <nav className="flex items-center gap-4 text-sm">
            <Link href="/login" className="hover:underline">
              Sign in
            </Link>
            <Link href="/register" className="font-medium text-primary hover:underline">
              Sign up
            </Link>
          </nav>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">{children}</main>
    </>
  );
}
