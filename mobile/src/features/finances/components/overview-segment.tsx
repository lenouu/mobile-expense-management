import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { FadeIn } from '@/components/ui/fade-in';
import { ProgressBar } from '@/components/ui/progress-bar';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { formatLongDate, formatMoney } from '@/features/finances/components/finance-ui';
import { IncomeOverview } from '@/features/finances/components/income-overview';
import { WindowControl, type WindowValue } from '@/features/finances/components/window-control';
import {
  type ExpenseOverview,
  fetchCurrentPeriod,
  fetchExpenseOverview,
  type FinancialPeriod,
} from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_CATEGORY_COLOR = '#01916D';

/**
 * The Overview segment: one window, and what it adds up to.
 *
 * <p>Three questions in order - how much, on what, and how that compares with the window before it -
 * then the shape of the window day by day. Everything comes from one request, so the headline, the
 * categories and the strip cannot disagree.
 *
 * <p>A line chart is deliberately absent: inside a single window it decorates rather than informs.
 * Trends across windows belong to the Dashboard, where the comparison is the whole point.
 */
export function OverviewSegment({
  session,
  onSignOut,
}: {
  session: Session;
  onSignOut: (message?: string) => void;
}) {
  const theme = useTheme();

  /** Which side of the ledger the overview is describing. */
  const [ledger, setLedger] = useState<'expenses' | 'income'>('expenses');

  const [period, setPeriod] = useState<FinancialPeriod | null>(null);
  const [window, setWindow] = useState<WindowValue>({ key: 'period' });
  const [overview, setOverview] = useState<ExpenseOverview | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        onSignOut('Your session has expired. Please sign in again.');
        return;
      }
      setError(describeError(e));
    },
    [onSignOut],
  );

  useEffect(() => {
    let cancelled = false;
    fetchCurrentPeriod(session)
      .then((current) => {
        if (!cancelled) setPeriod(current);
      })
      .catch(() => {
        // The server falls back to the current period itself.
      });
    return () => {
      cancelled = true;
    };
  }, [session]);

  /** The dates to ask for; an empty object means "whatever this period is". */
  const range = useMemo(() => {
    if (window.key === 'period') {
      return period ? { from: period.startDate, to: period.endDate } : {};
    }
    return { from: window.from, to: window.to };
  }, [window, period]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchExpenseOverview(session, range)
      .then((result) => {
        if (!cancelled) setOverview(result);
      })
      .catch((e) => {
        if (!cancelled) handleError(e);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [session, range, handleError]);

  const total = overview?.total.amount ?? 0;
  const changePercent = overview?.changePercent;
  const spentMore = (overview?.change.amount ?? 0) > 0;

  const busiest = Math.max(1, ...(overview?.daily ?? []).map((day) => day.total.amount));
  const busiestDay = (overview?.daily ?? []).reduce<{
    date: string;
    total: { amount: number; currency: string };
  } | null>(
    (best, day) =>
      day.total.amount > (best?.total.amount ?? 0) ? { date: day.date, total: day.total } : best,
    null,
  );
  const averagePerDay = overview && overview.daily.length > 0 ? total / overview.daily.length : 0;

  // The other half of the overview: the same questions, asked about money coming in.
  if (ledger === 'income') {
    return (
      <IncomeOverview
        session={session}
        onSignOut={onSignOut}
        onSwitch={() => setLedger('expenses')}
      />
    );
  }

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: theme.background }}
      contentContainerStyle={styles.content}
      contentInsetAdjustmentBehavior="automatic"
      refreshControl={
        <RefreshControl
          refreshing={loading && overview !== null}
          onRefresh={() => setWindow({ ...window })}
        />
      }>
      <View style={styles.page}>
        {/* Expenses or income: one overview, two directions. */}
        <View style={[styles.switchRow, { borderColor: theme.border }]}>
          <Pressable
            onPress={() => undefined}
            accessibilityRole="tab"
            accessibilityState={{ selected: true }}
            accessibilityLabel="Expenses overview"
            style={[styles.ledgerTab, { backgroundColor: theme.backgroundSelected }]}>
            <ThemedText type="smallBold">Expenses</ThemedText>
          </Pressable>

          <Pressable
            onPress={() => setLedger('income')}
            accessibilityRole="tab"
            accessibilityState={{ selected: false }}
            accessibilityLabel="Income overview"
            style={styles.ledgerTab}>
            <ThemedText type="small" themeColor="textMuted">
              Income
            </ThemedText>
          </Pressable>
        </View>

        {/* The same control the Transactions segment uses: one window, two views of it. */}
        <WindowControl
          period={period}
          value={window}
          onChange={(next) => setWindow({ key: next.key, from: next.from, to: next.to })}
        />

        {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
        {loading && !overview ? <ActivityIndicator style={styles.loader} /> : null}

        {overview ? (
          <>
            <FadeIn>
              <ThemedView type="backgroundElement" style={styles.hero}>
                <ThemedText type="small" themeColor="textMuted">
                  You have spent
                </ThemedText>
                <ThemedText type="display">{formatMoney(overview.total)}</ThemedText>

                <ThemedText type="small" themeColor="textSecondary">
                  {formatLongDate(overview.from)} – {formatLongDate(overview.to)}
                </ThemedText>

                {changePercent !== null ? (
                  <ThemedText type="small" style={{ color: spentMore ? Status.danger : Brand.green }}>
                    {spentMore ? '▲' : '▼'} {Math.abs(changePercent ?? 0)}% vs the window before (
                    {formatMoney(overview.previousTotal)})
                  </ThemedText>
                ) : (
                  <ThemedText type="small" themeColor="textMuted">
                    Nothing in the previous window to compare with.
                  </ThemedText>
                )}

                {/* The facts a reader extracts without doing arithmetic. */}
                <View style={styles.facts}>
                  <Fact label="Expenses" value={String(overview.count)} />
                  <Fact
                    label="A day on average"
                    value={formatMoney({ amount: averagePerDay, currency: overview.total.currency })}
                  />
                  <Fact label="Categories" value={String(overview.byCategory.length)} />
                </View>
              </ThemedView>
            </FadeIn>

            {overview.byCategory.length > 0 ? (
              <FadeIn delay={60}>
                <View style={styles.section}>
                  <ThemedText type="overline" themeColor="textMuted">
                    Where it went
                  </ThemedText>

                  <ThemedView type="backgroundElement" style={styles.card}>
                    {overview.byCategory.map((slice) => (
                      <View key={slice.category.id} style={styles.categoryBlock}>
                        <View style={styles.categoryHead}>
                          <CategoryBadge
                            icon={slice.category.icon}
                            color={slice.category.color ?? DEFAULT_CATEGORY_COLOR}
                            size={26}
                          />

                          <ThemedText type="small" style={styles.categoryName} numberOfLines={1}>
                            {slice.category.name}
                          </ThemedText>

                          <ThemedText type="smallBold" style={styles.categoryAmount}>
                            {formatMoney(slice.total)}
                          </ThemedText>
                        </View>

                        <View style={styles.categoryMeta}>
                          <ThemedText type="caption" themeColor="textMuted">
                            {slice.share}% of the window
                          </ThemedText>

                          {slice.changePercent !== null ? (
                            <ThemedText
                              type="caption"
                              style={{ color: slice.change.amount > 0 ? Status.danger : Brand.green }}>
                              {slice.change.amount > 0 ? '+' : '−'}
                              {formatMoney({
                                amount: Math.abs(slice.change.amount),
                                currency: slice.change.currency,
                              })}{' '}
                              vs before
                            </ThemedText>
                          ) : null}
                        </View>

                        <ProgressBar value={slice.total.amount} max={total} height={6} />
                      </View>
                    ))}
                  </ThemedView>
                </View>
              </FadeIn>
            ) : null}

            {overview.daily.length > 1 ? (
              <FadeIn delay={120}>
                <View style={styles.section}>
                  <ThemedText type="overline" themeColor="textMuted">
                    Day by day
                  </ThemedText>

                  <ThemedView type="backgroundElement" style={styles.card}>
                    <View style={styles.strip}>
                      {overview.daily.map((day) => (
                        <View key={day.date} style={styles.stripColumn}>
                          <View
                            style={[
                              styles.stripBar,
                              {
                                // A sliver for a quiet day, so the gap is visible as a day.
                                height: Math.max(2, Math.round((day.total.amount / busiest) * 60)),
                                backgroundColor:
                                  day.total.amount === 0
                                    ? theme.backgroundSelected
                                    : busiestDay && day.date === busiestDay.date
                                      ? Brand.green
                                      : 'rgba(1, 145, 109, 0.45)',
                              },
                            ]}
                          />
                        </View>
                      ))}
                    </View>

                    <View style={styles.stripDates}>
                      <ThemedText type="caption" themeColor="textMuted">
                        {formatLongDate(overview.daily[0].date)}
                      </ThemedText>
                      <ThemedText type="caption" themeColor="textMuted">
                        {formatLongDate(overview.daily[overview.daily.length - 1].date)}
                      </ThemedText>
                    </View>

                    {busiestDay && busiestDay.total.amount > 0 ? (
                      <ThemedText type="caption" themeColor="textSecondary">
                        Busiest day: {formatLongDate(busiestDay.date)} · {formatMoney(busiestDay.total)}
                      </ThemedText>
                    ) : null}
                  </ThemedView>
                </View>
              </FadeIn>
            ) : null}

            {overview.count === 0 ? (
              <FadeIn>
                <ThemedView type="backgroundElement" style={styles.card}>
                  <ThemedText themeColor="textSecondary">
                    Nothing recorded in this window. Record an expense and this page fills in.
                  </ThemedText>
                </ThemedView>
              </FadeIn>
            ) : null}
          </>
        ) : null}
      </View>
    </ScrollView>
  );
}

