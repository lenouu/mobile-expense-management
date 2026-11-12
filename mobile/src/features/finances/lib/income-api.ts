import { authRequest } from '@/features/auth/lib/api-client';
import type { Page, Session } from '@/types/api';

/** Mirrors `IncomeResponses.SourceRef` and `IncomeSourceResponses.Details`. */
export type IncomeSourceRef = {
  id: string;
  name: string;
  icon: string | null;
  color: string | null;
};

export type IncomeSource = IncomeSourceRef & {
  description: string | null;
  /** How many income entries use it - the count a delete refusal quotes. */
  incomeCount: number;
};

export type IncomeEntry = {
  id: string;
  amount: { amount: number; currency: string };
  date: string;
  /** Where the money came from; required, unlike an expense's category. */
  source: IncomeSourceRef | null;
  /** An optional label for this payment, e.g. "November salary". */
  name: string | null;
  description: string | null;
  createdAt: string;
  updatedAt: string;
};

/** One source's share of a window, from `GET /incomes/summary`. */
export type IncomeSourceSlice = {
  source: IncomeSourceRef;
  total: { amount: number; currency: string };
  count: number;
  share: number;
};

export type IncomeSummary = {
  from: string;
  to: string;
  total: { amount: number; currency: string };
  count: number;
  /** The window of the same length ending the day before `from`. */
  previousFrom: string;
  previousTo: string;
  previousTotal: { amount: number; currency: string };
  previousCount: number;
  change: { amount: number; currency: string };
  /** Against the previous window, or null when that window had nothing. */
  changePercent: number | null;
  bySource: IncomeSourceSlice[];
  /** Every day of the window, zeros included. */
  daily: { date: string; total: { amount: number; currency: string } }[];
};

export type SaveIncome = {
  amount: { amount: number; currency: string };
  date: string;
  sourceId: string;
  name?: string;
  description?: string;
};

export type SaveIncomeSource = {
  name: string;
  icon?: string;
  color?: string;
  description?: string;
};

/** GET /income-sources - alphabetical, with usage counts. */
export async function fetchIncomeSources(session: Session): Promise<IncomeSource[]> {
  const listing = await authRequest<{ items: IncomeSource[]; total: number }>(
    '/income-sources',
    session,
  );
  return listing.items;
}

/** POST /income-sources */
export async function createIncomeSource(
  session: Session,
  body: SaveIncomeSource,
): Promise<IncomeSource> {
  return authRequest<IncomeSource>('/income-sources', session, { method: 'POST', body });
}

/** PUT /income-sources/{id} */
export async function updateIncomeSource(
  session: Session,
  id: string,
  body: SaveIncomeSource,
): Promise<IncomeSource> {
  return authRequest<IncomeSource>(`/income-sources/${id}`, session, { method: 'PUT', body });
}

/** DELETE /income-sources/{id} - refused while income refers to it. */
export async function deleteIncomeSource(session: Session, id: string): Promise<void> {
  return authRequest<void>(`/income-sources/${id}`, session, { method: 'DELETE' });
}

/** GET /incomes - the window's payments, newest first. */
export async function fetchIncomes(
  session: Session,
  window: { from: string; to: string; page?: number; size?: number },
): Promise<Page<IncomeEntry>> {
  const params = new URLSearchParams({
    from: window.from,
    to: window.to,
    page: String(window.page ?? 0),
    size: String(window.size ?? 20),
  });
  return authRequest<Page<IncomeEntry>>(`/incomes?${params.toString()}`, session);
}

/** GET /incomes/summary - the by-source block. */
export async function fetchIncomeSummary(
  session: Session,
  window: { from: string; to: string },
): Promise<IncomeSummary> {
  const params = new URLSearchParams({ from: window.from, to: window.to });
  return authRequest<IncomeSummary>(`/incomes/summary?${params.toString()}`, session);
}

/** POST /incomes */
export async function createIncome(session: Session, body: SaveIncome): Promise<IncomeEntry> {
  return authRequest<IncomeEntry>('/incomes', session, { method: 'POST', body });
}

/** PUT /incomes/{id} - full replace, so an omitted description is cleared. */
export async function updateIncome(
  session: Session,
  id: string,
  body: SaveIncome,
): Promise<IncomeEntry> {
  return authRequest<IncomeEntry>(`/incomes/${id}`, session, { method: 'PUT', body });
}

/** DELETE /incomes/{id} */
export async function deleteIncome(session: Session, id: string): Promise<void> {
  return authRequest<void>(`/incomes/${id}`, session, { method: 'DELETE' });
}
