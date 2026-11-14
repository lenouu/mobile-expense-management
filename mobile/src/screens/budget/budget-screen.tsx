import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Alert, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { FadeIn } from '@/components/ui/fade-in';
import { Icon } from '@/components/ui/icon';
import { ProgressBar } from '@/components/ui/progress-bar';
import { Button } from '@/components/ui/primitives';
import type { ToastKind } from '@/components/ui/top-toast';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { BudgetFormPopup } from '@/features/finances/components/budget-form-popup';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { formatLongDate, formatMoney } from '@/features/finances/components/finance-ui';
import {
  type Budget,
  type BudgetAllocation,
  type BudgetSummary,
  deleteBudget,
  fetchBudget,
  fetchBudgets,
} from '@/features/finances/lib/budget-api';
import { useDataVersion } from '@/features/finances/lib/data-events';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_CATEGORY_COLOR = '#01916D';

/** The day after an ISO date, so a new plan can begin where the last one ended. */
function dayAfter(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  const date = new Date(year, month - 1, day + 1);
  const padded = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${padded(date.getMonth() + 1)}-${padded(date.getDate())}`;
}

function netColour(net: number): string {
  return net >= 0 ? Brand.green : Status.danger;
}

/** How a budget presents itself when it has no name. */
function titleOf(budget: { name: string | null; startDate: string; endDate: string }): string {
  return budget.name ?? `${formatLongDate(budget.startDate)} – ${formatLongDate(budget.endDate)}`;
}

/**
 * The Budget tab.
 *
 * <p>Built around one question - "am I inside my plan?" - so the answer is the largest thing on the
 * screen: what is left, green when there is some and red when there is not. A budget is a window the
 * user chose, so the window is stated rather than implied, and its spending is the sum of the
 * expenses the user assigned to it.
 *
 * <p>Running and upcoming plans can be changed or deleted; finished ones are history, listed with
 * the verdict the server closed them with.
 */
export function BudgetScreen({
  session,
  onToast,
  onSignOut,
}: {
  session: Session;
  onToast: (kind: ToastKind, message: string) => void;
  onSignOut: (message?: string) => void;
}) {
  const theme = useTheme();

  const [budgets, setBudgets] = useState<BudgetSummary[]>([]);
  const [current, setCurrent] = useState<Budget | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [rulesOpen, setRulesOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);
  /** Re-reads the plan when an expense lands somewhere else (e.g. the receipt preview). */
  const dataVersion = useDataVersion();

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

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const list = await fetchBudgets(session);
      setBudgets(list);

      // The plan that still matters: running first, then one waiting to start.
      const live =
        list.find((entry) => entry.status === 'ACTIVE' || entry.status === 'FIXED') ??
        list.find((entry) => entry.status === 'UPCOMING');

      setCurrent(live ? await fetchBudget(session, live.id) : null);
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, handleError, dataVersion]);

  useEffect(() => {
    load();
  }, [load]);

  const remove = () => {
    if (!current) return;

    const count = current.expenseCount;
    // Deleting the plan does not delete the money: the expenses stay in the ledger, unlinked.
    const expensesLine =
      count === 0
        ? 'It has no expenses under it yet.'
        : `The ${count} ${count === 1 ? 'expense' : 'expenses'} recorded under it stay in your ledger — they just stop belonging to a budget.`;

    Alert.alert('Delete this budget?', `${titleOf(current)}\n\n${expensesLine} The budget itself cannot be undone.`, [
      { text: 'Keep it', style: 'cancel' },
      {
        text: 'Delete budget',
        style: 'destructive',
        onPress: async () => {
          setDeleting(true);
          try {
            await deleteBudget(session, current.id);
            onToast(
              'deleted',
              count === 0
                ? 'The budget has been deleted.'
                : `The budget has been deleted. Its ${count} ${count === 1 ? 'expense was' : 'expenses were'} kept in your ledger.`,
            );
            setCurrent(null);
            load();
          } catch (e) {
            handleError(e);
          } finally {
            setDeleting(false);
          }
        },
      },
    ]);
  };

  const past = budgets.filter((entry) => entry.id !== current?.id);
  const net = current?.net ?? 0;
  const spent = current?.spent ?? 0;
  const planned = current?.overallAmount ?? 0;

  return (
    <View style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        contentInsetAdjustmentBehavior="automatic"
        refreshControl={<RefreshControl refreshing={loading && budgets.length > 0} onRefresh={load} />}>
        <View style={styles.page}>
          {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          {loading && budgets.length === 0 ? <ActivityIndicator style={styles.loader} /> : null}

          {/* ---------------------------------------------------------------- the plan in force */}
          {!loading && !current ? (
            <FadeIn>
              <ThemedView type="backgroundElement" style={styles.empty}>
                <View style={styles.emptyIcon}>
                  <Icon name="wallet" size={26} color={Brand.green} />
                </View>

                <ThemedText type="title">No budget right now</ThemedText>
                <ThemedText type="small" themeColor="textSecondary" style={styles.centerText}>
                  Pick a window and an amount to stay under. Nothing is blocked if you go past it -
                  the app just tells you where you stand.
                </ThemedText>

                <Pressable
                  onPress={() => setFormOpen(true)}
                  accessibilityRole="button"
                  accessibilityLabel="Create a budget"
                  style={({ pressed }) => [styles.primaryButton, pressed && styles.pressed]}>
                  <ThemedText type="smallBold" style={styles.primaryLabel}>
                    Create a budget
                  </ThemedText>
                </Pressable>
              </ThemedView>
            </FadeIn>
          ) : null}

          {current ? (
            <FadeIn>
              <Pressable
                onPress={() => router.push(`/budgets/${current.id}`)}
                accessibilityRole="button"
                accessibilityLabel="Open this budget in full"
                style={({ pressed }) => [pressed && styles.pressed]}>
                <ThemedView type="backgroundElement" style={styles.hero}>
                  <View style={styles.heroHead}>
                    <Icon name="wallet" size={16} color={theme.textSecondary} />
                    <ThemedText type="small" themeColor="textSecondary" style={styles.heroHeadText} numberOfLines={1}>
                      {titleOf(current)}
                    </ThemedText>
                    <StatusPill status={current.status} verdict={current.verdict} />
                  </View>

                  <ThemedText type="caption" themeColor="textMuted">
                    {formatLongDate(current.startDate)} – {formatLongDate(current.endDate)} ·{' '}
                    {current.lengthInDays} days
                  </ThemedText>

                  <ThemedText type="small" themeColor="textMuted">
                    {net >= 0 ? 'Left to spend' : 'Over your plan by'}
                  </ThemedText>
                  <ThemedText type="display" style={{ color: netColour(net) }}>
                    {formatMoney({ amount: Math.abs(net), currency: session.baseCurrency })}
                  </ThemedText>

                  <ProgressBar value={spent} max={planned} height={12} />

                  <View style={styles.heroFooter}>
                    <ThemedText type="small" themeColor="textSecondary">
                      {formatMoney({ amount: spent, currency: session.baseCurrency })} spent
                    </ThemedText>
                    <ThemedText type="small" themeColor="textMuted">
                      of {formatMoney({ amount: planned, currency: session.baseCurrency })}
                    </ThemedText>
                  </View>

                  <ThemedText type="small" themeColor="textMuted">
                    {current.status === 'UPCOMING'
                      ? `Starts ${formatLongDate(current.startDate)} · editable until then`
                      : current.editable
                        ? `Editable for now · ${current.editsRemaining} of 3 edits left`
                        : current.status === 'CLOSED'
                          ? 'Closed when the window ended'
                          : 'Fixed for this window'}
                  </ThemedText>
                </ThemedView>
              </Pressable>
            </FadeIn>
          ) : null}

          {current ? (
            <View style={styles.actions}>
              {current.editable ? (
                <Button label="Edit" onPress={() => setFormOpen(true)} secondary onCard style={styles.action} />
              ) : null}

              <Button
                label={deleting ? 'Deleting…' : 'Delete'}
                onPress={remove}
                disabled={deleting}
                secondary
                onCard
                style={styles.action}
              />
            </View>
          ) : null}

          {/* ---------------------------------------------------------------- the split */}
          {current && current.allocations.length > 0 ? (
            <View style={styles.section}>
              <ThemedText type="overline" themeColor="textMuted">
                Where it goes
              </ThemedText>

              {current.allocations.map((allocation, index) => (
                <AllocationRow key={allocation.id} allocation={allocation} delay={index * 60} />
              ))}
            </View>
          ) : null}

          {/* ---------------------------------------------------------------- history */}
          {past.length > 0 ? (
            <View style={styles.section}>
              <ThemedText type="overline" themeColor="textMuted">
                Previous budgets
              </ThemedText>

              {past.map((entry, index) => (
                <FadeIn key={entry.id} delay={index * 60}>
                  <Pressable
                    onPress={() => router.push(`/budgets/${entry.id}`)}
                    accessibilityRole="button"
                    accessibilityLabel={`Open ${titleOf(entry)}`}
                    style={({ pressed }) => [pressed && styles.pressed]}>
                    <ThemedView type="backgroundElement" style={styles.historyRow}>
                      <View style={styles.historyCopy}>
                        <ThemedText type="small" numberOfLines={1}>
                          {titleOf(entry)}
                        </ThemedText>
                        <ThemedText type="caption" themeColor="textMuted">
                          {formatMoney({ amount: entry.spent, currency: session.baseCurrency })} of{' '}
                          {formatMoney({ amount: entry.overallAmount, currency: session.baseCurrency })}
                        </ThemedText>
                      </View>

                      <ThemedText type="smallBold" style={{ color: netColour(entry.net) }}>
                        {entry.net >= 0 ? '+' : '−'}
                        {formatMoney({ amount: Math.abs(entry.net), currency: session.baseCurrency })}
                      </ThemedText>

                      <StatusPill status={entry.status} verdict={entry.verdict} />
                      <Icon name="chevronRight" size={16} color={theme.textMuted} />
                    </ThemedView>
                  </Pressable>
                </FadeIn>
              ))}
            </View>
          ) : null}

          {/* A way back to a plan once the current one is history. */}
          {!loading && current && !current.editable ? (
            <Button label="Start a new budget" primary onPress={() => setFormOpen(true)} style={styles.newPlan} />
          ) : null}
        </View>
      </ScrollView>

      {/* A plan is always one tap away, and it never overlaps the one in force. */}
      <Pressable
        onPress={() => setFormOpen(true)}
        accessibilityRole="button"
        accessibilityLabel="Create a budget"
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}>
        <Icon name="add" size={28} color="#FFFFFF" />
      </Pressable>

      <BudgetFormPopup
        visible={formOpen}
        onClose={() => setFormOpen(false)}
        session={session}
        budget={current && current.editable ? current : null}
        defaultStart={current && !current.editable ? dayAfter(current.endDate) : undefined}
        onSaved={(message) => {
          const created = current === null || !current.editable;
          setFormOpen(false);
          onToast(created ? 'created' : 'edited', message);
          if (created) setRulesOpen(true);
          load();
        }}
        onAuthError={() => onSignOut('Your session has expired. Please sign in again.')}
      />

      <RulesPopup visible={rulesOpen} onClose={() => setRulesOpen(false)} />
    </View>
  );
}

/**
 * The editing rule, stated once, at the moment it starts to apply.
 *
 * <p>A rule that expires in a few days is worth interrupting for once: the alternative is a user
 * discovering it when an edit is refused.
 */
function RulesPopup({ visible, onClose }: { visible: boolean; onClose: () => void }) {
  const theme = useTheme();

  if (!visible) return null;

  return (
    <View style={styles.rulesOverlay}>
      <ThemedView type="backgroundElement" style={[styles.rulesCard, { borderColor: theme.border }]}>
        <ThemedText type="hero">Your budget is set</ThemedText>

        <ThemedText type="small" themeColor="textSecondary">
          You can still change it before it starts, and then only in the first 6% of its window.
          After three edits it is fixed for the rest of the budget.
        </ThemedText>

        <ThemedText type="small" themeColor="textSecondary">
          Expenses count against it only when you put them there, and going over is allowed - it
          shows as a negative balance.
        </ThemedText>

        <Button label="I understand" primary onPress={onClose} style={styles.rulesButton} />
      </ThemedView>
    </View>
  );
}

/** The state of a budget, as a pill. */
function StatusPill({ status, verdict }: { status: Budget['status']; verdict: Budget['verdict'] }) {
  const met = verdict === 'SUCCESS';
  const exceeded = verdict === 'EXCEEDED';

  const label =
    status === 'CLOSED'
      ? met
        ? 'Met'
        : exceeded
          ? 'Exceeded'
          : 'Closed'
      : status === 'UPCOMING'
        ? 'Upcoming'
        : status === 'FIXED'
          ? 'Fixed'
          : 'Active';

  const colour = exceeded ? Status.danger : met || status === 'ACTIVE' ? Brand.green : '#8A94A6';

  return (
    <View style={[styles.pill, { borderColor: colour }]}>
      <ThemedText type="caption" style={{ color: colour }}>
        {label}
      </ThemedText>
    </View>
  );
}

/** One category against its allocation. */
function AllocationRow({ allocation, delay }: { allocation: BudgetAllocation; delay: number }) {
  const over = allocation.remaining < 0;

  return (
    <FadeIn delay={delay}>
      <ThemedView type="backgroundElement" style={styles.allocationRow}>
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
            {formatMoney({ amount: Math.abs(allocation.remaining), currency: 'XAF' })}
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
          {formatMoney({ amount: allocation.spent, currency: 'XAF' })} of{' '}
          {formatMoney({ amount: allocation.allocatedAmount, currency: 'XAF' })}
        </ThemedText>
      </ThemedView>
    </FadeIn>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  scroll: { flex: 1 },
  /** Leaves room for the FAB, which floats over the last row otherwise. */
  content: { flexGrow: 1, paddingBottom: Spacing.six + 56 },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  loader: { marginTop: Spacing.five },
  hero: { borderRadius: Radius.card, padding: Spacing.four, gap: Spacing.two },
  heroHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.one },
  heroHeadText: { flex: 1 },
  heroFooter: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  actions: { flexDirection: 'row', gap: Spacing.two },
  action: { flex: 1 },
  newPlan: { marginTop: Spacing.two },
  empty: {
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: Radius.card,
    padding: Spacing.five,
    marginTop: Spacing.four,
  },
  emptyIcon: {
    width: 56,
    height: 56,
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(1, 145, 109, 0.12)',
    marginBottom: Spacing.one,
  },
  centerText: { textAlign: 'center' },
  primaryButton: {
    alignSelf: 'stretch',
    alignItems: 'center',
    backgroundColor: Brand.green,
    borderRadius: Radius.field,
    paddingVertical: Spacing.three,
    marginTop: Spacing.two,
  },
  primaryLabel: { color: '#FFFFFF' },
  pill: {
    borderWidth: 1,
    borderRadius: Radius.pill,
    paddingHorizontal: Spacing.two,
    paddingVertical: 2,
  },
  section: { gap: Spacing.two },
  allocationRow: { borderRadius: Radius.field, padding: Spacing.three, gap: Spacing.two },
  allocationHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  allocationName: { flex: 1 },
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  historyCopy: { flex: 1, gap: Spacing.half },
  rulesOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(2, 40, 76, 0.45)',
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
  },
  rulesCard: {
    width: '100%',
    maxWidth: 420,
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.five,
    gap: Spacing.three,
  },
  rulesButton: { marginTop: Spacing.two },
  fab: {
    position: 'absolute',
    right: Spacing.four,
    bottom: Spacing.four,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Brand.green,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabPressed: { opacity: 0.85, transform: [{ scale: 0.94 }] },
  error: { color: Status.danger },
  pressed: { opacity: 0.7 },
});
