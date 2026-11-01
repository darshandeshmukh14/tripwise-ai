import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { CostBreakdown } from "@/lib/api/types";
import { CATEGORY_LABEL, CONFIDENCE_VARIANT, formatMoney, formatRange, SOURCE_META, titleCase } from "@/lib/format";

export function CostBreakdownTable({ breakdown, stopNames = {} }: { breakdown: CostBreakdown; stopNames?: Record<number, string> }) {
  const currency = breakdown.currency || "INR";
  return (
    <Card>
      <CardHeader>
        <CardTitle>Cost breakdown</CardTitle>
        <CardDescription>Every line has a range, a source (estimate, fare rule or web hint) and a confidence level.</CardDescription>
      </CardHeader>
      <CardContent className="overflow-x-auto">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-border text-left text-muted-foreground">
              <th className="py-2 pr-4 font-medium">Item</th>
              <th className="py-2 pr-4 font-medium">Expected</th>
              <th className="py-2 pr-4 font-medium">Range</th>
              <th className="py-2 font-medium">Confidence</th>
            </tr>
          </thead>
          <tbody>
            {breakdown.items.map((item, i) => (
              <tr key={i} className="border-b border-border/60 align-top">
                <td className="py-2 pr-4">
                  <p className="font-medium">{item.label}</p>
                  <p className="text-xs text-muted-foreground">
                    {CATEGORY_LABEL[item.category]}
                    {item.mode ? ` · ${titleCase(item.mode)}` : ""} · {titleCase(item.season)} season
                    {item.stopSeq != null && stopNames[item.stopSeq] ? ` · ${stopNames[item.stopSeq]}` : ""}
                  </p>
                  {item.assumptions && <p className="text-xs text-muted-foreground">{item.assumptions}</p>}
                  {item.sourceUrl && (
                    <a
                      href={item.sourceUrl}
                      target="_blank"
                      rel="noopener noreferrer nofollow"
                      className="text-xs text-primary underline"
                    >
                      Price source
                    </a>
                  )}
                </td>
                <td className="py-2 pr-4 font-medium">{formatMoney(item.amountExpected, currency)}</td>
                <td className="py-2 pr-4 whitespace-nowrap text-muted-foreground">
                  {formatRange(item.amountMin, item.amountMax, currency)}
                </td>
                <td className="py-2">
                  <div className="flex flex-wrap gap-1">
                    <Badge variant={SOURCE_META[item.source].variant}>{SOURCE_META[item.source].label}</Badge>
                    <Badge variant={CONFIDENCE_VARIANT[item.confidence]}>{titleCase(item.confidence)}</Badge>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr className="font-semibold">
              <td className="py-2 pr-4">Total</td>
              <td className="py-2 pr-4">{formatMoney(breakdown.totalExpected, currency)}</td>
              <td className="py-2 pr-4 whitespace-nowrap">{formatRange(breakdown.totalMin, breakdown.totalMax, currency)}</td>
              <td />
            </tr>
          </tfoot>
        </table>
      </CardContent>
    </Card>
  );
}
