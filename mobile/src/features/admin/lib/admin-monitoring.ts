import { ApiRequestError, authRequest, login } from '@/features/auth/lib/api-client';
import type { PagedModel, Session } from '@/types/api';

// Re-exported so callers that only need the shared plumbing do not have to import two modules.
export { ApiRequestError, type PagedModel } from '@/features/auth/lib/api-client';

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

/** An admin session is an ordinary session; the alias keeps the admin screens readable. */
export type AdminSession = Session;

/**
 * Logs in with POST /auth/login and makes sure the account is an ADMIN.
 * A USER token would be refused by every /admin endpoint anyway; checking here gives a clear message.
 */
export async function adminLogin(usernameOrEmail: string, password: string): Promise<AdminSession> {
  const session = await login(usernameOrEmail, password);
  if (session.type !== 'ADMIN') {
    throw new ApiRequestError(403, 'This account is not an administrator.');
  }
  return session;
}

/** Authenticated call to an /admin endpoint; a JSON `body` is serialized for you. */
export const adminRequest = authRequest;

function adminGet<T>(path: string, session: AdminSession) {
  return adminRequest<T>(path, session);
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