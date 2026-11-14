import { router } from 'expo-router';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  View,
} from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BrandLockup } from '@/components/ui/brand-lockup';
import { FadeIn } from '@/components/ui/fade-in';
import { GroupedBars, type BarGroup } from '@/components/ui/grouped-bars';
import { LineChart, type Series } from '@/components/ui/line-chart';
import { ProgressBar } from '@/components/ui/progress-bar';
import { Button } from '@/components/ui/primitives';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { type AnalyticsSummary, fetchAnalytics } from '@/features/analytics/lib/analytics-api';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { formatLongDate, formatMoney } from '@/features/finances/components/finance-ui';
import { WindowControl, type WindowValue } from '@/features/finances/components/window-control';
import { fetchCurrentPeriod, type FinancialPeriod } from '@/features/finances/lib/finances-api';
import { useDataVersion } from '@/features/finances/lib/data-events';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_COLOR = '#01916D';
const INCOME_COLOR = '#1F9D55';
const EXPENSE_COLOR = '#D93025';
const NET_COLOR = '#2D7FF9';

type Mode = 'cumulative' | 'bucket';
type Metric = 'income' | 'expenses' | 'net';

/**
 * The Dashboard: did this window leave me ahead, and why?
 *
 * <p>Everything on the page answers that one question, in order: the headline (net, and the rate
 * that makes it comparable), the three figures against the window before, the chart the answer is
 * drawn on, the days worth naming, where the money went and came from, the plan in force, and three
 * sentences of analysis.
 *
 * <p>All of it comes from one request, so the headline and the chart cannot disagree.
 */
export function DashboardScreen() {
  const { session, signOut } = useSession();

  if (!session) {
    return <SignedOut />;
  }

  return <Analytics session={session} onSignOut={signOut} />;
}

function SignedOut() {
  return (
    <Screen withTabInset={false} contentStyle={styles.content}>
      <BrandLockup
        floating
        title="FinFlow"
        subtitle="Track. Understand. Improve. Sign in to see your money, or create an account to get started."
      />

      <ThemedView type="backgroundElement" style={styles.card}>
        <ThemedText type="small" themeColor="textSecondary">
          Sign in and this page becomes your dashboard: what came in, what went out, what you kept -
          and how this window compares with the one before it.
        </ThemedText>

        <Button label="Sign in" primary onPress={() => router.push('/auth/sign-in')} />
        <Button
          label="Create an account"
          secondary
          onCard
          onPress={() => router.push('/auth/sign-up')}
        />
      </ThemedView>
    </Screen>
  );
}

