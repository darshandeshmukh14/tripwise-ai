import type {
  ChatMessage,
  ChatReply,
  ClarifyResponse,
  CreateTripRequest,
  Me,
  Page,
  StopSuggestions,
  TripDetail,
  TripStatusResponse,
  TripSummary,
} from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly title: string,
    readonly detail: string,
    readonly fieldErrors: Record<string, string> = {},
    /** Route rule violations (422 "Stops too far apart"). */
    readonly violations: string[] = [],
  ) {
    super(detail || title);
    this.name = "ApiError";
  }
}

async function toApiError(res: Response): Promise<ApiError> {
  let body: { title?: string; detail?: string; errors?: Record<string, string>; violations?: unknown } = {};
  try {
    body = await res.json();
  } catch {
    // non-JSON error body
  }
  const fallback = res.status === 429 ? "Too many requests. Please wait a moment and try again." : "Something went wrong.";
  const violations = Array.isArray(body.violations)
    ? body.violations.filter((v): v is string => typeof v === "string")
    : [];
  return new ApiError(res.status, body.title ?? res.statusText, body.detail ?? fallback, body.errors ?? {}, violations);
}

async function request<T>(url: string, method = "GET", body?: unknown): Promise<T> {
  const res = await fetch(url, {
    method,
    headers: body === undefined ? undefined : { "content-type": "application/json" },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!res.ok) throw await toApiError(res);
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

const backend = <T>(path: string, method?: string, body?: unknown) => request<T>(`/api/backend/${path}`, method, body);

export const authApi = {
  login: (body: { email: string; password: string }) => request<Me>("/api/auth/login", "POST", body),
  register: (body: { fullName: string; email: string; password: string }) =>
    request<Me>("/api/auth/register", "POST", body),
  logout: () => request<void>("/api/auth/logout", "POST"),
  me: () => backend<Me>("auth/me"),
};

export const tripsApi = {
  list: (page = 0, size = 12) => backend<Page<TripSummary>>(`trips?page=${page}&size=${size}`),
  get: (id: number) => backend<TripDetail>(`trips/${id}`),
  create: (body: CreateTripRequest) => backend<TripDetail>("trips", "POST", body),
  update: (id: number, body: CreateTripRequest) => backend<TripDetail>(`trips/${id}`, "PUT", body),
  remove: (id: number) => backend<void>(`trips/${id}`, "DELETE"),
  clarify: (id: number) => backend<ClarifyResponse>(`trips/${id}/clarify`, "POST"),
  generate: (id: number, answers: Record<string, string>) =>
    backend<TripStatusResponse>(`trips/${id}/generate`, "POST", { answers }),
  status: (id: number) => backend<TripStatusResponse>(`trips/${id}/status`),
  suggestStops: (id: number) => backend<StopSuggestions>(`trips/${id}/stops/suggest`, "POST"),
  /** Newest first. */
  messages: (id: number, page = 0, size = CHAT_PAGE_SIZE) =>
    backend<Page<ChatMessage>>(`trips/${id}/messages?page=${page}&size=${size}`),
  /** `expectedVersion`: itinerary version the user is looking at; a newer plan on the server answers 409. */
  sendMessage: (id: number, content: string, expectedVersion?: number) =>
    backend<ChatReply>(`trips/${id}/messages`, "POST", { content, expectedVersion }),
};

export const CHAT_PAGE_SIZE = 30;

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.detail;
  return "Something went wrong. Please try again.";
}
