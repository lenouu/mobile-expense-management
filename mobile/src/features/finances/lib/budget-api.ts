import { authRequest } from '@/features/auth/lib/api-client';
import type { Expense } from '@/features/finances/lib/finances-api';
import type { Page, Session } from '@/types/api';

/** Mirrors `BudgetStatus` on the backend. */
export type BudgetStatus = 'UPCOMING' | 'ACTIVE' | 'FIXED' | 'CLOSED';

/** `SUCCESS` or `EXCEEDED`, and only once the window has ended. */
export type BudgetVerdict = 'SUCCESS' | 'EXCEEDED' | null;

export type BudgetCategory = {
  id: string;
  name: string;
  icon: string | null;
  color: string | null;
};

export type BudgetAllocation = {
  id: string;
  budgetId: string;
  category: BudgetCategory;
  allocatedAmount: number;
  /** Spent on that category under this budget. */
  spent: number;
  /** Allocated minus spent; negative when the category was overspent. */
  remaining: number;
};

export type Budget = {
  id: string;
  /** Null when the plan was not named. */
  name: string | null;
  startDate: string;
  endDate: string;
  lengthInDays: number;
  overallAmount: number;
  totalAllocated: number;
  /** The sum of the expenses assigned to this budget - not everything inside the window. */
  spent: number;
  /** Overall minus spent; negative means the goal was passed. */
  net: number;
  expenseCount: number;
  status: BudgetStatus;
  editable: boolean;
  editsUsed: number;
  editsRemaining: number;
  editableUntil: string | null;
  verdict: BudgetVerdict;
  closedAt: string | null;
  allocations: BudgetAllocation[];
};

export type BudgetSummary = {
  id: string;
  name: string | null;
  startDate: string;
  endDate: string;
  overallAmount: number;
  spent: number;
  net: number;
  status: BudgetStatus;
  verdict: BudgetVerdict;
  closedAt: string | null;
};

/** One category in the plan, as the form submits it. */
export type AllocationInput = {
  categoryId: string;
  allocatedAmount: number;
};

/** The whole plan, as both create and update take it. */
export type PlanInput = {
  name?: string;
  startDate: string;
  endDate: string;
  overallAmount: number;
  allocations: AllocationInput[];
};

/** GET /budgets - newest window first. */
export async function fetchBudgets(session: Session): Promise<BudgetSummary[]> {
  return authRequest<BudgetSummary[]>('/budgets', session);
}

/** GET /budgets/{id} - the full picture, including what was spent under it. */
export async function fetchBudget(session: Session, budgetId: string): Promise<Budget> {
  return authRequest<Budget>(`/budgets/${budgetId}`, session);
}

/** POST /budgets - the window, the name and the split in one call. */
export async function createBudget(session: Session, plan: PlanInput): Promise<Budget> {
  return authRequest<Budget>('/budgets', session, { method: 'POST', body: plan });
}

/** PUT /budgets/{id} - replaces the plan. Counts as one of the three edits. */
export async function updateBudget(
  session: Session,
  budgetId: string,
  plan: PlanInput,
): Promise<Budget> {
  return authRequest<Budget>(`/budgets/${budgetId}`, session, { method: 'PUT', body: plan });
}

/**
 * DELETE /budgets/{id} - deletes the plan, and only the plan.
 *
 * The expenses recorded under it are kept: they are the user's financial record, and they simply
 * stop belonging to a budget. A finished budget refuses; the server says so and the client shows it.
 */
export async function deleteBudget(session: Session, budgetId: string): Promise<void> {
  return authRequest<void>(`/budgets/${budgetId}`, session, { method: 'DELETE' });
}

/** GET /budgets/{id}/expenses - the rows behind the spending figure. */
export async function fetchBudgetExpenses(
  session: Session,
  budgetId: string,
  options: { page?: number; size?: number } = {},
): Promise<Page<Expense>> {
  const params = new URLSearchParams({
    page: String(options.page ?? 0),
    size: String(options.size ?? 20),
  });

  return authRequest<Page<Expense>>(`/budgets/${budgetId}/expenses?${params.toString()}`, session);
}

/** Whether a budget can accept an expense dated {@code date}. */
export function acceptsDate(budget: Budget, date: string): boolean {
  return budget.status !== 'CLOSED' && date >= budget.startDate && date <= budget.endDate;
}
