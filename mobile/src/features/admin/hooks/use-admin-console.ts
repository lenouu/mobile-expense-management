import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import {
  fetchDefaultCategories,
  fetchErrorLogWindow,
  fetchPlatformHealth,
  fetchUsers,
  type AdminSession,
  type ErrorLogEntry,
  type PlatformHealth,
  type UserSummary,
} from '@/features/admin/lib/admin-api';
import { ADULT_ACCOUNT_STATES, type AdminRange } from '@/features/admin/lib/admin-range';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import type { AccountState } from '@/types/api';

export type AccountCounts = {
  /** Every account, soft-deleted included - the backend's unfiltered listing. */
  total: number;
  ACTIVE: number;
  PENDING: number;
  SUSPENDED: number;
  DELETED: number;
};

export type AdminConsoleData = {
  loading: boolean;
  refreshing: boolean;
  /** Set when a call failed; the screens show it and still render whatever loaded. */
  error: string | null;
  health: PlatformHealth | null;
  /** Null while the counts could not be read - the rest of the console still renders. */
  accounts: AccountCounts | null;
  /** Newest accounts, for the directory preview. Capped by the backend's page-size limit. */
  users: UserSummary[];
  /** True when there are more accounts than `users` holds. */
  usersTruncated: boolean;
  /** How many default categories exist, and how many of them are active. */
  categories: { total: number; active: number } | null;
  errors: ErrorLogEntry[];
  errorTotal: number;
  errorsTruncated: boolean;
  refresh: () => void;
};

/** Four pages of 100 is as much error history as any chart in the console needs. */
const MAX_ERROR_ENTRIES = 400;
/** The backend caps `size` at 100 (`spring.data.web.pageable.max-page-size`). */
const USER_PAGE_SIZE = 100;

/**
 * Everything the admin console reads, in one hook.
 *
 * The console's four screens are views over the same five endpoints, so loading once and sharing
 * the result keeps navigating between tabs instant and stops four screens from firing four
 * identical requests. The requests are independent and settle separately: a failing count should
 * not blank out the health card.
 */
