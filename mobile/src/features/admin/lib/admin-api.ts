import { ApiRequestError, authRequest, login } from '@/features/auth/lib/api-client';
import type { Account, AccountState, AccountType, PagedModel, Session } from '@/types/api';

// Every endpoint under /api/admin/**, in one module.
//
// Grouped deliberately: the admin screens are the only caller, and having the whole surface in
// one file makes it obvious when a backend route has no client. Sections follow the backend
// controllers - monitoring, user administration, default categories.
//
// All of these require an ADMIN token. SecurityConfig refuses a USER token with 403 and a
// missing token with 401, and each controller repeats the check with @PreAuthorize.

/** Re-exported so callers that only need shared plumbing import one module. */
export { ApiRequestError, type PagedModel } from '@/features/auth/lib/api-client';

/** An admin session is an ordinary session; the alias keeps the admin screens readable. */
export type AdminSession = Session;

/** How the backend reports a component: UP or DOWN. */
export type HealthStatus = 'UP' | 'DOWN';

/** Body of GET /api/admin/health. */
export type PlatformHealth = {
  /** DOWN as soon as any component is DOWN. */
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

/** One entry of GET /api/admin/logs/errors. */
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

/**
 * A row of GET /api/admin/users - `UserResponses.Summary`.
 *
 * No email: that lives on the details payload, which is why the account detail view fetches
 * `GET /admin/users/{id}` separately.
 *
 * `createdAt` is optional because the backend's `Summary` DTO does not declare it even though the
 * running API returns it. Treating it as optional keeps the signup-trend charts honest about what
 * they can rely on: they read every account the API hands back and simply count nothing when the
 * field is absent.
 */
export type UserSummary = {
  id: number;
  userName: string;
  firstName: string;
  lastName: string;
  profilePictureReference: string | null;
  status: AccountState;
  type: AccountType;
  createdAt?: string;
};

/** Body of PATCH /api/admin/users/{id}/status. `reason` is logged, never stored. */
export type ChangeAccountStatus = { status: AccountState; reason?: string };

/** Body of PATCH /api/admin/users/{id}/type. */
export type ChangeAccountType = { type: AccountType; reason?: string };

/** A default expense category, as the admin API returns it (`CategoryResponses.Details`). */
export type DefaultCategory = {
  id: number;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  active: boolean;
  createdAt: string;
  updatedAt: string;
};

/** Body of POST and PUT /api/admin/categories. PUT is a full replace. */
export type SaveCategory = {
  name: string;
  description?: string;
  icon?: string;
  color?: string;
};

/**
 * Logs in with POST /auth/login and insists the account is an ADMIN.
 *
 * A USER token would be refused by every admin route anyway; checking here turns a wall of 403s
 * into one clear message. The login screen does its own routing and does not use this.
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

// ---------------------------------------------------------------------------
// Monitoring: /api/admin/health, /api/admin/logs/errors
// ---------------------------------------------------------------------------

/**
 * GET /api/admin/health.
 *
 * Always 200, even with a component DOWN - that is reported in the body, because "the database is
 * down" is exactly what the administrator opened this screen to see.
 */
export function fetchPlatformHealth(session: AdminSession) {
  return adminGet<PlatformHealth>('/admin/health', session);
}

export type ErrorQuery = {
  /** Only errors at or after this time. The backend takes an ISO 8601 instant. */
  from?: Date;
  to?: Date;
  page?: number;
  /** Capped at 100 by `spring.data.web.pageable.max-page-size`. */
  size?: number;
};

/** Builds the query string shared by the error log call. */
function errorParams(options: ErrorQuery) {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  });
  if (options.from) params.set('from', options.from.toISOString());
  if (options.to) params.set('to', options.to.toISOString());
  return params;
}

function fetchErrorLogs(session: AdminSession, options: ErrorQuery) {
  return adminGet<PagedModel<ErrorLogEntry>>(`/admin/logs/errors?${errorParams(options)}`, session);
}

/**
 * GET /api/admin/logs/errors, walking every page up to `maxEntries`.
 *
 * The dashboard buckets errors to draw its chart, and a single page cannot say how errors are
 * spread across a week. `maxEntries` bounds the work: the endpoint pages 100 at a time, so four
 * requests covers 400 entries, which is far more than a chart needs.
 */
