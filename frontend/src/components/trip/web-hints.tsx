import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { WebHint } from "@/lib/api/types";

/**
 * Web price hints from Gemini Google Search grounding. The search suggestions chip must be shown as provided,
 * so it is rendered in a sandboxed iframe (no scripts, no same-origin access).
 */
export function WebHints({ hints }: { hints: WebHint[] }) {
  if (!hints.length) return null;
  const suggestions = hints.find((h) => h.searchSuggestionsHtml)?.searchSuggestionsHtml;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Prices seen on the web</CardTitle>
        <CardDescription>Found by web search; may be outdated. Always confirm on the provider&apos;s site.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        <ul className="space-y-1 text-sm">
          {hints.map((h) => (
            <li key={`${h.label}-${h.sourceUrl}`}>
              {h.label} —{" "}
              <a href={h.sourceUrl} target="_blank" rel="noopener noreferrer nofollow" className="text-primary underline">
                {h.sourceTitle || "source"}
              </a>
            </li>
          ))}
        </ul>
        {suggestions && (
          <iframe
            title="Google search suggestions"
            sandbox="allow-popups allow-popups-to-escape-sandbox"
            srcDoc={suggestions}
            className="h-24 w-full rounded-md border border-border"
          />
        )}
      </CardContent>
    </Card>
  );
}
