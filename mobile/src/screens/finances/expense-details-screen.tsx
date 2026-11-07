import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Platform, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { useLocalSearchParams, router } from 'expo-router';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BackButton } from '@/components/ui/back-button';
import { Button } from '@/components/ui/primitives';
import { BottomTabInset, Brand, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import {
  financeStyles as styles,
  formatAmount,
  formatDate,
} from '@/features/finances/components/finance-ui';
import { Expense, fetchExpense } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
import { describeError } from '@/utils/errors';

function formatTimestamp(isoString: string): string {
  try {
    const d = new Date(isoString);
    if (Number.isNaN(d.getTime())) return isoString;
    return d.toLocaleString(undefined, {
      dateStyle: 'medium',
      timeStyle: 'short',
    });
  } catch {
    return isoString;
  }
}

/**
 * US22: View expense details.
 *
 * Displays all fields of a single expense:
 * - Amount, date, category (with color fallback), description, created/updated timestamps.
 * - Handles loading, not-found (404), error, and unauthenticated states.
 */
export function ExpenseDetailsScreen() {
  const { id } = useLocalSearchParams<{ id?: string }>();
  const { session, signOut } = useSession();
  const theme = useTheme();
  const safeAreaInsets = useSafeAreaInsets();

  const [expense, setExpense] = useState<Expense | null>(null);
  const [loading, setLoading] = useState(true);
  const [notFound, setNotFound] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadDetails = useCallback(async () => {
    if (!session) return;
    if (!id || Number.isNaN(Number(id)) || Number(id) <= 0) {
      setLoading(false);
      setNotFound(true);
      setError('Invalid expense ID format.');
      return;
    }

    setLoading(true);
    setNotFound(false);
    setError(null);

    try {
      const data = await fetchExpense(session, id);
      setExpense(data);
    } catch (e) {
      if (e instanceof ApiRequestError) {
        if (e.status === 401) {
          signOut();
          return;
        }
        if (e.status === 404 || e.status === 403) {
          setNotFound(true);
          setError('Expense not found.');
          return;
        }
        if (e.status === 400) {
          setNotFound(true);
          setError(e.message || 'Invalid expense request.');
          return;
        }
      }
      setError(describeError(e));
    } finally {
      setLoading(false);
    }
  }, [id, session, signOut]);

  useEffect(() => {
    loadDetails();
  }, [loadDetails]);

  const goBack = () => {
    if (router.canGoBack()) {
      router.back();
    } else {
      router.replace('/history');
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
        refreshControl={
          <RefreshControl refreshing={loading && expense !== null} onRefresh={loadDetails} />
        }>
        <View style={styles.page}>
          {/* Header */}
          <View style={styles.headerRow}>
            <View style={localStyles.titleWithBack}>
              <BackButton onPress={goBack} />
              <View>
                <ThemedText type="subtitle">Expense Details</ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {expense ? `Transaction #${expense.id}` : 'Verify transaction'}
                </ThemedText>
              </View>
            </View>
            <Button label="Close" onPress={goBack} secondary />
          </View>

          {/* Loading State */}
          {loading && !expense && (
            <ActivityIndicator style={styles.loader} size="large" color={Brand.green} />
          )}

          {/* Not Found State (AC 2 & AC 3) */}
          {notFound && !loading && (
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText type="smallBold">Expense not found</ThemedText>
              <ThemedText themeColor="textSecondary">
                This transaction either does not exist, was deleted, or belongs to another account.
              </ThemedText>
              <Button label="Back to history" onPress={goBack} primary onCard />
            </ThemedView>
          )}

          {/* General Error State */}
          {error && !notFound && !loading && (
            <ThemedView type="backgroundElement" style={styles.card}>
              <ThemedText style={styles.errorText}>{error}</ThemedText>
              <Button label="Try again" onPress={loadDetails} secondary onCard />
            </ThemedView>
          )}

          {/* Details Content (AC 1 & AC 6) */}
          {expense && !loading && (
            <>
              {/* Amount Highlight Card */}
              <ThemedView type="backgroundElement" style={[styles.card, localStyles.heroCard]}>
                <ThemedText type="caption" themeColor="textSecondary">
                  Total Amount
                </ThemedText>
                <ThemedText type="title" style={localStyles.heroAmount}>
                  {formatAmount(expense.amount)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  Date of expense: {formatDate(expense.date)}
                </ThemedText>
              </ThemedView>

              {/* Transaction Metadata Card */}
              <ThemedView type="backgroundElement" style={styles.card}>
                <ThemedText type="smallBold">Transaction Information</ThemedText>

                {/* Category with Safe Fallback (AC 6) */}
                <View style={localStyles.infoRow}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Category
                  </ThemedText>
                  <View style={styles.row}>
                    <View
                      style={[
                        styles.dot,
                        { backgroundColor: expense.category?.color ?? theme.textSecondary },
                      ]}
                    />
                    <ThemedText type="smallBold">
                      {expense.category?.name ?? 'Uncategorized'}
                    </ThemedText>
                  </View>
                </View>

                {/* Date */}
                <View style={localStyles.infoRow}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Expense Date
                  </ThemedText>
                  <ThemedText type="smallBold">{formatDate(expense.date)}</ThemedText>
                </View>

                {/* Description */}
                <View style={localStyles.infoColumn}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Description / Notes
                  </ThemedText>
                  <ThemedText type="small">
                    {expense.description ? expense.description : 'No description provided'}
                  </ThemedText>
                </View>

                {/* Created Timestamp */}
                <View style={localStyles.infoRow}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Recorded At
                  </ThemedText>
                  <ThemedText type="caption">{formatTimestamp(expense.createdAt)}</ThemedText>
                </View>

                {/* Updated Timestamp */}
                <View style={localStyles.infoRow}>
                  <ThemedText type="small" themeColor="textSecondary">
                    Last Updated
                  </ThemedText>
                  <ThemedText type="caption">{formatTimestamp(expense.updatedAt)}</ThemedText>
                </View>
              </ThemedView>

              {/* Actions */}
              <View style={localStyles.actionsRow}>
                <Button label="Back to history" onPress={goBack} primary />
              </View>
            </>
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
  heroCard: {
    alignItems: 'center',
    paddingVertical: Spacing.four,
    gap: Spacing.one,
  },
  heroAmount: {
    color: Brand.green,
    fontSize: 32,
    lineHeight: 38,
    fontWeight: '700',
  },
  infoRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: Spacing.half,
  },
  infoColumn: {
    gap: Spacing.half,
    paddingVertical: Spacing.half,
  },
  actionsRow: {
    marginTop: Spacing.two,
    gap: Spacing.two,
  },
});
