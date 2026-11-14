import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { ScanBanner } from '@/components/ui/scan-banner';
import { ScanPreviewPopup } from '@/features/finances/components/scan-preview-popup';
import { notifyDataChanged } from '@/features/finances/lib/data-events';
import type { MyCategory } from '@/features/finances/lib/finances-api';
import type { Receipt } from '@/features/finances/lib/receipts';
import {
  scanReceipt,
  type ScannedExpense,
  type ScanState,
} from '@/features/finances/lib/receipt-scan';
import { useSession } from '@/providers/session-provider';

type ReceiptScanContextValue = {
  state: ScanState;
  /** Sends the photo off and returns immediately: the app stays usable while it is read. */
  startScan: (receipt: Receipt, categories: MyCategory[]) => void;
  /** Throws away the current notice (and any result waiting behind it). */
  dismiss: () => void;
};

const ReceiptScanContext = createContext<ReceiptScanContextValue | null>(null);

/** How long the "added" confirmation stays before the banner clears itself. */
const SAVED_VISIBLE_MS = 4000;

/**
 * Runs the receipt scan in the background, above the screens.
 *
 * It lives at the root rather than inside the Expenses screen for one reason: the request
 * must survive the user walking away from the screen that started it. The work happens here,
 * the notice is drawn here, and the review pop-up opens over whatever the user is doing.
 */
export function ReceiptScanProvider({ children }: { children: React.ReactNode }) {
  const { session, signOut } = useSession();

  const [state, setState] = useState<ScanState>({ status: 'idle' });
  const [scanned, setScanned] = useState<ScannedExpense[]>([]);
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);

  // The confirmation is a moment, not a state the user has to clear.
  useEffect(() => {
    if (state.status !== 'saved') return;
    const handle = setTimeout(() => setState({ status: 'idle' }), SAVED_VISIBLE_MS);
    return () => clearTimeout(handle);
  }, [state]);

  // Signing out must not leave another account's receipt on screen.
  useEffect(() => {
    if (session) return;
    setState({ status: 'idle' });
    setScanned([]);
    setPreviewOpen(false);
  }, [session]);

  const dismiss = useCallback(() => {
    setPreviewOpen(false);
    setScanned([]);
    setState({ status: 'idle' });
  }, []);

  const startScan = useCallback(
    (receipt: Receipt, userCategories: MyCategory[]) => {
      if (!session) return;

      setCategories(userCategories);
      setScanned([]);
      setPreviewOpen(false);

      if (!receipt.base64) {
        setState({
          status: 'error',
          message: 'That picture could not be read. Try taking it again.',
        });
        return;
      }

      setState({ status: 'scanning' });

      // Deliberately not awaited: the caller closes its pop-up and the user carries on.
      void (async () => {
        try {
          const found = await scanReceipt(session, {
            base64: receipt.base64 ?? '',
            mimeType: receipt.mimeType,
          });

          if (found.length === 0) {
            setState({
              status: 'error',
              message: 'No expenses were found in that picture.',
            });
            return;
          }

          setScanned(found);
          setState({ status: 'ready' });
        } catch (e) {
          setState({
            status: 'error',
            message: e instanceof Error ? e.message : 'That picture could not be read.',
          });
        }
      })();
    },
    [session],
  );

  const handleSaved = useCallback((count: number) => {
    setPreviewOpen(false);
    setScanned([]);
    setState({ status: 'saved', count });
    // Every screen showing money re-reads itself: Overview, Transactions, Budget.
    notifyDataChanged();
  }, []);

  const value = useMemo<ReceiptScanContextValue>(
    () => ({ state, startScan, dismiss }),
    [state, startScan, dismiss],
  );

  return (
    <ReceiptScanContext.Provider value={value}>
      {children}

      {session ? (
        <>
          <ScanBanner
            state={state}
            onPress={() => setPreviewOpen(true)}
            onDismiss={dismiss}
          />

          <ScanPreviewPopup
            visible={previewOpen}
            scanned={scanned}
            categories={categories}
            session={session}
            onClose={() => setPreviewOpen(false)}
            onSaved={handleSaved}
            onAuthError={signOut}
          />
        </>
      ) : null}
    </ReceiptScanContext.Provider>
  );
}

/** The scan's current state, and the two actions the screens need. */
export function useReceiptScan(): ReceiptScanContextValue {
  const value = useContext(ReceiptScanContext);
  if (!value) {
    throw new Error(
      'useReceiptScan must be used inside <ReceiptScanProvider> (see src/app/_layout.tsx).',
    );
  }
  return value;
}
