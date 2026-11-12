import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { FadeIn } from '@/components/ui/fade-in';
import { Icon } from '@/components/ui/icon';
import { Pagination } from '@/components/ui/pagination';
import { ProgressBar } from '@/components/ui/progress-bar';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { formatLongDate, formatMoney } from '@/features/finances/components/finance-ui';
import {
  type Budget,
  type BudgetAllocation,
  fetchBudget,
  fetchBudgetExpenses,
} from '@/features/finances/lib/budget-api';
import type { Expense } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_CATEGORY_COLOR = '#01916D';
const PAGE_SIZE = 20;

const STATUS_LABEL: Record<Budget['status'], string> = {
  UPCOMING: 'Upcoming',
  ACTIVE: 'Active',
  FIXED: 'Fixed',
  CLOSED: 'Closed',
};

/**
 * One budget, in full.
 *
 * <p>The tab answers "am I inside my plan?"; this screen answers "where did it go?". So it opens
 * with the same headline figure, then the plan against the spending category by category, then the
 * expenses themselves - the rows that add up to the number at the top, paged so a long period can
 * be read from either end.
 */
export function BudgetDetailScreen({
  session,
  budgetId,
}: {
  session: Session;
  budgetId: string;
}) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const [budget, setBudget] = useState<Budget | null>(null);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [expenseTotal, setExpenseTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingExpenses, setLoadingExpenses] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const loadBudget = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setBudget(await fetchBudget(session, budgetId));
    } catch (e) {
      setError(describeError(e));
    } finally {
      setLoading(false);
    }
  }, [session, budgetId]);

  const loadExpenses = useCallback(
    async (target: number) => {
      setLoadingExpenses(true);
      try {
        const result = await fetchBudgetExpenses(session, budgetId, { page: target, size: PAGE_SIZE });
        setExpenses(result.data);
        setTotalPages(result.totalPages);
        setExpenseTotal(result.totalElements);
      } catch (e) {
        if (e instanceof ApiRequestError && e.status === 401) {
          router.replace('/auth/sign-in');
          return;
        }
        setError(describeError(e));
      } finally {
        setLoadingExpenses(false);
      }
    },
    [session, budgetId],
  );

  useEffect(() => {
    loadBudget();
  }, [loadBudget]);

  useEffect(() => {
    loadExpenses(page);
  }, [loadExpenses, page]);

  const net = budget?.net ?? 0;
  const met = budget?.verdict === 'SUCCESS';
  const exceeded = budget?.verdict === 'EXCEEDED';
  const verdictColour = exceeded ? Status.danger : met ? Brand.green : theme.textSecondary;

  return (
    <View style={[styles.screen, { backgroundColor: theme.background }]}>
      <View style={[styles.header, { paddingTop: insets.top + Spacing.two }]}>
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Back to budgets"
          hitSlop={10}
          style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}>
          <Icon name="chevronLeft" size={22} color={theme.textSecondary} />
        </Pressable>

        <ThemedText type="title">Budget</ThemedText>
      </View>

      <ScrollView style={styles.scroll} contentContainerStyle={styles.content}>
        <View style={styles.page}>
          {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          {loading && !budget ? <ActivityIndicator style={styles.loader} /> : null}

          {budget ? (
            <>
              {/* The headline: the same figure the tab shows, so arriving here changes the detail
                  rather than the subject. */}
              <FadeIn>
                <ThemedView type="backgroundElement" style={styles.hero}>
                  <View style={styles.heroHead}>
                    <Icon name="clock" size={16} color={theme.textSecondary} />
                    <ThemedText type="small" themeColor="textSecondary" style={styles.heroHeadText}>
                      {formatLongDate(budget.startDate)} – {formatLongDate(budget.endDate)} · {budget.lengthInDays} days
                    </ThemedText>

                    <View style={[styles.pill, { borderColor: verdictColour }]}>
                      <ThemedText type="caption" style={{ color: verdictColour }}>
                        {budget.verdict === 'SUCCESS'
                          ? 'Met'
                          : budget.verdict === 'EXCEEDED'
                            ? 'Exceeded'
                            : STATUS_LABEL[budget.status]}
                      </ThemedText>
                    </View>
                  </View>

                  <ThemedText type="small" themeColor="textMuted">
                    {net >= 0 ? 'Left to spend' : 'Over your plan by'}
                  </ThemedText>
                  <ThemedText type="display" style={{ color: net >= 0 ? Brand.green : Status.danger }}>
                    {formatMoney({ amount: Math.abs(net), currency: session.baseCurrency })}
                  </ThemedText>

                  <ProgressBar value={budget.spent} max={budget.overallAmount} height={12} />

                  <View style={styles.heroFooter}>
                    <ThemedText type="small" themeColor="textSecondary">
                      {formatMoney({ amount: budget.spent, currency: session.baseCurrency })} spent
                    </ThemedText>
                    <ThemedText type="small" themeColor="textMuted">
                      of {formatMoney({ amount: budget.overallAmount, currency: session.baseCurrency })}
                    </ThemedText>
                  </View>


                </ThemedView>
              </FadeIn>

              {/* The facts that are not a number: how the plan was made, and whether it can move. */}
              <FadeIn delay={60}>
                <ThemedView type="backgroundElement" style={styles.facts}>
                  <Fact label="Planned" value={formatMoney({ amount: budget.overallAmount, currency: session.baseCurrency })} />
                  <Fact label="Spent" value={formatMoney({ amount: budget.spent, currency: session.baseCurrency })} />
                  <Fact
                    label="Net"
                    value={`${net >= 0 ? '+' : '−'}${formatMoney({ amount: Math.abs(net), currency: session.baseCurrency })}`}
                    colour={net >= 0 ? Brand.green : Status.danger}
                  />
                  <Fact
                    label="Edits"
                    value={
                      budget.status === 'CLOSED'
                        ? 'Closed when the period ended'
                        : budget.editable
                          ? `${budget.editsUsed} of 3 used`
                          : budget.editsRemaining === 0
                            ? 'All 3 used · fixed'
                            : 'Fixed · the 6% window closed'
                    }
                  />
                  {budget.allocations.length > 0 ? (
                    <Fact
                      label="Allocated"
                      value={`${formatMoney({ amount: budget.totalAllocated, currency: session.baseCurrency })} of ${formatMoney({ amount: budget.overallAmount, currency: session.baseCurrency })}`}
                    />
                  ) : null}
                </ThemedView>
              </FadeIn>

              {/* Where the plan went. */}
              {budget.allocations.length > 0 ? (
                <View style={styles.section}>
                  <ThemedText type="overline" themeColor="textMuted">
                    Categories
                  </ThemedText>

                  {budget.allocations.map((allocation, index) => (
                    <AllocationCard
                      key={allocation.id}
                      allocation={allocation}
                      currency={session.baseCurrency}
                      delay={index * 50}
                    />
                  ))}
                </View>
              ) : null}

              {/* The rows behind the number. */}
              <View style={styles.section}>
                <ThemedText type="overline" themeColor="textMuted">
                  Expenses · {expenseTotal}
                </ThemedText>

                {loadingExpenses && expenses.length === 0 ? <ActivityIndicator /> : null}

                {!loadingExpenses && expenses.length === 0 ? (
                  <ThemedView type="backgroundElement" style={styles.emptyCard}>
                    <ThemedText type="small" themeColor="textSecondary">
                      Nothing was recorded in this period.
                    </ThemedText>
                  </ThemedView>
                ) : null}

                {expenses.map((expense, index) => (
                  <FadeIn key={expense.id} delay={index * 20}>
                    <ThemedView type="backgroundElement" style={styles.expenseRow}>
                      <CategoryBadge
                        icon={expense.category.icon}
                        color={expense.category.color ?? DEFAULT_CATEGORY_COLOR}
                        size={30}
                      />

                      <View style={styles.expenseCopy}>
                        <ThemedText type="small" numberOfLines={1}>
                          {expense.description?.trim() || expense.category.name}
                        </ThemedText>
                        <ThemedText type="caption" themeColor="textMuted">
                          {formatLongDate(expense.date)} · {expense.category.name}
                        </ThemedText>
                      </View>

                      <ThemedText type="smallBold">{formatMoney(expense.amount)}</ThemedText>
                    </ThemedView>
                  </FadeIn>
                ))}

                {totalPages > 1 ? (
                  <Pagination
                    page={page}
                    totalPages={totalPages}
                    onChange={setPage}
                    disabled={loadingExpenses}
                  />
                ) : null}
              </View>
            </>
          ) : null}
        </View>
      </ScrollView>
    </View>
  );
}