function Analytics({
  session,
  onSignOut,
}: {
  session: Session;
  onSignOut: (message?: string) => void;
}) {
  const theme = useTheme();

  const [period, setPeriod] = useState<FinancialPeriod | null>(null);
  const [window, setWindow] = useState<WindowValue>({ key: 'period' });
  const [data, setData] = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);
  /** Re-reads the figures when money is added elsewhere (receipt scan, SMS review). */
  const dataVersion = useDataVersion();
  const [error, setError] = useState<string | null>(null);

  const [mode, setMode] = useState<Mode>('cumulative');
  /** Lines for the trend, bars for the comparison. */
  const [view, setView] = useState<'lines' | 'bars'>('lines');
  /** Draws the previous window beside this one. */
  const [compare, setCompare] = useState(true);
  /** A window chosen explicitly for comparison; null means the one the server picked. */
  const [compareWindow, setCompareWindow] = useState<WindowValue | null>(null);
  const [compareData, setCompareData] = useState<AnalyticsSummary | null>(null);
  const [visible, setVisible] = useState<Record<Metric, boolean>>({
    income: true,
    expenses: true,
    net: true,
  });

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
        // The dashboard needs explicit dates; a failure here simply leaves it empty.
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

    fetchAnalytics(session, range)
      .then((result) => {
        if (!cancelled) setData(result);
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [session, range?.from, range?.to, handleError, dataVersion]);

  /** The lines the chart draws: cumulative running totals, or the buckets on their own. */
  const { chartSeries, labels } = useMemo(() => {
    if (!data) return { chartSeries: [] as Series[], labels: [] as string[] };

    if (mode === 'bucket') {
      const income = data.buckets.map((bucket) => bucket.income.amount);
      const expenses = data.buckets.map((bucket) => bucket.expenses.amount);
      const net = income.map((value, index) => value - (expenses[index] ?? 0));

      return {
        labels: data.buckets.map((bucket) => bucket.label),
        chartSeries: [
          { key: 'income', label: 'Income', color: INCOME_COLOR, values: income },
          { key: 'expenses', label: 'Expenses', color: EXPENSE_COLOR, values: expenses },
          { key: 'net', label: 'Net', color: NET_COLOR, values: net },
        ].filter((line) => visible[line.key as Metric]),
      };
    }

    const income = data.daily.map((day) => day.income.amount);
    const expenses = data.daily.map((day) => day.expenses.amount);

    let incomeRun = 0;
    let expenseRun = 0;
    let netRun = 0;

    const cumulativeIncome = income.map((value) => (incomeRun += value));
    const cumulativeExpenses = expenses.map((value) => (expenseRun += value));
    const cumulativeNet = income.map((value, index) => (netRun += value - (expenses[index] ?? 0)));

    const lines: Series[] = [
      { key: 'income', label: 'Income', color: INCOME_COLOR, values: cumulativeIncome },
      { key: 'expenses', label: 'Expenses', color: EXPENSE_COLOR, values: cumulativeExpenses },
      { key: 'net', label: 'Net', color: NET_COLOR, values: cumulativeNet },
    ].filter((line) => visible[line.key as Metric]);

    // The window being compared, drawn as a dashed ghost on the same day positions: any two
    // periods can be set against each other, not only the one immediately before.
    if (compare) {
      const other = compareData?.daily ?? data.previousDaily;

      const walk = (
        pick: (point: { income: { amount: number }; expenses: { amount: number } }) => number,
      ) => {
        let running = 0;
        return other.map((point) => (running += pick(point)));
      };

      const ghosts: Record<Metric, number[]> = {
        income: walk((point) => point.income.amount),
        expenses: walk((point) => point.expenses.amount),
        net: walk((point) => point.income.amount - point.expenses.amount),
      };

      for (const metric of ['income', 'expenses', 'net'] as const) {
        if (!visible[metric]) continue;

        lines.push({
          key: `${metric}-before`,
          label: `${metric === 'net' ? 'Net' : metric === 'income' ? 'Income' : 'Expenses'} before`,
          color:
            metric === 'income' ? INCOME_COLOR : metric === 'expenses' ? EXPENSE_COLOR : NET_COLOR,
          values: ghosts[metric],
          dashed: true,
        });
      }
    }

    return {
      labels: data.daily.map((day) => formatLongDate(day.date).replace(/\s\d{4}$/, '')),
      chartSeries: lines,
    };
  }, [data, mode, visible, compare, compareData]);

  // The compared window, fetched on its own, so any two periods can be set side by side.
  useEffect(() => {
    if (!compareWindow?.from || !compareWindow?.to) {
      setCompareData(null);
      return;
    }

    let cancelled = false;
    fetchAnalytics(session, { from: compareWindow.from, to: compareWindow.to })
      .then((result) => {
        if (!cancelled) setCompareData(result);
      })
      .catch(() => {
        if (!cancelled) setCompareData(null);
      });

    return () => {
      cancelled = true;
    };
  }, [session, compareWindow?.from, compareWindow?.to]);
  /** The metric the bars show: the first one still switched on. */
  const barMetric = (['income', 'expenses', 'net'] as const).find((metric) => visible[metric]) ?? 'income';

  const barLabels = useMemo(
    () =>
      (data?.buckets ?? []).map((bucket, index) =>
        // Short labels: the band is ~45 points wide.
        String(index + 1),
      ),
    [data],
  );

  const barGroups: BarGroup[] = useMemo(() => {
    if (!data) return [];

    const colour =
      barMetric === 'income' ? INCOME_COLOR : barMetric === 'expenses' ? EXPENSE_COLOR : NET_COLOR;

    const valueOf = (bucket: { income: { amount: number }; expenses: { amount: number }; }) =>
      barMetric === 'income'
        ? bucket.income.amount
        : barMetric === 'expenses'
          ? bucket.expenses.amount
          : bucket.income.amount - bucket.expenses.amount;

    const groups: BarGroup[] = [
      {
        key: 'current',
        label: `This window · ${barMetric === 'net' ? 'net' : barMetric}`,
        color: colour,
        values: data.buckets.map(valueOf),
      },
    ];

    if (compare) {
      // The chosen window if there is one, otherwise the one the server picked.
      const other = compareData
        ? compareData.buckets.map(valueOf)
        : data.previousBuckets.map(valueOf);

      groups.push({
        key: 'previous',
        label: compareData
          ? `Compared · ${formatLongDate(compareData.from).replace(/\s\d{4}$/, '')} – ${formatLongDate(compareData.to).replace(/\s\d{4}$/, '')}`
          : 'Window before',
        color: colour,
        ghost: true,
        values: other,
      });
    }

    return groups;
  }, [data, barMetric, compare, compareData]);
  const greeting = useMemo(() => {
    const raw = session.userName ?? '';
    const first = raw.split(/[._\s-]/)[0] ?? raw;
    return first.charAt(0).toUpperCase() + first.slice(1);
  }, [session.userName]);

  const net = data?.current.net.amount ?? 0;
  const saved = net >= 0;
  const rate = data?.current.savingsRate ?? null;

  return (
    <Screen withTabInset contentStyle={styles.content}>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.page}
        contentInsetAdjustmentBehavior="automatic"
        refreshControl={
          <RefreshControl
            refreshing={loading && data !== null}
            onRefresh={() => setWindow({ ...window })}
          />
        }>
        <FadeIn>
          <View style={styles.greeting}>
            <ThemedText type="title">Hi 👋 {greeting}</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              here are your analytics
            </ThemedText>
          </View>
        </FadeIn>

        <FadeIn delay={60}>
          <WindowControl
            period={period}
            value={window}
            onChange={(next) => setWindow({ key: next.key, from: next.from, to: next.to })}
          />
        </FadeIn>

        {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
        {loading && !data ? <ActivityIndicator style={styles.loader} /> : null}

        {data ? (
          <>
            <FadeIn delay={100}>
              <ThemedView type="backgroundElement" style={styles.hero}>
                <ThemedText type="small" themeColor="textMuted">
                  {saved ? 'You saved' : 'You came up short'}
                </ThemedText>

                <ThemedText type="display" style={{ color: saved ? INCOME_COLOR : EXPENSE_COLOR }}>
                  {formatMoney({ amount: Math.abs(net), currency: data.currency })}
                </ThemedText>

                <ThemedText type="small" themeColor="textSecondary">
                  {rate === null
                    ? 'Nothing came in during this window, so there is no rate to show.'
                    : `${Math.abs(rate)}% of the ${formatMoney(data.current.income)} that came in`}
                </ThemedText>

                <ThemedText type="small" themeColor="textMuted">
                  {formatLongDate(data.from)} – {formatLongDate(data.to)}
                </ThemedText>

                <View style={styles.flowRow}>
                  <Flow label="In" value={formatMoney(data.current.income)} color={INCOME_COLOR} />
                  <Flow label="Out" value={formatMoney(data.current.expenses)} color={EXPENSE_COLOR} />
                  <Flow
                    label={saved ? 'Kept' : 'Short'}
                    value={formatMoney({ amount: Math.abs(net), currency: data.currency })}
                    color={saved ? Brand.green : EXPENSE_COLOR}
                  />
                </View>
              </ThemedView>
            </FadeIn>

            <FadeIn delay={140}>
              <View style={styles.compareRow}>
                <Compare
                  label="Income"
                  value={formatMoney(data.current.income)}
                  change={data.change.incomePercent}
                />
                <Compare
                  label="Expenses"
                  value={formatMoney(data.current.expenses)}
                  change={data.change.expensePercent}
                  invert
                />
                <Compare
                  label="Net"
                  value={formatMoney(data.current.net)}
                  change={data.change.netPercent}
                />
              </View>
            </FadeIn>

            <FadeIn delay={180}>
              <ThemedView type="backgroundElement" style={styles.card}>
                <View style={styles.cardHead}>
                  <ThemedText type="smallBold">How the window went</ThemedText>

                  <View style={[styles.modeSwitch, { borderColor: theme.border }]}>
                    {(['lines', 'bars'] as const).map((option) => {
                      const active = view === option;
                      return (
                        <Pressable
                          key={option}
                          onPress={() => setView(option)}
                          accessibilityRole="tab"
                          accessibilityState={{ selected: active }}
                          accessibilityLabel={option === 'lines' ? 'Line chart' : 'Bar chart'}
                          style={[
                            styles.modeTab,
                            active && { backgroundColor: theme.backgroundSelected },
                          ]}>
                          <ThemedText type="caption" themeColor={active ? 'text' : 'textMuted'}>
                            {option === 'lines' ? 'Lines' : 'Bars'}
                          </ThemedText>
                        </Pressable>
                      );
                    })}
                  </View>
                </View>

                <View style={styles.chips}>
                  {(['income', 'expenses', 'net'] as const).map((metric) => {
                    const active = visible[metric];
                    const color =
                      metric === 'income'
                        ? INCOME_COLOR
                        : metric === 'expenses'
                          ? EXPENSE_COLOR
                          : NET_COLOR;

                    return (
                      <Pressable
                        key={metric}
                        onPress={() =>
                          setVisible((current) => ({ ...current, [metric]: !current[metric] }))
                        }
                        accessibilityRole="switch"
                        accessibilityState={{ checked: active }}
                        accessibilityLabel={metric}
                        style={({ pressed }) => [
                          styles.chip,
                          {
                            borderColor: active ? color : theme.border,
                            backgroundColor: active ? `${color}1A` : 'transparent',
                          },
                          pressed && styles.pressed,
                        ]}>
                        <ThemedText
                          type="caption"
                          style={{ color: active ? color : theme.textMuted }}>
                          {metric === 'income' ? 'Income' : metric === 'expenses' ? 'Expenses' : 'Net'}
                        </ThemedText>
                      </Pressable>
                    );
                  })}

                  {view === 'lines' ? (
                    <Pressable
                      onPress={() => setCompare((value) => !value)}
                      accessibilityRole="switch"
                      accessibilityState={{ checked: compare }}
                      accessibilityLabel="Compare with the previous window"
                      style={({ pressed }) => [
                        styles.chip,
                        {
                          borderColor: compare ? theme.text : theme.border,
                          backgroundColor: compare ? theme.backgroundSelected : 'transparent',
                        },
                        pressed && styles.pressed,
                      ]}>
                      <ThemedText type="caption" themeColor={compare ? 'text' : 'textMuted'}>
                        vs before
                      </ThemedText>
                    </Pressable>
                  ) : null}

                  {view === 'lines' ? (
                    <View style={[styles.modeSwitch, { borderColor: theme.border }]}>
                      {(['cumulative', 'bucket'] as const).map((option) => {
                        const active = mode === option;
                        return (
                          <Pressable
                            key={option}
                            onPress={() => setMode(option)}
                            accessibilityRole="tab"
                            accessibilityState={{ selected: active }}
                            accessibilityLabel={
                              option === 'cumulative' ? 'Cumulative' : 'Per period'
                            }
                            style={[
                              styles.modeTab,
                              active && { backgroundColor: theme.backgroundSelected },
                            ]}>
                            <ThemedText type="caption" themeColor={active ? 'text' : 'textMuted'}>
                              {option === 'cumulative' ? 'Total' : 'Each'}
                            </ThemedText>
                          </Pressable>
                        );
                      })}
                    </View>
                  ) : null}
                </View>

                {compare ? (
                  <View style={styles.compareBar}>
                    <ThemedText type="caption" themeColor="textMuted">
                      compared with
                    </ThemedText>
                    <View style={styles.comparePicker}>
                      <WindowControl
                        period={period}
                        value={compareWindow ?? { key: 'period' }}
                        onChange={(next) =>
                          setCompareWindow({ key: next.key, from: next.from, to: next.to })
                        }
                      />
                    </View>
                  </View>
                ) : null}
                {view === 'bars' ? (
                  <>
                    <GroupedBars
                      labels={barLabels}
                      groups={barGroups}
                      formatValue={short}
                    />
                    <ThemedText type="caption" themeColor="textMuted">
                      {compare
                        ? 'Each segment beside the same segment of the window before - solid is now, outlined is before.'
                        : 'Each segment of the window on its own, so a heavy week stands out.'}
                    </ThemedText>
                  </>
                ) : chartSeries.length > 0 ? (
                  <>
                    <LineChart
                      labels={labels}
                      series={chartSeries}
                      formatValue={short}
                    />
                    <ThemedText type="caption" themeColor="textMuted">
                      {mode === 'cumulative'
                        ? 'Running totals. Where income stays above expenses, the gap is what you kept.'
                        : 'Each segment of the window on its own, so a heavy week stands out.'}
                    </ThemedText>
                  </>
                ) : (
                  <ThemedText type="caption" themeColor="textMuted">
                    Pick a line to draw.
                  </ThemedText>
                )}
              </ThemedView>            </FadeIn>

            {data.bestDay || data.worstDay ? (
              <FadeIn delay={220}>
                <View style={styles.daysRow}>
                  {data.bestDay ? (
                    <DayCard emoji="🌟" label="Best day" day={data.bestDay} color={INCOME_COLOR} />
                  ) : null}
                  {data.worstDay ? (
                    <DayCard emoji="😬" label="Hardest day" day={data.worstDay} color={EXPENSE_COLOR} />
                  ) : null}
                </View>
              </FadeIn>
            ) : null}

            {data.byCategory.length > 0 ? (
              <FadeIn delay={260}>
                <ThemedView type="backgroundElement" style={styles.card}>
                  <ThemedText type="smallBold">Where it went</ThemedText>

                  {data.byCategory.slice(0, 5).map((slice) => (
                    <View key={slice.id} style={styles.slice}>
                      <View style={styles.sliceHead}>
                        <CategoryBadge icon={slice.icon} color={slice.color ?? DEFAULT_COLOR} size={24} />
                        <ThemedText type="small" style={styles.sliceName} numberOfLines={1}>
                          {slice.name}
                        </ThemedText>
                        <ThemedText type="caption" themeColor="textMuted">
                          {slice.share}%
                        </ThemedText>
                        <ThemedText type="smallBold" style={styles.sliceAmount}>
                          {formatMoney(slice.total)}
                        </ThemedText>
                        {slice.changePercent !== null ? (
                          <ThemedText
                            type="caption"
                            style={{
                              color:
                                slice.changePercent > 0 ? EXPENSE_COLOR : INCOME_COLOR,
                            }}>
                            {slice.changePercent > 0 ? '▲' : '▼'}{' '}
                            {Math.abs(slice.changePercent)}%
                          </ThemedText>
                        ) : null}
                      </View>
                      <ProgressBar
                        value={slice.total.amount}
                        max={Math.max(1, data.current.expenses.amount)}
                        height={5}
                      />
                    </View>
                  ))}
                </ThemedView>
              </FadeIn>
            ) : null}

            {data.bySource.length > 0 ? (
              <FadeIn delay={300}>
                <ThemedView type="backgroundElement" style={styles.card}>
                  <ThemedText type="smallBold">Where it came from</ThemedText>

                  {data.bySource.slice(0, 4).map((slice) => (
                    <View key={slice.id} style={styles.slice}>
                      <View style={styles.sliceHead}>
                        <CategoryBadge icon={slice.icon} color={slice.color ?? INCOME_COLOR} size={24} />
                        <ThemedText type="small" style={styles.sliceName} numberOfLines={1}>
                          {slice.name}
                        </ThemedText>
                        <ThemedText type="caption" themeColor="textMuted">
                          {slice.share}%
                        </ThemedText>
                        <ThemedText type="smallBold" style={styles.sliceAmount}>
                          {formatMoney(slice.total)}
                        </ThemedText>
                        {slice.changePercent !== null ? (
                          <ThemedText
                            type="caption"
                            style={{
                              color:
                                slice.changePercent > 0 ? EXPENSE_COLOR : INCOME_COLOR,
                            }}>
                            {slice.changePercent > 0 ? '▲' : '▼'}{' '}
                            {Math.abs(slice.changePercent)}%
                          </ThemedText>
                        ) : null}
                      </View>
                      <ProgressBar
                        value={slice.total.amount}
                        max={Math.max(1, data.current.income.amount)}
                        height={5}
                      />
                    </View>
                  ))}
                </ThemedView>
              </FadeIn>
            ) : null}

            {data.budget ? (
              <FadeIn delay={340}>
                <ThemedView type="backgroundElement" style={styles.card}>
                  <View style={styles.cardHead}>
                    <ThemedText type="smallBold">{data.budget.name ?? 'Your current plan'}</ThemedText>
                    <ThemedText type="caption" themeColor="textMuted">
                      {data.budget.daysLeft >= 0
                        ? `${data.budget.daysLeft} ${data.budget.daysLeft === 1 ? 'day' : 'days'} to go`
                        : 'ended'}
                    </ThemedText>
                  </View>

                  <ThemedText type="small" themeColor="textSecondary">
                    {formatMoney(data.budget.remaining)} left of {formatMoney(data.budget.planned)}
                  </ThemedText>

                  <ProgressBar
                    value={data.budget.spent.amount}
                    max={Math.max(1, data.budget.planned.amount)}
                    height={8}
                  />

                  <Pressable
                    onPress={() => router.push('/budget')}
                    accessibilityRole="button"
                    accessibilityLabel="Open the budget tab">
                    <ThemedText type="caption" style={{ color: Brand.green }}>
                      Open the plan
                    </ThemedText>
                  </Pressable>
                </ThemedView>
              </FadeIn>
            ) : null}

            <FadeIn delay={380}>
              <ThemedView type="backgroundElement" style={styles.card}>
                <ThemedText type="smallBold">Worth knowing</ThemedText>

                {insights(data).map((line) => (
                  <View key={line} style={styles.insight}>
                    <ThemedText type="caption" style={{ color: Brand.green }}>
                      •
                    </ThemedText>
                    <ThemedText type="small" themeColor="textSecondary" style={styles.insightText}>
                      {line}
                    </ThemedText>
                  </View>
                ))}
              </ThemedView>
            </FadeIn>
          </>
        ) : null}
      </ScrollView>
    </Screen>
  );
}

