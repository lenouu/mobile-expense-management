import { ApiRequestError, authRequest, login, Session } from '@/features/auth/lib/api-client';
import type { Money, Page } from '@/types/api';

// Shapes of /categories (S2-TECH-1), /expenses and /incomes (S2-TECH-2) for a signed-in user.

export type MyCategory = {
  id: string;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  defaultCategoryId: string | null;
};

export type Expense = {
  id: string;
  /** `{ amount, currency }`; the backend never sends money as a bare number. */
  amount: Money;
  /** YYYY-MM-DD */
  date: string;
  description: string | null;
  category: { id: string; name: string; icon: string | null; color: string | null } | null;
  createdAt: string;
  updatedAt: string;
};

export type Income = {
  id: string;
  /** `{ amount, currency }`; the backend never sends money as a bare number. */
  amount: Money;
  date: string;
  source: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
};

/** Body of POST and PUT. PUT is a full replace: an omitted description is cleared. */
export type SaveExpense = { amount: Money; date: string; categoryId: string; description?: string };

export type SaveIncome = { amount: Money; date: string; source: string; description?: string };

/**
 * Logs in a regular user. Admin accounts have no categories or money of their own, so they are
 * pointed to the Admin tab instead.
 */
export async function userLogin(usernameOrEmail: string, password: string): Promise<Session> {
  const session = await login(usernameOrEmail, password);
  if (session.type !== 'USER') {
    throw new ApiRequestError(403, 'Administrator accounts use the Admin tab.');
  }
  return session;
}

export function fetchMyCategories(session: Session) {
  return authRequest<MyCategory[]>('/categories', session);
}

/** Body of POST /categories (US17). Only the name is required. */
export type NewCategory = { name: string; description?: string; icon?: string; color?: string };

export function createMyCategory(session: Session, category: NewCategory) {
  return authRequest<MyCategory>('/categories', session, { method: 'POST', body: category });
}

function pageParams(options: { page?: number; size?: number }) {
  return new URLSearchParams({ page: String(options.page ?? 0), size: String(options.size ?? 20) });
}

export function fetchExpenses(session: Session, options: { page?: number; size?: number } = {}) {
  return authRequest<Page<Expense>>(`/expenses?${pageParams(options)}`, session);
}

export function createExpense(session: Session, expense: SaveExpense) {
  return authRequest<Expense>('/expenses', session, { method: 'POST', body: expense });
}

export function updateExpense(session: Session, id: string, expense: SaveExpense) {
  return authRequest<Expense>(`/expenses/${id}`, session, { method: 'PUT', body: expense });
}

export function deleteExpense(session: Session, id: string) {
  return authRequest<void>(`/expenses/${id}`, session, { method: 'DELETE' });
}

/** One financial period of the signed-in user (S4-TECH-1). Dates are YYYY-MM-DD, both inclusive. */
export type FinancialPeriod = {
  id: string;
  sequence: number;
  startDate: string;
  endDate: string;
  days: number;
  lengthCount: number;
  lengthUnit: 'DAYS' | 'WEEKS' | 'MONTHS';
  status: 'PAST' | 'CURRENT' | 'UPCOMING';
};

/** GET /expenses/total (US39). */
export type ExpenseTotal = { period: FinancialPeriod; total: Money; count: number };

/** GET /periods - newest first, so index 0 is the current period. */
export function fetchPeriods(session: Session, options: { page?: number; size?: number } = {}) {
  return authRequest<Page<FinancialPeriod>>(`/periods?${pageParams(options)}`, session);
}

/** Total of my expenses for a period; the current one when `periodId` is omitted. */
export function fetchExpenseTotal(session: Session, periodId?: string) {
  const query = periodId !== undefined ? `?periodId=${periodId}` : '';
  return authRequest<ExpenseTotal>(`/expenses/total${query}`, session);
}

export function fetchIncomes(session: Session, options: { page?: number; size?: number } = {}) {
  return authRequest<Page<Income>>(`/incomes?${pageParams(options)}`, session);
}

export function createIncome(session: Session, income: SaveIncome) {
  return authRequest<Income>('/incomes', session, { method: 'POST', body: income });
}

export function updateIncome(session: Session, id: string, income: SaveIncome) {
  return authRequest<Income>(`/incomes/${id}`, session, { method: 'PUT', body: income });
}

export function deleteIncome(session: Session, id: string) {
  return authRequest<void>(`/incomes/${id}`, session, { method: 'DELETE' });
}