/** One labelled figure in the facts card. */
function Fact({ label, value, colour }: { label: string; value: string; colour?: string }) {
  return (
    <View style={styles.factRow}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      <ThemedText type="small" style={colour ? { color: colour } : undefined}>
        {value}
      </ThemedText>
    </View>
  );
}

/** One category: what was planned, what was spent, and the bar between them. */
function AllocationCard({
  allocation,
  currency,
  delay,
}: {
  allocation: BudgetAllocation;
  currency: string;
  delay: number;
}) {
  const over = allocation.remaining < 0;

  return (
    <FadeIn delay={delay}>
      <ThemedView type="backgroundElement" style={styles.allocationCard}>
        <View style={styles.allocationHead}>
          <CategoryBadge
            icon={allocation.category.icon}
            color={allocation.category.color ?? DEFAULT_CATEGORY_COLOR}
            size={28}
          />
          <ThemedText type="small" style={styles.allocationName} numberOfLines={1}>
            {allocation.category.name}
          </ThemedText>
          <ThemedText type="smallBold" style={over ? { color: Status.danger } : undefined}>
            {allocation.remaining >= 0 ? '' : '−'}
            {formatMoney({ amount: Math.abs(allocation.remaining), currency })}
          </ThemedText>
        </View>

        <ProgressBar
          value={allocation.spent}
          max={allocation.allocatedAmount}
          height={6}
          delay={delay}
          exceeded={over}
        />

        <ThemedText type="caption" themeColor="textMuted">
          {formatMoney({ amount: allocation.spent, currency })} of{' '}
          {formatMoney({ amount: allocation.allocatedAmount, currency })}
        </ThemedText>
      </ThemedView>
    </FadeIn>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  backButton: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -Spacing.two,
  },
  scroll: {
    flex: 1,
  },
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
  loader: {
    marginTop: Spacing.five,
  },
  hero: {
    borderRadius: Radius.card,
    padding: Spacing.four,
    gap: Spacing.two,
  },
  heroHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  heroHeadText: {
    flex: 1,
  },
  heroFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  pill: {
    borderWidth: 1,
    borderRadius: Radius.pill,
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
  },
  warning: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    padding: Spacing.two,
  },
  warningText: {
    flex: 1,
  },
  facts: {
    borderRadius: Radius.card,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  factRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  section: {
    gap: Spacing.two,
  },
  allocationCard: {
    borderRadius: Radius.field,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  allocationHead: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  allocationName: {
    flex: 1,
  },
  emptyCard: {
    borderRadius: Radius.field,
    padding: Spacing.three,
  },
  expenseRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  expenseCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  error: {
    color: Status.danger,
  },
  pressed: {
    opacity: 0.7,
  },
});
