import { useEffect, useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { Button } from '@/components/ui/primitives';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { financeStyles, formatAmount, formatDate } from '@/features/finances/components/finance-ui';
import {
  type ExpenseTotal,
  fetchExpenseTotal,
  fetchPeriods,
  type FinancialPeriod,
} from '@/features/finances/lib/finances-api';
import { describeError } from '@/utils/errors';
import type { Session } from '@/types/api';

/** More than enough history for the arrows; periods are at least a week long. */
const PERIOD_HISTORY_SIZE = 100;

/**
 * US39: "Spent this period" - the total of the user's expenses for one financial period, with
 * arrows to step back through past periods.
 *
 * Reloads whenever `refreshKey` changes, which the Expenses screen bumps after every add, edit
 * or delete, so the total is always right without tapping Refresh.
 */
export function ExpenseTotalCard({
  session,
  refreshKey,
  onError,
}: {
  session: Session;
  refreshKey: number;
  onError: (e: unknown) => void;
}) {
  // Newest first: index 0 is the current period, higher indexes go back in time.
  const [periods, setPeriods] = useState<FinancialPeriod[]>([]);
  const [index, setIndex] = useState(0);
  const [data, setData] = useState<ExpenseTotal | null>(null);
  const [loading, setLoading] = useState(true);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      setLoading(true);
      setFailure(null);
      try {
        const history = await fetchPeriods(session, { size: PERIOD_HISTORY_SIZE });
        const selected = history.content[Math.min(index, history.content.length - 1)];
        const total = await fetchExpenseTotal(session, selected?.id);
        if (!cancelled) {
          setPeriods(history.content);
          setData(total);
        }
      } catch (e) {
        if (!cancelled) {
          setFailure(describeError(e));
          onError(e);
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [session, refreshKey, index, onError]);

  const period = data?.period;
  const isCurrent = period?.status === 'CURRENT';

  return (
    <ThemedView type="backgroundElement" style={[financeStyles.card, styles.card]}>
      <View style={financeStyles.headerRow}>
        <ThemedText type="small" themeColor="textSecondary">
          {isCurrent || !period ? 'Spent this period' : `Spent in period ${period.sequence}`}
        </ThemedText>
        <View style={financeStyles.row}>
          <Button
            label="‹"
            onPress={() => setIndex((i) => i + 1)}
            disabled={loading || index >= periods.length - 1}
            secondary
            onCard
          />
          <Button label="›" onPress={() => setIndex((i) => i - 1)} disabled={loading || index === 0} secondary onCard />
        </View>
      </View>

      {loading && !data ? (
        <ActivityIndicator />
      ) : failure && !data ? (
        <ThemedText type="small" style={financeStyles.errorText}>
          {failure}
        </ThemedText>
      ) : (
        data &&
        period && (
          <>
            <ThemedText type="smallBold" style={styles.total}>
              {formatAmount(data.total)}
            </ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              {formatDate(period.startDate)} – {formatDate(period.endDate)} · {data.count}{' '}
              {data.count === 1 ? 'expense' : 'expenses'}
            </ThemedText>
          </>
        )
      )}
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  card: {
    gap: 4,
  },
  total: {
    fontSize: 28,
    lineHeight: 36,
  },
});
