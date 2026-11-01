"use client";

import { useInfiniteQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Send } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

import { Alert } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Textarea } from "@/components/ui/form-controls";
import { ApiError, errorMessage, tripsApi } from "@/lib/api/client";
import type { ChatMessage } from "@/lib/api/types";
import { cn } from "@/lib/utils";

const SUGGESTIONS = ["Make it cheaper", "Go by train instead", "Make the days more relaxed"];
const MAX_LENGTH = 2000;

/** The API pages newest first; new messages shift offsets, so later pages can repeat ids. */
export function chronological(pages: { content: ChatMessage[] }[]): ChatMessage[] {
  const byId = new Map<number, ChatMessage>();
  for (const page of pages) for (const m of page.content) if (!byId.has(m.id)) byId.set(m.id, m);
  return [...byId.values()].filter((m) => m.senderRole !== "SYSTEM").sort((a, b) => a.id - b.id);
}

export function ChatPanel({ tripId, itineraryVersion }: { tripId: number; itineraryVersion?: number }) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);
  const messages = useInfiniteQuery({
    queryKey: ["trip", tripId, "messages"],
    queryFn: ({ pageParam }) => tripsApi.messages(tripId, pageParam),
    initialPageParam: 0,
    getNextPageParam: (last) => (last.page + 1 < last.totalPages ? last.page + 1 : undefined),
  });

  const send = useMutation({
    mutationFn: (content: string) => tripsApi.sendMessage(tripId, content, itineraryVersion),
    onSuccess: async (reply) => {
      setDraft("");
      await queryClient.invalidateQueries({ queryKey: ["trip", tripId, "messages"] });
      if (reply.itineraryUpdated) await queryClient.invalidateQueries({ queryKey: ["trip", tripId] });
    },
    onError: async (error) => {
      // The plan changed elsewhere (another tab or a regenerate): load it so the next message targets it.
      if (error instanceof ApiError && error.status === 409) {
        await queryClient.invalidateQueries({ queryKey: ["trip", tripId] });
      }
    },
  });

  function submit(content: string) {
    const text = content.trim();
    if (text && !send.isPending) send.mutate(text);
  }

  const list = useMemo(() => chronological(messages.data?.pages ?? []), [messages.data]);
  const latestId = list.at(-1)?.id;
  useEffect(() => {
    bottomRef.current?.scrollIntoView?.({ block: "nearest" });
  }, [latestId, send.isPending]);

  return (
    <Card className="flex flex-col">
      <CardHeader>
        <CardTitle>Refine with chat</CardTitle>
        <CardDescription>Ask for changes — costs are re-estimated automatically.</CardDescription>
      </CardHeader>
      <CardContent className="flex flex-1 flex-col gap-3">
        <div className="max-h-96 min-h-24 space-y-2 overflow-y-auto" aria-live="polite">
          {messages.hasNextPage && (
            <Button
              variant="ghost"
              size="sm"
              className="w-full"
              disabled={messages.isFetchingNextPage}
              onClick={() => void messages.fetchNextPage()}
            >
              {messages.isFetchingNextPage ? "Loading…" : "Load older messages"}
            </Button>
          )}
          {list.length === 0 && !messages.isLoading && (
            <p className="text-sm text-muted-foreground">No messages yet. Try a suggestion below.</p>
          )}
          {list.map((m) => (
            <div
              key={m.id}
              className={cn(
                "max-w-[85%] whitespace-pre-wrap rounded-lg px-3 py-2 text-sm",
                m.senderRole === "USER" ? "ml-auto bg-primary text-primary-foreground" : "bg-muted",
              )}
            >
              {m.content}
            </div>
          ))}
          {send.isPending && <p className="text-sm text-muted-foreground">Thinking…</p>}
          <div ref={bottomRef} />
        </div>
        {messages.error && <Alert variant="destructive">{errorMessage(messages.error)}</Alert>}
        {send.error && <Alert variant="destructive">{errorMessage(send.error)}</Alert>}
        <div className="flex flex-wrap gap-2">
          {SUGGESTIONS.map((s) => (
            <Button key={s} variant="outline" size="sm" disabled={send.isPending} onClick={() => submit(s)}>
              {s}
            </Button>
          ))}
        </div>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            submit(draft);
          }}
        >
          <Textarea
            aria-label="Message"
            className="min-h-10 flex-1"
            rows={2}
            maxLength={MAX_LENGTH}
            value={draft}
            placeholder="e.g. Add a beach day and skip the museum"
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && !e.shiftKey) {
                e.preventDefault();
                submit(draft);
              }
            }}
          />
          <Button type="submit" size="icon" aria-label="Send" disabled={send.isPending || !draft.trim()}>
            <Send className="h-4 w-4" aria-hidden />
          </Button>
        </form>
      </CardContent>
    </Card>
  );
}
