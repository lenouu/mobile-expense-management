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
import { WindowControl, type WindowValue } from '@/features/finances/components/window-control';
import { fetchCurrentPeriod, type FinancialPeriod } from '@/features/finances/lib/finances-api';
import { type IncomeSummary, fetchIncomeSummary } from '@/features/finances/lib/income-api';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_SOURCE_COLOR = '#1F9D55';

/**
 * The income half of the Overview.
 *
 * <p>The same three questions as the expense side, in the other direction: how much came in, from
 * where, and how that compares with the window before it - then the shape of the window day by day.
 *
 * <p>Income is never netted against spending here. The Dashboard is where the two are set against
 * each other; this screen describes each side on its own terms.
 */
export function IncomeOverview({
  session,
  onSignOut,
  onSwitch,
}: {
  session: Session;
  onSignOut: (message?: string) => void;
  /** Puts the expense half back on screen. */
  onSwitch: (ledger: 'expenses') => void;
}) {
  const theme = useTheme();

  const [period, setPeriod] = useState<FinancialPeriod | null>(null);
  const [window, setWindow] = useState<WindowValue>({ key: 'period' });
  const [summary, setSummary] = useState<IncomeSummary | null>(null);
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

  const range = useMemo(() => {
    if (window.key === 'period') {
      return period ? { from: period.startDate, to: period.endDate } : null;
    }
    return window.from && window.to ? { from: window.from, to: window.to } : null;
  }, [window, period]);

  useEffect(() => {
    if (!range) return;

    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchIncomeSummary(session, range)
      .then((result) => {
        if (!cancelled) setSummary(result);
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
    // The dates are what matter, not the object identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, range?.from, range?.to, handleError]);

  const total = summary?.total.amount ?? 0;
  const changePercent = summary?.changePercent ?? null;
  const more = (summary?.change.amount ?? 0) > 0;

  const busiest = Math.max(1, ...(summary?.daily ?? []).map((day) => day.total.amount));
  const busiestDay = (summary?.daily ?? []).reduce<{
    date: string;
    total: { amount: number; currency: string };
  } | null>(
    (best, day) =>
      day.total.amount > (best?.total.amount ?? 0) ? { date: day.date, total: day.total } : best,
    null,
  );
  const averagePerDay = summary && summary.daily.length > 0 ? total / summary.daily.length : 0;

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: theme.background }}
      contentContainerStyle={styles.content}
      contentInsetAdjustmentBehavior="automatic"
      refreshControl={
        <RefreshControl
          refreshing={loading && summary !== null}
          onRefresh={() => setWindow({ ...window })}
        />
      }>
      <View style={styles.page}>
        {/* Expenses or income: the same window, the other direction. */}
        <View style={[styles.switchRow, { borderColor: theme.border }]}>
          <Pressable
            onPress={() => onSwitch('expenses')}
            accessibilityRole="tab"
            accessibilityState={{ selected: false }}
            accessibilityLabel="Expenses overview"
            style={styles.ledgerTab}>
            <ThemedText type="small" themeColor="textMuted">
              Expenses
            </ThemedText>
          </Pressable>

          <Pressable
            onPress={() => undefined}
            accessibilityRole="tab"
            accessibilityState={{ selected: true }}
            accessibilityLabel="Income overview"
            style={[styles.ledgerTab, { backgroundColor: theme.backgroundSelected }]}>
            <ThemedText type="smallBold">Income</ThemedText>
          </Pressable>
        </View>

        <WindowControl
          period={period}
          value={window}
          onChange={(next) => setWindow({ key: next.key, from: next.from, to: next.to })}
        />

        {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
        {loading && !summary ? <ActivityIndicator style={styles.loader} /> : null}

        {summary ? (
          <>
            <FadeIn>
              <ThemedView type="backgroundElement" style={styles.hero}>
                <ThemedText type="small" themeColor="textMuted">
                  Money in
                </ThemedText>
                <ThemedText type="display">{formatMoney(summary.total)}</ThemedText>

                <ThemedText type="small" themeColor="textSecondary">
                  {formatLongDate(summary.from)} – {formatLongDate(summary.to)}
                </ThemedText>

                {changePercent !== null ? (
                  <ThemedText type="small" style={{ color: more ? Brand.green : Status.danger }}>
                    {more ? '▲' : '▼'} {Math.abs(changePercent)}% vs the window before (
                    {formatMoney(summary.previousTotal)})
                  </ThemedText>
                ) : (
                  <ThemedText type="small" themeColor="textMuted">
                    Nothing in the previous window to compare with.
                  </ThemedText>
                )}

                <View style={styles.facts}>
                  <Fact label="Payments" value={String(summary.count)} />
                  <Fact
                    label="A day on average"
                    value={formatMoney({ amount: averagePerDay, currency: summary.total.currency })}
                  />
                  <Fact label="Sources" value={String(summary.bySource.length)} />
                </View>
              </ThemedView>
            </FadeIn>

            {summary.bySource.length > 0 ? (
              <FadeIn delay={60}>
                <View style={styles.section}>
                  <ThemedText type="overline" themeColor="textMuted">
                    Where it came from
                  </ThemedText>

                  <ThemedView type="backgroundElement" style={styles.card}>
                    {summary.bySource.map((slice) => (
                      <View key={slice.source.id} style={styles.slice}>
                        <View style={styles.sliceHead}>
                          <CategoryBadge
                            icon={slice.source.icon}
                            color={slice.source.color ?? DEFAULT_SOURCE_COLOR}
                            size={26}
                          />
                          <ThemedText type="small" style={styles.sliceName} numberOfLines={1}>
                            {slice.source.name}
                          </ThemedText>
                          <ThemedText type="smallBold" style={styles.sliceAmount}>
                            {formatMoney(slice.total)}
                          </ThemedText>
                        </View>

                        <View style={styles.sliceMeta}>
                          <ThemedText type="caption" themeColor="textMuted">
                            {slice.share}% of what came in
                          </ThemedText>
                          <ThemedText type="caption" themeColor="textMuted">
                            {slice.count} {slice.count === 1 ? 'payment' : 'payments'}
                          </ThemedText>
                        </View>

                        <ProgressBar value={slice.total.amount} max={Math.max(1, total)} height={6} />
                      </View>
                    ))}
                  </ThemedView>
                </View>
              </FadeIn>
            ) : null}

            {summary.daily.length > 1 ? (
              <FadeIn delay={120}>
                <View style={styles.section}>
                  <ThemedText type="overline" themeColor="textMuted">
                    Day by day
                  </ThemedText>

                  <ThemedView type="backgroundElement" style={styles.card}>
                    <View style={styles.strip}>
                      {summary.daily.map((day) => (
                        <View key={day.date} style={styles.stripColumn}>
                          <View
                            style={[
                              styles.stripBar,
                              {
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
                        {formatLongDate(summary.daily[0].date)}
                      </ThemedText>
                      <ThemedText type="caption" themeColor="textMuted">
                        {formatLongDate(summary.daily[summary.daily.length - 1].date)}
                      </ThemedText>
                    </View>

                    {busiestDay && busiestDay.total.amount > 0 ? (
                      <ThemedText type="caption" themeColor="textSecondary">
                        Biggest day: {formatLongDate(busiestDay.date)} · {formatMoney(busiestDay.total)}
                      </ThemedText>
                    ) : null}
                  </ThemedView>
                </View>
              </FadeIn>
            ) : null}

            {summary.count === 0 ? (
              <FadeIn>
                <ThemedView type="backgroundElement" style={styles.card}>
                  <ThemedText themeColor="textSecondary">
                    Nothing came in during this window. Record income and this page fills in.
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
  content: { flexGrow: 1, paddingBottom: Spacing.six },
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
  ledgerTab: { paddingHorizontal: Spacing.three, paddingVertical: Spacing.one, borderRadius: 999 },
  hero: { borderRadius: Radius.card, padding: Spacing.four, gap: Spacing.two },
  facts: { flexDirection: 'row', gap: Spacing.two, marginTop: Spacing.two },
  fact: { flex: 1, gap: Spacing.half },
  section: { gap: Spacing.two },
  card: { borderRadius: Radius.card, padding: Spacing.three, gap: Spacing.three },
  slice: { gap: Spacing.one },
  sliceHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  sliceName: { flex: 1, flexShrink: 1, minWidth: 0 },
  sliceAmount: { flexShrink: 0 },
  sliceMeta: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: Spacing.two },
  strip: { flexDirection: 'row', alignItems: 'flex-end', gap: 2, height: 60 },
  stripColumn: { flex: 1, justifyContent: 'flex-end' },
  stripBar: { width: '100%', borderRadius: 2 },
  stripDates: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  loader: { marginTop: Spacing.five },
  error: { color: Status.danger },
});
