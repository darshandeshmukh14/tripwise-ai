import { BadgeIndianRupee, MessagesSquare, Plane, Route } from "lucide-react";
import Link from "next/link";

const FEATURES = [
  { icon: Route, title: "Compare how you travel", text: "Train, flight, bus or car — side by side with time, comfort and cost." },
  { icon: BadgeIndianRupee, title: "Season-aware estimates", text: "Hotel, food and transport ranges that adjust for peak and off season." },
  { icon: MessagesSquare, title: "Refine by chat", text: "“Make it cheaper”, “go by bus” — the plan and costs update instantly." },
];

export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-5xl flex-1 flex-col justify-center gap-12 px-4 py-16">
      <section className="space-y-6 text-center">
        <p className="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-sm font-medium text-primary">
          <Plane className="h-4 w-4" aria-hidden /> TripWise AI
        </p>
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">Plan the trip. Know the cost.</h1>
        <p className="mx-auto max-w-2xl text-lg text-muted-foreground">
          Tell us your budget, comfort level and how you like to travel. Our planning agents build a day-by-day
          itinerary with transparent cost estimates.
        </p>
        <div className="flex justify-center gap-3">
          <Link
            href="/register"
            className="inline-flex h-11 items-center rounded-md bg-primary px-6 font-medium text-primary-foreground hover:bg-primary/90"
          >
            Get started
          </Link>
          <Link href="/login" className="inline-flex h-11 items-center rounded-md border border-border px-6 font-medium hover:bg-muted">
            Sign in
          </Link>
        </div>
      </section>
      <section className="grid gap-4 sm:grid-cols-3">
        {FEATURES.map(({ icon: Icon, title, text }) => (
          <div key={title} className="rounded-lg border border-border bg-card p-5">
            <Icon className="mb-3 h-6 w-6 text-primary" aria-hidden />
            <h2 className="font-semibold">{title}</h2>
            <p className="mt-1 text-sm text-muted-foreground">{text}</p>
          </div>
        ))}
      </section>
      <p className="text-center text-xs text-muted-foreground">
        All prices are indicative estimates, not live quotes or bookings.
      </p>
    </main>
  );
}
