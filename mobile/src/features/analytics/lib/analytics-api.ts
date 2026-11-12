import { authRequest } from '@/features/auth/lib/api-client';
import type { Session } from '@/types/api';

export type Money = { amount: number; currency: string };

export type AnalyticsTotals = {
  income: Money;
  expenses: Money;
  /** Income minus expenses. Negative is a debt, not an error. */
  net: Money;
  /** Net as a percentage of income, or null when nothing came in. */
  savingsRate: number | null;
  /** The window's spending divided by its days. */
  averagePerDay: Money;
  incomeCount: number;
  expenseCount: number;
};

export type AnalyticsChange = {
  incomePercent: number | null;
  expensePercent: number | null;
  netPercent: number | null;
};

export type DailyPoint = { date: string; income: Money; expenses: Money };

export type Bucket = {
  label: string;
  from: string;
  to: string;
  income: Money;
  expenses: Money;
};

export type CategorySlice = {
  id: string;
  name: string;
  icon: string | null;
  color: string | null;
  total: Money;
  previousTotal: Money;
  /** Against the same slice in the window before; null when it had none. */
  changePercent: number | null;
  share: number;
};

export type SourceSlice = {
  id: string;
  name: string;
  icon: string | null;
  color: string | null;
  total: Money;
  previousTotal: Money;
  /** Against the same slice in the window before; null when it had none. */
  changePercent: number | null;
  share: number;
};

export type DaySummary = { date: string; income: Money; expenses: Money; net: Money };

export type BudgetSnapshot = {
  id: string;
  name: string | null;
  planned: Money;
  spent: Money;
  remaining: Money;
  daysLeft: number;
  status: 'UPCOMING' | 'ACTIVE' | 'FIXED' | 'CLOSED';
};

export type AnalyticsSummary = {
  from: string;
  to: string;
  currency: string;
  current: AnalyticsTotals;
  previousFrom: string;
  previousTo: string;
  previous: AnalyticsTotals;
  change: AnalyticsChange;
  daily: DailyPoint[];
  buckets: Bucket[];
  /** The window before, aligned to daily by index. */
  previousDaily: DailyPoint[];
  /** The window before, in the same buckets, so bars pair one for one. */
  previousBuckets: Bucket[];
  byCategory: CategorySlice[];
  bySource: SourceSlice[];
  bestDay: DaySummary | null;
  /** The single largest expense of the window, or null when there was none. */
  biggestExpense: {
    date: string;
    categoryName: string | null;
    icon: string | null;
    color: string | null;
    description: string | null;
    amount: Money;
  } | null;
  worstDay: DaySummary | null;
  noSpendDays: number;
  budget: BudgetSnapshot | null;
};

/** GET /analytics/summary - every figure the Dashboard shows, in one request. */
export async function fetchAnalytics(
  session: Session,
  window: { from: string; to: string },
): Promise<AnalyticsSummary> {
  const params = new URLSearchParams({ from: window.from, to: window.to });
  return authRequest<AnalyticsSummary>(`/analytics/summary?${params.toString()}`, session);
}
