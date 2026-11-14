import { authRequest } from '@/features/auth/lib/api-client';
import { todayIso } from '@/features/finances/components/finance-ui';
import type { Session } from '@/types/api';

/**
 * Reads a pasted message through the backend's AI reader.
 *
 * The model decides whether each movement is an outflow (an expense) or an inflow (an income);
 * the app shows that as a toggle the user can override. Nothing is created here - the caller
 * reviews the rows and posts them to the ordinary expense and income endpoints.
 */

/** Which way the money moved. */
export type ScanDirection = 'IN' | 'OUT';

/** One money movement found in the message, still unattached to the database. */
export type ScannedTransaction = {
  direction: ScanDirection;
  description: string;
  /** Major units, e.g. 1250 for 1250 XAF. */
  amount: number;
  /** YYYY-MM-DD. */
  date: string;
  /** For an outflow: the expense category the model chose. */
  categoryName: string | null;
  /** For an inflow: the income source the model chose. */
  incomeSourceName: string | null;
};

/** `POST /api/captures/scan` - the shape the backend documents. */
type ScanResponse = {
  transactions?: {
    direction?: string | null;
    description?: string | null;
    amount?: number | string | null;
    date?: string | null;
    category?: string | null;
    incomeSource?: string | null;
  }[];
};

function asDirection(value: unknown): ScanDirection {
  return typeof value === 'string' && value.trim().toUpperCase() === 'IN' ? 'IN' : 'OUT';
}

/**
 * Sends the text and returns the movements it contained.
 *
 * An empty array is a normal answer: the message held nothing that moves money.
 *
 * @throws ApiRequestError (from `authRequest`) with the backend's own message when the text is
 *         rejected or the AI service is unavailable.
 */
export async function scanMessage(
  session: Session,
  text: string,
): Promise<ScannedTransaction[]> {
  const response = await authRequest<ScanResponse>('/captures/scan', session, {
    method: 'POST',
    body: { text },
  });

  const rows = Array.isArray(response?.transactions) ? response.transactions : [];

  return rows
    .map((entry): ScannedTransaction | null => {
      const amount = Number(entry?.amount);
      if (!Number.isFinite(amount) || amount <= 0) return null;

      const date =
        typeof entry.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(entry.date)
          ? entry.date
          : todayIso();

      return {
        direction: asDirection(entry.direction),
        description: typeof entry.description === 'string' ? entry.description.trim() : '',
        amount,
        date,
        categoryName: typeof entry.category === 'string' ? entry.category.trim() : null,
        incomeSourceName:
          typeof entry.incomeSource === 'string' ? entry.incomeSource.trim() : null,
      };
    })
    .filter((entry): entry is ScannedTransaction => entry !== null);
}