/**
 * The analysis behind the numbers, in sentences.
 *
 * <p>These come from the same payload the charts read, so they cannot drift from what is drawn. The
 * order is deliberate: what happened to the rate, how spending moved, what moved most, the single
 * largest spend, then the small comforts.
 */
function insights(data: AnalyticsSummary): string[] {
  const lines: string[] = [];
  const rate = data.current.savingsRate;
  const before = data.previous.savingsRate;

  if (rate !== null) {
    if (data.current.net.amount >= 0) {
      const delta = before === null ? null : Math.round((rate - before) * 10) / 10;
      lines.push(
        delta === null
          ? `You kept ${rate}% of what came in.`
          : delta === 0
            ? `You kept ${rate}% of what came in - the same rate as the window before.`
            : `You kept ${rate}% of what came in, ${Math.abs(delta)} points ${delta > 0 ? 'better' : 'worse'} than the window before.`,
      );
    } else {
      lines.push(`You spent ${Math.abs(rate)}% more than you received this window.`);
    }
  }

  if (data.change.expensePercent !== null) {
    const moved = data.change.expensePercent;
    lines.push(
      `Spending is ${Math.abs(moved)}% ${moved > 0 ? 'higher' : 'lower'} than the window before (${formatMoney(data.previous.expenses)} then).`,
    );
  }

  if (data.change.incomePercent !== null) {
    const moved = data.change.incomePercent;
    lines.push(
      `Income is ${Math.abs(moved)}% ${moved > 0 ? 'higher' : 'lower'} than the window before.`,
    );
  }

  // The category that moved most, in money rather than percent: a small category doubling is noise.
  const movers = data.byCategory
    .filter((slice) => slice.changePercent !== null)
    .sort(
      (a, b) =>
        Math.abs(b.total.amount - b.previousTotal.amount) -
        Math.abs(a.total.amount - a.previousTotal.amount),
    );
  const mover = movers[0];
  if (mover) {
    const delta = mover.total.amount - mover.previousTotal.amount;
    if (Math.abs(delta) > 0) {
      lines.push(
        `${mover.name} ${delta > 0 ? 'grew' : 'fell'} by ${formatMoney({
          amount: Math.abs(delta),
          currency: mover.total.currency,
        })} - ${formatMoney(mover.previousTotal)} before, ${formatMoney(mover.total)} now.`,
      );
    }
  }

  if (data.biggestExpense) {
    const biggest = data.biggestExpense;
    lines.push(
      `Your largest single expense was ${formatMoney(biggest.amount)} on ${formatLongDate(biggest.date).replace(/\s\d{4}$/, '')}${biggest.categoryName ? ` (${biggest.categoryName})` : ''}.`,
    );
  }

  lines.push(
    `That is ${formatMoney(data.current.averagePerDay)} a day, against ${formatMoney(data.previous.averagePerDay)} in the window before.`,
  );

  if (data.noSpendDays > 0) {
    lines.push(
      `${data.noSpendDays} ${data.noSpendDays === 1 ? 'day' : 'days'} passed without spending anything.`,
    );
  }

  return lines;
}
function Flow({ label, value, color }: { label: string; value: string; color: string }) {
  return (
    <View style={styles.flow}>
      <ThemedText type="caption" themeColor="textMuted">
        {label}
      </ThemedText>
      <ThemedText type="smallBold" style={{ color }} numberOfLines={1}>
        {value}
      </ThemedText>
    </View>
  );
}

