import { resolveApiBaseUrl } from '@/constants/api';
import type { Session } from '@/types/api';

// Shared HTTP plumbing for every backend call (admin and user screens alike).

// The shared vocabulary lives in `types/api`, so there is exactly one definition of each shape.
export type { AccountType, Page, Session } from '@/types/api';

/**
 * A failed request.
 *
 * `fields` mirrors the backend's `fields` object: a field path (`amount.amount`, `date`, ...) to
 * the reason it failed. The path can be dotted, because a nested value such as money is reported
 * where the invalid part actually sits.
 */
export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** Per-field messages when the backend rejected a request body (400 validation_failed). */
    readonly fields: Record<string, string> = {},
    /** Stable machine-readable token such as `validation_failed` or `not_found`. */
    readonly code: string | null = null,
  ) {
    super(message);
  }
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  // Resolved per call rather than once at import, so the address always matches the network the
  // app was actually started on, and a Fast Refresh picks up a changed override.
  const baseUrl = resolveApiBaseUrl();

  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...init.headers },
    });
  } catch (error) {
    // The underlying reason is appended on purpose. A bare "cannot reach the server" hid a real
    // failure once already: the request was fine, the message was not informative enough.
    const detail = error instanceof Error && error.message ? ` — ${error.message}` : '';
    throw new ApiRequestError(0, `Cannot reach the server at ${baseUrl}${detail}`);
  }

  if (!response.ok) {
    // Backend errors are { code, message, fields } (see backend Exceptions/ApiError).
    const body = await response.json().catch(() => null);
    throw new ApiRequestError(
      response.status,
      body?.message ?? `Request failed (${response.status})`,
      body?.fields ?? {},
      body?.code ?? null,
    );
  }
  // 204 No Content (e.g. DELETE) has no body to parse.
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

type LoginResponse = {
  accessToken: string;
  user: { userName: string; type: 'USER' | 'ADMIN' };
};

/** POST /auth/login. Callers decide whether the account type suits their screen. */
export async function login(usernameOrEmail: string, password: string): Promise<Session> {
  const result = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usernameOrEmail, password }),
  });
  return { accessToken: result.accessToken, userName: result.user.userName, type: result.user.type };
}

/** Call with the session's Bearer token; a JSON `body` is serialized for you. */
export function authRequest<T>(
  path: string,
  session: Session,
  options: { method?: string; body?: unknown } = {},
) {
  const headers: Record<string, string> = { Authorization: `Bearer ${session.accessToken}` };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  return request<T>(path, {
    method: options.method ?? 'GET',
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });
}
