"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useState } from "react";

import { ApiError } from "@/lib/api/client";

export function makeQueryClient(onUnauthorized: () => void = () => {}) {
  // An expired session surfaces as 401 from the BFF (which also clears the cookie): send the user to sign in.
  const onError = (error: Error) => {
    if (error instanceof ApiError && error.status === 401) onUnauthorized();
  };
  return new QueryClient({
    queryCache: new QueryCache({ onError }),
    mutationCache: new MutationCache({ onError }),
    defaultOptions: {
      queries: {
        staleTime: 10_000,
        refetchOnWindowFocus: false,
        // Client errors (4xx) are final; retry transient server/gateway errors only.
        retry: (failureCount, error) => failureCount < 2 && error instanceof ApiError && error.status >= 500,
      },
    },
  });
}

export function Providers({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const [client] = useState(() => {
    const queryClient = makeQueryClient(() => {
      queryClient.clear();
      router.push("/login");
    });
    return queryClient;
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