function Compare({
  label,
  value,
  change,
  invert = false,
}: {
  label: string;
  value: string;
  change: number | null;
  /** For expenses: more is worse, so the arrows keep their meaning but the colours swap. */
  invert?: boolean;
}) {
  const up = (change ?? 0) > 0;
  const good = invert ? !up : up;
  const color = change === null ? undefined : good ? INCOME_COLOR : EXPENSE_COLOR;

  return (
    <ThemedView type="backgroundElement" style={styles.compare}>
      <ThemedText type="caption" themeColor="textMuted">
        {label}
      </ThemedText>
      <ThemedText type="smallBold" numberOfLines={1}>
        {value}
      </ThemedText>
      <ThemedText type="caption" style={{ color }}>
        {change === null ? 'no baseline' : `${up ? '▲' : '▼'} ${Math.abs(change)}%`}
      </ThemedText>
    </ThemedView>
  );
}

function DayCard({
  emoji,
  label,
  day,
  color,
}: {
  emoji: string;
  label: string;
  day: { date: string; net: { amount: number; currency: string } };
  color: string;
}) {
  return (
    <ThemedView type="backgroundElement" style={styles.dayCard}>
      <ThemedText type="caption" themeColor="textMuted">
        {emoji} {label}
      </ThemedText>
      <ThemedText type="smallBold" numberOfLines={1}>
        {formatLongDate(day.date).replace(/\s\d{4}$/, '')}
      </ThemedText>
      <ThemedText type="small" style={{ color }} numberOfLines={1}>
        {day.net.amount >= 0 ? '+' : '−'}
        {formatMoney({ amount: Math.abs(day.net.amount), currency: day.net.currency })}
      </ThemedText>
    </ThemedView>
  );
}

