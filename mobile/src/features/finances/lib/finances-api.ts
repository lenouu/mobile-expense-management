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
  category: { id: string; name: string; icon: string | null; color: string | null };
  /** The budget it is counted against, or null for a normal expense. */
  budget: { id: string; name: string | null; startDate: string; endDate: string } | null;
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
export type SaveExpense = {
  amount: Money;
  date: string;
  categoryId: string;
  description?: string;
  /** Optional: the budget this expense counts against. */
  budgetId?: string;
};

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

/**
 * GET /categories - the caller's own categories, paged and sorted by name.
 *
 * `q` searches names on the server, so a match is found even when it is on another page - which is
 * what makes "is this category already there?" an honest question.
 *
 * A picker that needs every category asks for a single large page rather than walking the pages.
 */
export function fetchMyCategories(
  session: Session,
  options: { page?: number; size?: number; q?: string } = {},
) {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  });
  // A blank q would become a filter that matches nothing useful; leave it out instead.
  if (options.q?.trim()) params.set('q', options.q.trim());

  return authRequest<Page<MyCategory>>(`/categories?${params.toString()}`, session);
}

/** Body of POST and PUT /categories. Only the name is required; PUT is a full replace. */
export type NewCategory = { name: string; description?: string; icon?: string; color?: string };

/**
 * POST /categories (US17) - a category that belongs to the caller alone.
 *
 * @throws ApiRequestError 409 when the caller already has a category with that name
 */
export function createMyCategory(session: Session, category: NewCategory) {
  return authRequest<MyCategory>('/categories', session, { method: 'POST', body: category });
}

/**
 * PUT /categories/{id} (US18) - full replace of one of the caller's own categories.
 *
 * Editing a category inherited from the defaults rewrites only the caller's copy; the platform
 * default and every other account keep theirs.
 *
 * @throws ApiRequestError 404 when the id is not one of the caller's categories
 * @throws ApiRequestError 409 when another of the caller's categories already uses the name
 */
export function updateMyCategory(session: Session, id: string, category: NewCategory) {
  return authRequest<MyCategory>(`/categories/${id}`, session, { method: 'PUT', body: category });
}

/**
 * DELETE /categories/{id} (US19) - removes one of the caller's own categories.
 *
 * @throws ApiRequestError 404 when the id is not one of the caller's categories
 * @throws ApiRequestError 422 while expenses still reference the category, so spending history
 *         is never orphaned - the message says how many
 */
export function deleteMyCategory(session: Session, id: string) {
  return authRequest<void>(`/categories/${id}`, session, { method: 'DELETE' });
}

function pageParams(options: { page?: number; size?: number }) {
  return new URLSearchParams({ page: String(options.page ?? 0), size: String(options.size ?? 20) });
}

/**
 * The filters a listing and a total both accept. All optional, and they combine - which is why
 * one shape is used for both: the total must describe exactly the rows the list would return.
 */
export type ExpenseFilters = {
  periodId?: string;
  /** YYYY-MM-DD, inclusive. */
  from?: string;
  to?: string;
  categoryId?: string;
  q?: string;
};

function expenseQuery(filters: ExpenseFilters, page?: { page?: number; size?: number }) {
  const params = new URLSearchParams();

  if (filters.periodId) params.set('periodId', filters.periodId);
  if (filters.from) params.set('from', filters.from);
  if (filters.to) params.set('to', filters.to);
  if (filters.categoryId) params.set('categoryId', filters.categoryId);
  // Blank would become "%%": a filter matching only rows that have a description.
  if (filters.q?.trim()) params.set('q', filters.q.trim());

  if (page) {
    params.set('page', String(page.page ?? 0));
    params.set('size', String(page.size ?? 20));
  }

  return params.toString();
}

export function fetchExpenses(
  session: Session,
  options: ExpenseFilters & { page?: number; size?: number } = {},
) {
  const { page, size, ...filters } = options;
  return authRequest<Page<Expense>>(`/expenses?${expenseQuery(filters, { page, size })}`, session);
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

/** GET /expenses/total (US39): the same filters as the listing, summed. */
export type ExpenseTotal = {
  /** First day covered, inclusive. */
  from: string;
  /** Last day covered, inclusive. */
  to: string;
  total: Money;
  count: number;
  /** Null when the window was given as dates only - a reporting window is not a period. */
  period: FinancialPeriod | null;
};

/** GET /periods - newest first, so index 0 is the current period. */
export function fetchPeriods(session: Session, options: { page?: number; size?: number } = {}) {
  return authRequest<Page<FinancialPeriod>>(`/periods?${pageParams(options)}`, session);
}

/** GET /periods/current - the period containing today, created on first use. */
export function fetchCurrentPeriod(session: Session) {
  return authRequest<FinancialPeriod>('/periods/current', session);
}

/**
 * Total of my expenses over a window.
 *
 * With no filters at all the backend answers for the current period; with `from`/`to` it answers
 * for exactly that window, and `period` comes back null because a window is not a period.
 */
export function fetchExpenseTotal(session: Session, filters: ExpenseFilters = {}) {
  return authRequest<ExpenseTotal>(`/expenses/total?${expenseQuery(filters)}`, session);
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
