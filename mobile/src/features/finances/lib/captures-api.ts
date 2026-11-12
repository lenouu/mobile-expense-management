import { authRequest, Session } from '@/features/auth/lib/api-client';
import type { SaveExpense } from '@/features/finances/lib/finances-api';
import type { Money, Page } from '@/types/api';

// S6-TECH-2: transactions captured from a pasted mobile money SMS, and their review.

export type MobileMoneyProvider = 'MTN_MOMO' | 'ORANGE_MONEY';

export type CaptureStatus = 'PENDING' | 'CONFIRMED' | 'DISMISSED';

export type CapturedTransaction = {
  id: string;
  source: 'MOBILE_MONEY_SMS';
  provider: MobileMoneyProvider;
  type: 'PAYMENT' | 'TRANSFER' | 'WITHDRAWAL' | 'UNKNOWN';
  /** What was sent or paid, without the fee. */
  amount: Money;
  /** Null when the SMS stated no fee. */
  fee: Money | null;
  /** Amount plus fee: what actually left the wallet. */
  total: Money;
  // Every field below is null when the SMS did not contain it - blank, never guessed.
  counterparty: string | null;
  reference: string | null;
  /** YYYY-MM-DD */
  transactionDate: string | null;
  /** HH:mm:ss */
  transactionTime: string | null;
  /** e.g. "MTN MoMo payment - ENEO" */
  suggestedDescription: string;
  status: CaptureStatus;
  /** The expense created on confirmation. */
  expenseId: string | null;
  createdAt: string;
  reviewedAt: string | null;
};

export const PROVIDER_LABELS: Record<MobileMoneyProvider, string> = {
  MTN_MOMO: 'MTN MoMo',
  ORANGE_MONEY: 'Orange Money',
};

/**
 * POST /captures/sms - reads the SMS and keeps the transaction as PENDING. No expense yet.
 *
 * @throws ApiRequestError 422 (`unsupported_message`) when it is not a supported outgoing
 *         transaction - the message says why
 * @throws ApiRequestError 409 when the same transaction was already captured
 */
export function captureSms(session: Session, text: string, provider?: MobileMoneyProvider) {
  return authRequest<CapturedTransaction>('/captures/sms', session, {
    method: 'POST',
    body: provider ? { text, provider } : { text },
  });
}

export function fetchCaptures(
  session: Session,
  options: { status?: CaptureStatus; page?: number; size?: number } = {},
) {
  const params = new URLSearchParams({ page: String(options.page ?? 0), size: String(options.size ?? 20) });
  if (options.status) params.set('status', options.status);
  return authRequest<Page<CapturedTransaction>>(`/captures?${params.toString()}`, session);
}

/** Creates the expense from the reviewed values; the capture becomes CONFIRMED. */
export function confirmCapture(session: Session, id: string, expense: SaveExpense) {
  return authRequest<CapturedTransaction>(`/captures/${id}/confirm`, session, { method: 'POST', body: expense });
}

/** Discards the capture; no expense is created. */
export function dismissCapture(session: Session, id: string) {
  return authRequest<CapturedTransaction>(`/captures/${id}/dismiss`, session, { method: 'POST' });
}
