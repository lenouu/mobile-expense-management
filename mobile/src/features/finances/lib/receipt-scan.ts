import { authRequest } from '@/features/auth/lib/api-client';
import { todayIso } from '@/features/finances/components/finance-ui';
import type { Session } from '@/types/api';

/**
 * Reads a receipt through the backend.
 *
 * The AI provider's key lives on the server, so the app never holds it: this module only knows
 * the backend's own route, authenticated with the same bearer token as every other call. The
 * photo goes up as base64 inside JSON - the format the backend expects, with the MIME type
 * alongside it - and the backend answers with rows already normalised to `ScannedExpense`.
 */

/** One expense found in the photo, still unattached to the database. */
export type ScannedExpense = {
  description: string;
  /** Major units, e.g. 1250 for 1250 XAF. */
  amount: number;
  /** YYYY-MM-DD. */
  date: string;
  /** The name the model chose; matched against the real categories when the preview opens. */
  categoryName: string | null;
};

/**
 * What the background scan is doing, shown by the banner at the bottom of the app.
 *
 * `saved` is transient: it confirms the review finished, then the banner goes away.
 */
export type ScanState =
  | { status: 'idle' }
  | { status: 'scanning' }
  | { status: 'ready' }
  | { status: 'saved'; count: number }
  | { status: 'error'; message: string };

/** `POST /api/receipts/scan` - the shape the backend documents. */
type ScanResponse = {
  expenses?: {
    description?: string | null;
    amount?: number | string | null;
    date?: string | null;
    category?: string | null;
  }[];
};

/**
 * Sends the picture and returns what it contained.
 *
 * An empty array is a normal answer: the photo simply held nothing readable.
 *
 * @throws ApiRequestError (from `authRequest`) with the backend's own message when the
 *         picture is rejected or the AI service is unavailable.
 */
export async function scanReceipt(
  session: Session,
  image: { base64: string; mimeType: string },
): Promise<ScannedExpense[]> {
  const response = await authRequest<ScanResponse>('/receipts/scan', session, {
    method: 'POST',
    body: {
      imageBase64: image.base64,
      mimeType: image.mimeType,
    },
  });

  const rows = Array.isArray(response?.expenses) ? response.expenses : [];

  return rows
    .map((entry): ScannedExpense | null => {
      const amount = Number(entry?.amount);
      if (!Number.isFinite(amount) || amount <= 0) return null;

      const date =
        typeof entry.date === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(entry.date)
          ? entry.date
          : todayIso();

      return {
        description: typeof entry.description === 'string' ? entry.description.trim() : '',
        amount,
        date,
        categoryName: typeof entry.category === 'string' ? entry.category.trim() : null,
      };
    })
    .filter((entry): entry is ScannedExpense => entry !== null);
}