export function useAdminConsole(session: AdminSession, range: AdminRange): AdminConsoleData {
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [health, setHealth] = useState<PlatformHealth | null>(null);
  const [accounts, setAccounts] = useState<AccountCounts | null>(null);
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [usersTruncated, setUsersTruncated] = useState(false);
  const [categories, setCategories] = useState<{ total: number; active: number } | null>(null);
  const [errors, setErrors] = useState<ErrorLogEntry[]>([]);
  const [errorTotal, setErrorTotal] = useState(0);
  const [errorsTruncated, setErrorsTruncated] = useState(false);

  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const rangeKey = range.key;
  const rangeHours = range.hours;

  const load = useCallback(
    async (mode: 'initial' | 'refresh') => {
      if (mode === 'refresh') setRefreshing(true);
      else setLoading(true);
      setError(null);

      const from = new Date(Date.now() - rangeHours * 3600 * 1000);

      const [healthResult, errorsResult, usersResult, recentResult, ...stateResults] =
        await Promise.allSettled([
          fetchPlatformHealth(session),
          fetchErrorLogWindow(session, { from, maxEntries: MAX_ERROR_ENTRIES }),
          fetchUsers(session, { page: 0, size: 1 }),
          // Newest first: the directory preview and the signup trend both want recent accounts.
          fetchUsers(session, { page: 0, size: USER_PAGE_SIZE, sort: 'createdAt,desc' }),
          ...ADULT_ACCOUNT_STATES.map((status) => fetchUsers(session, { status, page: 0, size: 1 })),
        ]);

      if (!mounted.current) return;

      if (healthResult.status === 'fulfilled') setHealth(healthResult.value);

      if (errorsResult.status === 'fulfilled') {
        setErrors(errorsResult.value.entries);
        setErrorTotal(errorsResult.value.total);
        setErrorsTruncated(errorsResult.value.truncated);
      }

      if (recentResult.status === 'fulfilled') {
        setUsers(recentResult.value.content);
        setUsersTruncated(
          recentResult.value.page.totalElements > recentResult.value.content.length,
        );
      }

      const categoriesResult = await Promise.allSettled([
        fetchDefaultCategories(session, { page: 0, size: 1 }),
        fetchDefaultCategories(session, { active: true, page: 0, size: 1 }),
      ]);

      // The unfiltered listing gives the total; the `active=true` listing gives the active count.
      // Two cheap calls rather than one big one, because `CategoryResponses.Details` has no field
      // to filter on client-side.
      if (categoriesResult[0].status === 'fulfilled') {
        setCategories({
          total: categoriesResult[0].value.page.totalElements,
          active:
            categoriesResult[1].status === 'fulfilled'
              ? categoriesResult[1].value.page.totalElements
              : 0,
        });
      }

      const totals = stateResults.map((result) =>
        result.status === 'fulfilled' ? result.value.page.totalElements : null,
      );
      const total = usersResult.status === 'fulfilled' ? usersResult.value.page.totalElements : null;

      if (total !== null && totals.every((value) => value !== null)) {
        setAccounts({
          total,
          ACTIVE: totals[ADULT_ACCOUNT_STATES.indexOf('ACTIVE')] ?? 0,
          PENDING: totals[ADULT_ACCOUNT_STATES.indexOf('PENDING')] ?? 0,
          SUSPENDED: totals[ADULT_ACCOUNT_STATES.indexOf('SUSPENDED')] ?? 0,
          DELETED: totals[ADULT_ACCOUNT_STATES.indexOf('DELETED')] ?? 0,
        });
      } else {
        setAccounts(null);
      }

      const failures = [
        healthResult.status === 'rejected' ? healthResult.reason : null,
        errorsResult.status === 'rejected' ? errorsResult.reason : null,
        usersResult.status === 'rejected' ? usersResult.reason : null,
      ].filter(Boolean) as unknown[];

      if (failures.length > 0) setError(describeConsoleFailure(failures[0], failures.length));

      if (!mounted.current) return;
      setLoading(false);
      setRefreshing(false);
    },
    [session, rangeKey, rangeHours],
  );

  useEffect(() => {
    void load('initial');
  }, [load]);

  const refresh = useCallback(() => {
    void load('refresh');
  }, [load]);

  return useMemo(
    () => ({
      loading,
      refreshing,
      error,
      health,
      accounts,
      users,
      usersTruncated,
      categories,
      errors,
      errorTotal,
      errorsTruncated,
      refresh,
    }),
    [
      loading,
      refreshing,
      error,
      health,
      accounts,
      users,
      usersTruncated,
      categories,
      errors,
      errorTotal,
      errorsTruncated,
      refresh,
    ],
  );
}

/**
 * Turns the first failure into a sentence.
 *
 * 401/403 is called out because it has one cause worth acting on - the token expired (they last
 * 60 minutes) or this account lost its ADMIN role - and either way the fix is to sign in again.
 */
function describeConsoleFailure(error: unknown, failureCount: number): string {
  const suffix = failureCount > 1 ? ` (and ${failureCount - 1} other request failed)` : '';

  if (error instanceof ApiRequestError) {
    if (error.status === 401) return `Your session has expired. Sign in again.${suffix}`;
    if (error.status === 403) return `This account no longer has administrator access.${suffix}`;
    return `${error.message}${suffix}`;
  }
  return `Could not load part of the console.${suffix}`;
}

/** Convenience for the screens: the account-state counts as chart-ready rows. */
export function accountStateRows(counts: AccountCounts | null): { state: AccountState; value: number }[] {
  if (!counts) return [];
  return ADULT_ACCOUNT_STATES.map((state) => ({ state, value: counts[state] }));
}
