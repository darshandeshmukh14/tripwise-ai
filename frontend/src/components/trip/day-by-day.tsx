import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type { DayPlan } from "@/lib/api/types";
import { formatDate, titleCase } from "@/lib/format";

export function DayByDay({ days }: { days: DayPlan[] }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Day by day</CardTitle>
      </CardHeader>
      <CardContent>
        <ol className="space-y-6">
          {days.map((d) => (
            <li key={d.day} className="border-l-2 border-primary/40 pl-4">
              <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                Day {d.day}
                {d.date ? ` · ${formatDate(d.date)}` : ""}
              </p>
              <h3 className="font-semibold">{d.title}</h3>
              {d.summary && <p className="text-sm text-muted-foreground">{d.summary}</p>}
              {!!d.activities.length && (
                <ul className="mt-2 space-y-1 text-sm">
                  {d.activities.map((a, i) => (
                    <li key={i}>
                      <span className="mr-2 inline-block w-20 text-xs text-muted-foreground">{titleCase(a.timeOfDay)}</span>
                      <span className="font-medium">{a.title}</span>
                      {a.description && <span className="text-muted-foreground"> — {a.description}</span>}
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  );
}
