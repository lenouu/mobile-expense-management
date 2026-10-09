import { resolveApiBaseUrl } from '@/constants/api';

// Shared HTTP plumbing for every backend call (admin and user screens alike).

/** Spring Data's PagedModel envelope, used by every paged backend endpoint. */
export type PagedModel<T> = {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};

export type AccountType = 'USER' | 'ADMIN';

/** What a successful POST /auth/login leaves us with. */
export type Session = { accessToken: string; userName: string; type: AccountType };

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
    /** Per-field messages when the backend rejected a request body (400 validation_failed). */
    readonly fieldErrors: Record<string, string> = {},
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
  } catch {
    throw new ApiRequestError(0, `Cannot reach the server at ${baseUrl}`);
  }

  if (!response.ok) {
    // Backend errors are { status, error, message, ... } (see backend Exceptions/ApiError).
    const body = await response.json().catch(() => null);
    throw new ApiRequestError(
      response.status,
      body?.message ?? `Request failed (${response.status})`,
      body?.fieldErrors ?? {},
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
  user: { userName: string; type: AccountType };
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
