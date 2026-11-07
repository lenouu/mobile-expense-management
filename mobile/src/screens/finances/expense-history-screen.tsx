import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Platform, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BackButton } from '@/components/ui/back-button';
import { Button } from '@/components/ui/primitives';
import { BottomTabInset, Brand, MaxContentWidth, Spacing } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import {
  financeStyles as styles,
  formatAmount,
  formatDate,
} from '@/features/finances/components/finance-ui';
import { Expense, fetchExpenses } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
import { describeError } from '@/utils/errors';

const PAGE_SIZE = 20;

/**
 * US21: View expense history.
 *
 * Lists the authenticated user's expenses with amount, category, date, and description,
 * ordered newest first. Displays an empty state when no expenses exist, plus loading and error states.
 */
export function ExpenseHistoryScreen() {
  const { session, signOut } = useSession();
  const theme = useTheme();
  const safeAreaInsets = useSafeAreaInsets();

  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        signOut();
        return;
      }
      setError(describeError(e));
    },
    [signOut],
  );

  const load = useCallback(async () => {
    if (!session) return;
    setLoading(true);
    setError(null);
    try {
      const response = await fetchExpenses(session, { page: 0, size: PAGE_SIZE });
      setExpenses(response.content);
      setPage(0);
      setTotalPages(response.page.totalPages);
      setTotalElements(response.page.totalElements);
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  const loadMore = async () => {
    if (!session || loadingMore || page + 1 >= totalPages) return;
    setLoadingMore(true);
    try {
      const next = await fetchExpenses(session, { page: page + 1, size: PAGE_SIZE });
      setExpenses((current) => [...current, ...next.content]);
      setPage(next.page.number);
      setTotalPages(next.page.totalPages);
    } catch (e) {
      handleError(e);
    } finally {
      setLoadingMore(false);
    }
  };

  const contentPlatformStyle = Platform.select({
    android: {
      paddingTop: safeAreaInsets.top + Spacing.two,
      paddingBottom: safeAreaInsets.bottom + BottomTabInset + Spacing.three,
    },
    ios: {
      paddingTop: safeAreaInsets.top,
      paddingBottom: Spacing.three,
    },
    web: {
      paddingTop: Spacing.six,
      paddingBottom: Spacing.four,
    },
  });

  return (
    <ThemedView style={localStyles.screen}>
      <ScrollView
        style={{ backgroundColor: theme.background }}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={[styles.contentContainer, contentPlatformStyle]}
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={loading && expenses.length > 0} onRefresh={load} />}>
        <View style={styles.page}>
          <View style={styles.headerRow}>
            <View style={localStyles.titleWithBack}>
              {router.canGoBack() && <BackButton onPress={() => router.back()} />}
              <View>
                <ThemedText type="subtitle">Expense History</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {session ? `Signed in as ${session.userName}` : 'Monitor your previous spending'}
                </ThemedText>
              </View>
            </View>
            <Button label="Refresh" onPress={load} disabled={loading} secondary />
          </View>

          {/* Error State */}
          {error && (
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText style={styles.errorText}>{error}</ThemedText>
              <Button label="Try again" onPress={load} secondary onCard />
            </ThemedView>
          )}

          {/* Loading State */}
          {loading && expenses.length === 0 && (
            <ActivityIndicator style={styles.loader} size="large" color={Brand.green} />
          )}

          {/* Empty State (AC 2) */}
          {!loading && !error && expenses.length === 0 && (
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold">No expenses recorded yet</ThemedText>
              <ThemedText themeColor="textSecondary">
                Your expense history will appear here once you record spending in the Money tab.
              </ThemedText>
              <Button label="Go to Money" onPress={() => router.replace('/finances')} primary onCard />
            </ThemedView>
          )}

          {/* List of Expenses (AC 1) */}
          {!loading && expenses.length > 0 && (
            <ThemedText type="smallBold">
              All transactions ({totalElements})
            </ThemedText>
          )}

          {expenses.map((expense) => (
            <ThemedView key={expense.id} type="backgroundElement" style={styles.card}>
              <View style={styles.headerRow}>
                <ThemedText type="smallBold" style={styles.amount}>
                  {formatAmount(expense.amount)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatDate(expense.date)}
                </ThemedText>
              </View>

              {/* Category with safe fallback for missing/null category */}
              <View style={styles.row}>
                <View
                  style={[
                    styles.dot,
                    { backgroundColor: expense.category?.color ?? theme.textSecondary },
                  ]}
                />
                <ThemedText type="small">
                  {expense.category?.name ?? 'Uncategorized'}
                </ThemedText>
              </View>

              {/* Description */}
              {expense.description ? (
                <ThemedText type="small" themeColor="textSecondary">
                  {expense.description}
                </ThemedText>
              ) : null}

              {/* View Details button (US22) */}
              <View style={[styles.row, { marginTop: Spacing.one }]}>
                <Button
                  label="View details"
                  onPress={() => router.push(`/expenses/${expense.id}` as any)}
                  secondary
                  onCard
                />
              </View>
            </ThemedView>
          ))}

          {/* Pagination / Load more */}
          {page + 1 < totalPages && (
            <Button
              label={loadingMore ? 'Loading…' : 'Load more'}
              onPress={loadMore}
              disabled={loadingMore}
              secondary
            />
          )}
        </View>
      </ScrollView>
    </ThemedView>
  );
}

const localStyles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  titleWithBack: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
});
