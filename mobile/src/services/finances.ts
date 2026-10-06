import { ApiRequestError, authRequest, login, PagedModel, Session } from '@/services/api-client';

// Shapes of /categories (S2-TECH-1), /expenses and /incomes (S2-TECH-2) for a signed-in user.

export type MyCategory = {
  id: number;
  name: string;
  description: string | null;
  icon: string | null;
  color: string | null;
  defaultCategoryId: number | null;
};

export type Expense = {
  id: number;
  /** JSON number; the backend stores it as an exact decimal with 2 places. */
  amount: number;
  /** YYYY-MM-DD */
  date: string;
  description: string | null;
  category: { id: number; name: string; icon: string | null; color: string | null };
  createdAt: string;
  updatedAt: string;
};

export type Income = {
  id: number;
  amount: number;
  date: string;
  source: string;
  description: string | null;
  createdAt: string;
  updatedAt: string;
};

/** Body of POST and PUT. PUT is a full replace: an omitted description is cleared. */
export type SaveExpense = { amount: number; date: string; categoryId: number; description?: string };

export type SaveIncome = { amount: number; date: string; source: string; description?: string };

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

function pageParams(options: { page?: number; size?: number }) {
  return new URLSearchParams({ page: String(options.page ?? 0), size: String(options.size ?? 20) });
}

export function fetchExpenses(session: Session, options: { page?: number; size?: number } = {}) {
  return authRequest<PagedModel<Expense>>(`/expenses?${pageParams(options)}`, session);
}

export function createExpense(session: Session, expense: SaveExpense) {
  return authRequest<Expense>('/expenses', session, { method: 'POST', body: expense });
}

export function updateExpense(session: Session, id: number, expense: SaveExpense) {
  return authRequest<Expense>(`/expenses/${id}`, session, { method: 'PUT', body: expense });
}

export function deleteExpense(session: Session, id: number) {
  return authRequest<void>(`/expenses/${id}`, session, { method: 'DELETE' });
}

export function fetchIncomes(session: Session, options: { page?: number; size?: number } = {}) {
  return authRequest<PagedModel<Income>>(`/incomes?${pageParams(options)}`, session);
}

export function createIncome(session: Session, income: SaveIncome) {
  return authRequest<Income>('/incomes', session, { method: 'POST', body: income });
}

export function updateIncome(session: Session, id: number, income: SaveIncome) {
  return authRequest<Income>(`/incomes/${id}`, session, { method: 'PUT', body: income });
}

export function deleteIncome(session: Session, id: number) {
  return authRequest<void>(`/incomes/${id}`, session, { method: 'DELETE' });
}
