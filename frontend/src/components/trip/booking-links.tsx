import { ExternalLink } from "lucide-react";

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { BookingLink } from "@/lib/api/types";

/** Search links to booking sites. Sponsored (affiliate) links are marked rel="sponsored" and disclosed. */
export function BookingLinks({ links }: { links: BookingLink[] }) {
  if (!links.length) return null;
  const sponsored = links.some((l) => l.sponsored);
  const groups = [
    ["TRANSPORT", "Transport"],
    ["STAY", "Stays"],
  ] as const;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Check live prices</CardTitle>
        <CardDescription>Opens the provider&apos;s search for your dates. TripWise doesn&apos;t book or take payments.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        {groups.map(([kind, label]) => {
          const items = links.filter((l) => l.kind === kind);
          if (!items.length) return null;
          return (
            <div key={kind}>
              <p className="mb-2 text-sm font-medium">{label}</p>
              <ul className="flex flex-wrap gap-2">
                {items.map((l) => (
                  <li key={l.url}>
                    <a
                      href={l.url}
                      target="_blank"
                      rel={l.sponsored ? "sponsored noopener noreferrer" : "noopener noreferrer"}
                      className="inline-flex items-center gap-1 rounded-md border border-border px-3 py-1.5 text-sm hover:bg-muted"
                    >
                      {l.label}
                      <ExternalLink className="h-3 w-3" aria-hidden />
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          );
        })}
        {sponsored && (
          <p className="text-xs text-muted-foreground" data-testid="affiliate-disclosure">
            Some links are affiliate links: we may earn a commission at no extra cost to you.
          </p>
        )}
      </CardContent>
    </Card>
  );
}