/** One labelled figure inside the hero, sized so three fit on a phone without wrapping. */
function Fact({ label, value }: { label: string; value: string }) {
  return (
    <View style={styles.fact}>
      <ThemedText type="caption" themeColor="textMuted" numberOfLines={2}>
        {label}
      </ThemedText>
      <ThemedText type="smallBold" numberOfLines={1}>
        {value}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  content: {
    flexGrow: 1,
    paddingBottom: Spacing.six,
  },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  switchRow: {
    flexDirection: 'row',
    alignSelf: 'flex-start',
    gap: 3,
    padding: 3,
    borderWidth: 1,
    borderRadius: 999,
  },
  ledgerTab: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 999,
  },
  hero: {
    borderRadius: Radius.card,
    padding: Spacing.four,
    gap: Spacing.two,
  },
  facts: {
    flexDirection: 'row',
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  fact: {
    flex: 1,
    gap: Spacing.half,
  },
  section: {
    gap: Spacing.two,
  },
  card: {
    borderRadius: Radius.card,
    padding: Spacing.three,
    gap: Spacing.three,
  },
  categoryBlock: {
    gap: Spacing.one,
  },
  categoryHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  /** The name gives way; the amount never does - a wrapped amount is unreadable. */
  categoryName: {
    flex: 1,
    flexShrink: 1,
    minWidth: 0,
  },
  categoryAmount: {
    flexShrink: 0,
  },
  categoryMeta: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  strip: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 2,
    height: 60,
  },
  stripColumn: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  stripBar: {
    width: '100%',
    borderRadius: 2,
  },
  stripDates: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  loader: {
    marginTop: Spacing.five,
  },
  error: {
    color: Status.danger,
  },
});
