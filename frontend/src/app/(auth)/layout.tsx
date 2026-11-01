import { Plane } from "lucide-react";
import Link from "next/link";

export default function AuthLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 px-4 py-12">
      <Link href="/" className="flex items-center gap-2 text-lg font-semibold">
        <Plane className="h-5 w-5 text-primary" aria-hidden /> TripWise AI
      </Link>
      <div className="w-full max-w-sm">{children}</div>
    </main>
  );
}