/** Compact axis labels: 1.5M rather than 1 500 000,00. */
function short(value: number): string {
  const absolute = Math.abs(value);
  if (absolute >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (absolute >= 1_000) return `${Math.round(value / 1_000)}k`;
  return String(Math.round(value));
}

const styles = StyleSheet.create({
  content: { flexGrow: 1, paddingBottom: Spacing.six },
  scroll: { flex: 1 },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.three,
    paddingBottom: Spacing.six,
  },
  greeting: { gap: Spacing.half },
  hero: { borderRadius: Radius.card, padding: Spacing.four, gap: Spacing.two },
  flowRow: { flexDirection: 'row', gap: Spacing.two, marginTop: Spacing.one },
  flow: { flex: 1, gap: Spacing.half },
  compareRow: { flexDirection: 'row', gap: Spacing.two },
  compare: {
    flex: 1,
    gap: Spacing.half,
    borderRadius: Radius.field,
    padding: Spacing.two + Spacing.one,
  },
  card: { borderRadius: Radius.card, padding: Spacing.three, gap: Spacing.two },
  cardHead: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  modeSwitch: { flexDirection: 'row', gap: 3, padding: 3, borderWidth: 1, borderRadius: 999 },
  modeTab: { paddingHorizontal: Spacing.two, paddingVertical: 2, borderRadius: 999 },
  chips: { flexDirection: 'row', gap: Spacing.one, flexWrap: 'wrap' },
  compareBar: { gap: Spacing.one },
  comparePicker: { marginTop: Spacing.half },
  chip: { borderWidth: 1, borderRadius: 999, paddingHorizontal: Spacing.two, paddingVertical: 2 },
  daysRow: { flexDirection: 'row', gap: Spacing.two },
  dayCard: { flex: 1, gap: Spacing.half, borderRadius: Radius.field, padding: Spacing.three },
  slice: { gap: Spacing.one },
  sliceHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  sliceName: { flex: 1, flexShrink: 1, minWidth: 0 },
  sliceAmount: { flexShrink: 0 },
  insight: { flexDirection: 'row', gap: Spacing.two, alignItems: 'flex-start' },
  insightText: { flex: 1 },
  loader: { marginTop: Spacing.five },
  error: { color: Status.danger },
  pressed: { opacity: 0.7 },
});