export async function fetchErrorLogWindow(
  session: AdminSession,
  options: { from?: Date; maxEntries?: number } = {},
): Promise<{ entries: ErrorLogEntry[]; total: number; truncated: boolean }> {
  const pageSize = 100;
  const maxEntries = options.maxEntries ?? 400;

  const entries: ErrorLogEntry[] = [];
  let page = 0;
  let total = 0;

  while (entries.length < maxEntries) {
    const result = await fetchErrorLogs(session, { from: options.from, page, size: pageSize });
    total = result.page.totalElements;
    entries.push(...result.content);
    // Stop when the last page has been read, or when the server returns an empty page.
    if (result.content.length < pageSize || page + 1 >= result.page.totalPages) break;
    page += 1;
  }

  return { entries, total, truncated: total > entries.length };
}

// ---------------------------------------------------------------------------
// User administration: /api/admin/users/**
// ---------------------------------------------------------------------------

export type UserQuery = {
  /** Omit to include soft-deleted accounts, as the backend does. */
  status?: AccountState;
  page?: number;
  size?: number;
  /** Spring Data sort string, e.g. `userName,asc`. */
  sort?: string;
};

/**
 * GET /api/admin/users - a page of account summaries.
 *
 * The response is `PagedModel`, so `page.totalElements` is the count for the current `status`
 * filter. That is how the dashboard gets "total accounts" and "active accounts" without a
 * dedicated count endpoint.
 */
export function fetchUsers(session: AdminSession, options: UserQuery = {}) {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
    sort: options.sort ?? 'userName,asc',
  });
  if (options.status) params.set('status', options.status);
  return adminGet<PagedModel<UserSummary>>(`/admin/users?${params}`, session);
}

/** GET /api/admin/users/search - case-insensitive contains-match on first OR last name. */
export function searchUsers(
  session: AdminSession,
  name: string,
  options: Omit<UserQuery, 'status'> = {},
) {
  const params = new URLSearchParams({
    name,
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
    sort: options.sort ?? 'userName,asc',
  });
  return adminGet<PagedModel<UserSummary>>(`/admin/users/search?${params}`, session);
}

/** GET /api/admin/users/{id} - the full profile, including email and date of birth. */
export function fetchUserById(session: AdminSession, id: number) {
  return adminGet<Account>(`/admin/users/${id}`, session);
}

/**
 * PATCH /api/admin/users/{id}/status - PENDING, ACTIVE, SUSPENDED or DELETED.
 *
 * `reason` has no column on the entity: the backend writes it to the application log, so it is
 * an audit trail rather than stored data.
 */
export function changeUserStatus(session: AdminSession, id: number, request: ChangeAccountStatus) {
  return adminRequest<Account>(`/admin/users/${id}/status`, session, { method: 'PATCH', body: request });
}

/**
 * PATCH /api/admin/users/{id}/type - promote or demote.
 *
 * The backend refuses to demote the last remaining ADMIN with 409, so a 409 here is a real
 * business rule rather than a bug: promote a successor first.
 */
export function changeUserType(session: AdminSession, id: number, request: ChangeAccountType) {
  return adminRequest<Account>(`/admin/users/${id}/type`, session, { method: 'PATCH', body: request });
}

// ---------------------------------------------------------------------------
// Default categories: /api/admin/categories/**
// ---------------------------------------------------------------------------

/** GET /api/admin/categories. Omit `active` to include deactivated categories. */
export function fetchDefaultCategories(
  session: AdminSession,
  options: { active?: boolean; page?: number; size?: number; sort?: string } = {},
) {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 40),
    sort: options.sort ?? 'name,asc',
  });
  if (options.active !== undefined) params.set('active', String(options.active));
  return adminGet<PagedModel<DefaultCategory>>(`/admin/categories?${params}`, session);
}

/** GET /api/admin/categories/{id}. */
export function fetchDefaultCategory(session: AdminSession, id: number) {
  return adminGet<DefaultCategory>(`/admin/categories/${id}`, session);
}

/** POST /api/admin/categories - new categories start active. */
export function createDefaultCategory(session: AdminSession, category: SaveCategory) {
  return adminRequest<DefaultCategory>('/admin/categories', session, {
    method: 'POST',
    body: category,
  });
}

/** PUT /api/admin/categories/{id} - full replace; omitted optional fields are cleared. */
export function updateDefaultCategory(session: AdminSession, id: number, category: SaveCategory) {
  return adminRequest<DefaultCategory>(`/admin/categories/${id}`, session, {
    method: 'PUT',
    body: category,
  });
}

/** PATCH /api/admin/categories/{id}/status - activate or deactivate. */
export function setDefaultCategoryActive(session: AdminSession, id: number, active: boolean) {
  return adminRequest<DefaultCategory>(`/admin/categories/${id}/status`, session, {
    method: 'PATCH',
    body: { active },
  });
}
