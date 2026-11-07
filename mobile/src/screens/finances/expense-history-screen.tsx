import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Platform, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { router } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BackButton } from '@/components/ui/back-button';
import { Button, Chip } from '@/components/ui/primitives';
import { BottomTabInset, Brand, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import {
  Field,
  financeStyles as styles,
  formatAmount,
  formatDate,
  TextField,
} from '@/features/finances/components/finance-ui';
import {
  Expense,
  fetchExpenses,
  fetchMyCategories,
  MyCategory,
} from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
import { describeError } from '@/utils/errors';

const PAGE_SIZE = 20;

/**
 * US21 & US25: View & filter expense history.
 *
 * Lists the authenticated user's expenses with amount, category, date, and description,
 * ordered newest first. Supports server-side filtering by category and date range (start/end).
 */
export function ExpenseHistoryScreen() {
  const { session, signOut } = useSession();
  const theme = useTheme();
  const safeAreaInsets = useSafeAreaInsets();

  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [dateError, setDateError] = useState<string | null>(null);

  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  // Filter state
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [fromDate, setFromDate] = useState('');
  const [toDate, setToDate] = useState('');
  const [appliedCategory, setAppliedCategory] = useState<number | null>(null);
  const [appliedFrom, setAppliedFrom] = useState<string | null>(null);
  const [appliedTo, setAppliedTo] = useState<string | null>(null);

  const hasActiveFilter =
    appliedCategory !== null || appliedFrom !== null || appliedTo !== null;

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

  // Load user categories once
  useEffect(() => {
    if (!session) return;
    fetchMyCategories(session)
      .then(setCategories)
      .catch((e) => {
        if (e instanceof ApiRequestError && e.status === 401) signOut();
      });
  }, [session, signOut]);

  const loadExpensesWithFilters = useCallback(
    async (catId: number | null, from: string | null, to: string | null) => {
      if (!session) return;
      setLoading(true);
      setError(null);
      try {
        const response = await fetchExpenses(session, {
          page: 0,
          size: PAGE_SIZE,
          categoryId: catId,
          from: from || undefined,
          to: to || undefined,
        });
        setExpenses(response.content);
        setPage(0);
        setTotalPages(response.page.totalPages);
        setTotalElements(response.page.totalElements);
        setAppliedCategory(catId);
        setAppliedFrom(from);
        setAppliedTo(to);
      } catch (e) {
        handleError(e);
      } finally {
        setLoading(false);
      }
    },
    [session, handleError],
  );

  // Initial load
  useEffect(() => {
    loadExpensesWithFilters(null, null, null);
  }, [loadExpensesWithFilters]);

  const handleApplyFilters = () => {
    setDateError(null);
    const cleanFrom = fromDate.trim() || null;
    const cleanTo = toDate.trim() || null;

    if (cleanFrom && cleanTo && cleanFrom > cleanTo) {
      setDateError("'From' date must not be after 'To' date");
      return;
    }

    loadExpensesWithFilters(selectedCategoryId, cleanFrom, cleanTo);
  };

  const handleCategorySelect = (id: number | null) => {
    setSelectedCategoryId(id);
    const cleanFrom = fromDate.trim() || null;
    const cleanTo = toDate.trim() || null;
    if (cleanFrom && cleanTo && cleanFrom > cleanTo) {
      setDateError("'From' date must not be after 'To' date");
      return;
    }
    setDateError(null);
    loadExpensesWithFilters(id, cleanFrom, cleanTo);
  };

  const handleResetFilters = () => {
    setSelectedCategoryId(null);
    setFromDate('');
    setToDate('');
    setDateError(null);
    loadExpensesWithFilters(null, null, null);
  };

  const loadMore = async () => {
    if (!session || loadingMore || page + 1 >= totalPages) return;
    setLoadingMore(true);
    try {
      const next = await fetchExpenses(session, {
        page: page + 1,
        size: PAGE_SIZE,
        categoryId: appliedCategory,
        from: appliedFrom || undefined,
        to: appliedTo || undefined,
      });
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

  const selectedCategoryName =
    categories.find((c) => c.id === appliedCategory)?.name ?? 'Selected';

  return (
    <ThemedView style={localStyles.screen}>
      <ScrollView
        style={{ backgroundColor: theme.background }}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={[styles.contentContainer, contentPlatformStyle]}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl
            refreshing={loading && expenses.length > 0}
            onRefresh={() => loadExpensesWithFilters(appliedCategory, appliedFrom, appliedTo)}
          />
        }>
        <View style={styles.page}>
          {/* Header */}
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
            <Button
              label="Refresh"
              onPress={() => loadExpensesWithFilters(appliedCategory, appliedFrom, appliedTo)}
              disabled={loading}
              secondary
            />
          </View>

          {/* Filter Card (US25) */}
          <ThemedView type="backgroundElement" style={styles.card}>
            <View style={styles.headerRow}>
              <ThemedText type="smallBold">Filter expenses</ThemedText>
              {hasActiveFilter && (
                <Button label="Reset filters" onPress={handleResetFilters} secondary onCard />
              )}
            </View>

            {/* Category Selector Chips (AC 1) */}
            <ThemedText type="small" themeColor="textSecondary">
              Category
            </ThemedText>
            <View style={styles.row}>
              <Chip
                label="All"
                selected={selectedCategoryId === null}
                onPress={() => handleCategorySelect(null)}
                onCard
              />
              {categories.map((c) => (
                <Chip
                  key={c.id}
                  label={c.name}
                  selected={selectedCategoryId === c.id}
                  onPress={() => handleCategorySelect(selectedCategoryId === c.id ? null : c.id)}
                  onCard
                />
              ))}
            </View>

            {/* Date Range Inputs (AC 2) */}
            <View style={localStyles.dateRow}>
              <View style={localStyles.dateCol}>
                <Field label="From (YYYY-MM-DD)">
                  <TextField
                    placeholder="e.g. 2026-09-01"
                    value={fromDate}
                    onChangeText={setFromDate}
                    autoCorrect={false}
                    maxLength={10}
                  />
                </Field>
              </View>
              <View style={localStyles.dateCol}>
                <Field label="To (YYYY-MM-DD)">
                  <TextField
                    placeholder="e.g. 2026-09-30"
                    value={toDate}
                    onChangeText={setToDate}
                    autoCorrect={false}
                    maxLength={10}
                  />
                </Field>
              </View>
            </View>

            {dateError && <ThemedText style={styles.errorText}>{dateError}</ThemedText>}

            <Button label="Apply filters" onPress={handleApplyFilters} primary onCard />

            {/* Active filter badge / info */}
            {hasActiveFilter && (
              <ThemedView style={localStyles.filterStatusBanner}>
                <ThemedText type="caption" themeColor="textSecondary">
                  Active filter:{' '}
                  {appliedCategory !== null ? `Category: ${selectedCategoryName}` : 'All categories'}
                  {appliedFrom ? ` · From: ${appliedFrom}` : ''}
                  {appliedTo ? ` · To: ${appliedTo}` : ''}
                </ThemedText>
              </ThemedView>
            )}
          </ThemedView>

          {/* General Error State */}
          {error && (
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText style={styles.errorText}>{error}</ThemedText>
              <Button
                label="Try again"
                onPress={() => loadExpensesWithFilters(appliedCategory, appliedFrom, appliedTo)}
                secondary
                onCard
              />
            </ThemedView>
          )}

          {/* Loading Indicator */}
          {loading && expenses.length === 0 && (
            <ActivityIndicator style={styles.loader} size="large" color={Brand.green} />
          )}

          {/* Empty State when filters return no matches (AC 3) */}
          {!loading && !error && expenses.length === 0 && hasActiveFilter && (
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold">No expenses match your filters</ThemedText>
              <ThemedText themeColor="textSecondary">
                Try selecting a different category or adjusting the date range.
              </ThemedText>
              <Button label="Reset filters" onPress={handleResetFilters} primary onCard />
            </ThemedView>
          )}

          {/* Default Empty State when no expenses exist at all (US21 AC 2) */}
          {!loading && !error && expenses.length === 0 && !hasActiveFilter && (
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold">No expenses recorded yet</ThemedText>
              <ThemedText themeColor="textSecondary">
                Your expense history will appear here once you record spending in the Money tab.
              </ThemedText>
              <Button label="Go to Money" onPress={() => router.replace('/finances')} primary onCard />
            </ThemedView>
          )}

          {/* List of Filtered Expenses */}
          {!loading && expenses.length > 0 && (
            <ThemedText type="smallBold">
              Transactions ({totalElements})
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

              {/* View Details button */}
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
  dateRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  dateCol: {
    flex: 1,
  },
  filterStatusBanner: {
    padding: Spacing.one + Spacing.half,
    borderRadius: Radius.card,
    backgroundColor: 'rgba(0, 0, 0, 0.04)',
  },
});
