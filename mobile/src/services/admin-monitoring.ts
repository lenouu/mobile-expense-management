import { API_BASE_URL } from '@/constants/api';

// Shapes returned by POST /auth/login, GET /admin/health and GET /admin/logs/errors (US10).

export type HealthStatus = 'UP' | 'DOWN';

export type PlatformHealth = {
  status: HealthStatus;
  timestamp: string;
  applicationName: string;
  version: string;
  uptimeSeconds: number;
  database: { status: HealthStatus; responseTimeMs?: number; error?: string };
  memory: { status: HealthStatus; usedBytes: number; maxBytes: number; usagePercent: number };
  disk: { status: HealthStatus; freeBytes: number; totalBytes: number };
  /** Null while the database cannot be read. */
  errorsLast24Hours: number | null;
};

export type ErrorLogEntry = {
  id: number;
  occurredAt: string;
  httpMethod: string | null;
  path: string | null;
  status: number;
  exceptionType: string;
  message: string | null;
  stackTrace: string | null;
};

/** Spring Data's PagedModel envelope, used by every paged backend endpoint. */
export type PagedModel<T> = {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};

export type AdminSession = { accessToken: string; userName: string };

export class ApiRequestError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...init.headers },
    });
  } catch {
    throw new ApiRequestError(0, `Cannot reach the server at ${API_BASE_URL}`);
  }

  if (!response.ok) {
    // Backend errors are { status, error, message, ... } (see backend Exceptions/ApiError).
    const body = await response.json().catch(() => null);
    throw new ApiRequestError(response.status, body?.message ?? `Request failed (${response.status})`);
  }
  return response.json();
}

type LoginResponse = {
  accessToken: string;
  user: { userName: string; type: 'USER' | 'ADMIN' };
};

/**
 * Logs in with POST /auth/login and makes sure the account is an ADMIN.
 * A USER token would be refused by every /admin endpoint anyway; checking here gives a clear message.
 */
export async function adminLogin(usernameOrEmail: string, password: string): Promise<AdminSession> {
  const result = await request<LoginResponse>('/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ usernameOrEmail, password }),
  });
  if (result.user.type !== 'ADMIN') {
    throw new ApiRequestError(403, 'This account is not an administrator.');
  }
  return { accessToken: result.accessToken, userName: result.user.userName };
}

function adminGet<T>(path: string, session: AdminSession) {
  return request<T>(path, { headers: { Authorization: `Bearer ${session.accessToken}` } });
}

export function fetchPlatformHealth(session: AdminSession) {
  return adminGet<PlatformHealth>('/admin/health', session);
}

export function fetchErrorLogs(
  session: AdminSession,
  options: { from?: Date; page?: number; size?: number } = {},
) {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  });
  if (options.from) {
    params.set('from', options.from.toISOString());
  }
  return adminGet<PagedModel<ErrorLogEntry>>(`/admin/logs/errors?${params}`, session);
}
