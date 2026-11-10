import { router } from 'expo-router';
import { ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Button, Chip } from '@/components/ui/primitives';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import {
  CurrencyPicker,
  DeleteButton,
  Field,
  financeStyles as styles,
  formatDate,
  formatMoney,
  moneyFieldError,
  TextField,
  todayIso,
  toMoney,
  validateAmountAndDate,
} from '@/features/finances/components/finance-ui';
import {
  createExpense,
  deleteExpense,
  type Expense,
  type ExpenseFilters,
  type ExpenseTotal,
  fetchCurrentPeriod,
  fetchExpenseTotal,
  fetchExpenses,
  fetchMyCategories,
  type FinancialPeriod,
  type MyCategory,
  type SaveExpense,
  updateExpense,
} from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { DEFAULT_CURRENCY } from '@/types/api';
import { describeError } from '@/utils/errors';

const PAGE_SIZE = 20;

/** null: form closed. 'new': adding. A string: editing that expense. */
type Editing = null | 'new' | string;

/**
 * How the window is chosen (section 6.2 of the vision: reporting ranges are independent of the
 * financial period, so the period is one preset among several rather than a constraint).
 */
type WindowKey = 'period' | 'week' | 'month' | 'custom';

const WINDOWS: { key: WindowKey; label: string }[] = [
  { key: 'period', label: 'This period' },
  { key: 'week', label: 'Last 7 days' },
  { key: 'month', label: 'This month' },
  { key: 'custom', label: 'Custom' },
];

function iso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return iso(date);
}

function startOfMonth(): string {
  const now = new Date();
  return iso(new Date(now.getFullYear(), now.getMonth(), 1));
}

/**
 * S2-TECH-2: the signed-in user's expenses - record, list, filter, edit, delete.
 *
 * The window is a *reporting* range, never a requirement: the user can look at their period, the
 * last week, this month, or any two dates. The total above the list asks the backend for the same
 * query that produced the list, so the two can never disagree.
 */
export function ExpensesManager({
  session,
  contentStyle,
  onSignOut,
  header,
}: {
  session: Session;
  contentStyle?: object;
  /** Called when the session turns out to be expired. */
  onSignOut?: (message?: string) => void;
  /** Screen chrome drawn above the filters (the app header). */
  header?: ReactNode;
}) {
  const theme = useTheme();
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [period, setPeriod] = useState<FinancialPeriod | null>(null);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editing, setEditing] = useState<Editing>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  // The window, the category and the search text - all optional, all combinable.
  const [windowKey, setWindowKey] = useState<WindowKey>('period');
  const [customFrom, setCustomFrom] = useState(daysAgo(29));
  const [customTo, setCustomTo] = useState(todayIso());
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const [q, setQ] = useState('');

  // The total for whatever the filters currently describe.
  const [windowTotal, setWindowTotal] = useState<ExpenseTotal | null>(null);
  const [totalLoading, setTotalLoading] = useState(true);
  const [totalError, setTotalError] = useState<string | null>(null);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        // Tokens last 60 minutes and there is no refresh token: sign in again.
        if (onSignOut) onSignOut('Your session has expired. Please sign in again.');
        else router.replace('/auth/sign-in');
        return;
      }
      setMessage(describeError(e));
    },
    [onSignOut],
  );

  // The current period dates are only needed for the "This period" preset's bounds and label.
  useEffect(() => {
    let cancelled = false;
    fetchCurrentPeriod(session)
      .then((current) => {
        if (!cancelled) setPeriod(current);
      })
      .catch(() => {
        // The preset simply falls back to "no dates" and the backend uses the current period.
      });
    return () => {
      cancelled = true;
    };
  }, [session]);

  const filters: ExpenseFilters = useMemo(() => {
    const next: ExpenseFilters = {};
    if (categoryId) next.categoryId = categoryId;
    if (q) next.q = q;

    if (windowKey === 'period' && period) {
      next.from = period.startDate;
      next.to = period.endDate;
    } else if (windowKey === 'week') {
      next.from = daysAgo(6);
      next.to = todayIso();
    } else if (windowKey === 'month') {
      next.from = startOfMonth();
      next.to = todayIso();
    } else if (windowKey === 'custom') {
      if (customFrom) next.from = customFrom;
      if (customTo) next.to = customTo;
    }

    return next;
  }, [categoryId, q, windowKey, period, customFrom, customTo]);

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const [list, mine] = await Promise.all([
        fetchExpenses(session, { ...filters, page: 0, size: PAGE_SIZE }),
        // One large page: the form needs every category to offer as a chip, not the first twenty.
        fetchMyCategories(session, { size: 200 }),
      ]);
      setExpenses(list.data);
      setPage(0);
      setTotalPages(list.totalPages);
      setCategories(mine.data);
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, handleError, filters]);

  const loadTotal = useCallback(async () => {
    setTotalLoading(true);
    setTotalError(null);
    try {
      setWindowTotal(await fetchExpenseTotal(session, filters));
    } catch (e) {
      setTotalError(describeError(e));
      if (e instanceof ApiRequestError && e.status === 401) handleError(e);
    } finally {
      setTotalLoading(false);
    }
  }, [session, filters, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    loadTotal();
  }, [loadTotal]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const next = await fetchExpenses(session, { ...filters, page: page + 1, size: PAGE_SIZE });
      setExpenses((current) => [...current, ...next.data]);
      setPage(next.page);
      setTotalPages(next.totalPages);
    } catch (e) {
      handleError(e);
    } finally {
      setLoadingMore(false);
    }
  };

  const refresh = () => {
    load();
    loadTotal();
  };

  const onSaved = (created: boolean) => {
    setEditing(null);
    setNotice(created ? 'Expense added.' : 'Expense updated.');
    refresh();
  };

  const remove = async (expense: Expense) => {
    setDeletingId(expense.id);
    setMessage(null);
    setNotice(null);
    try {
      await deleteExpense(session, expense.id);
      setNotice(`Expense of ${formatMoney(expense.amount)} deleted.`);
      refresh();
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 404) {
        // Already gone (deleted elsewhere, or a double tap): the goal is reached.
        setNotice('This expense was already deleted.');
        refresh();
      } else {
        handleError(e);
      }
    } finally {
      setDeletingId(null);
    }
  };

  const startEditing = (value: Editing) => {
    setNotice(null);
    setEditing(value);
  };

  // Grouped by day, so the list reads like a statement instead of a flat stream.
  const groups = useMemo(() => {
    const byDate = new Map<string, Expense[]>();
    for (const expense of expenses) {
      const list = byDate.get(expense.date);
      if (list) list.push(expense);
      else byDate.set(expense.date, [expense]);
    }
    return [...byDate.entries()].map(([date, items]) => ({ date, items }));
  }, [expenses]);

  const windowLabel = useMemo(() => {
    if (windowKey === 'period') {
      return period ? `${formatDate(period.startDate)} – ${formatDate(period.endDate)}` : 'This period';
    }
    if (windowKey === 'week') return 'Last 7 days';
    if (windowKey === 'month') return 'This month';
    return customFrom && customTo ? `${formatDate(customFrom)} – ${formatDate(customTo)}` : 'Custom range';
  }, [windowKey, period, customFrom, customTo]);

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[local.content, contentStyle]}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={loading && expenses.length > 0} onRefresh={refresh} />}>
      {header}

      <View style={local.page}>

        <ThemedView type="backgroundElement" style={styles.card}>
          <View style={styles.row}>
            {WINDOWS.map((w) => (
              <Chip
                key={w.key}
                label={w.label}
                selected={windowKey === w.key}
                onPress={() => setWindowKey(w.key)}
                onCard
              />
            ))}
          </View>

          {windowKey === 'custom' && (
            <View style={styles.row}>
              <View style={local.grow}>
                <Field label="From">
                  <TextField
                    placeholder="YYYY-MM-DD"
                    autoCorrect={false}
                    maxLength={10}
                    value={customFrom}
                    onChangeText={setCustomFrom}
                  />
                </Field>
              </View>
              <View style={local.grow}>
                <Field label="To">
                  <TextField
                    placeholder="YYYY-MM-DD"
                    autoCorrect={false}
                    maxLength={10}
                    value={customTo}
                    onChangeText={setCustomTo}
                  />
                </Field>
              </View>
            </View>
          )}

          <Field label="Category">
            <View style={styles.row}>
              <Chip label="All" selected={categoryId === null} onPress={() => setCategoryId(null)} onCard />
              {categories.map((c) => (
                <Chip
                  key={c.id}
                  label={c.name}
                  selected={categoryId === c.id}
                  onPress={() => setCategoryId(c.id)}
                  onCard
                />
              ))}
            </View>
          </Field>

          <Field label="Search descriptions">
            <View style={styles.row}>
              <View style={local.grow}>
                <TextField
                  placeholder="e.g. lunch"
                  autoCorrect={false}
                  value={searchInput}
                  onChangeText={setSearchInput}
                  onSubmitEditing={() => setQ(searchInput.trim())}
                />
              </View>
              <Button label="Search" onPress={() => setQ(searchInput.trim())} secondary onCard />
              {q ? <Button label="Clear" onPress={() => { setSearchInput(''); setQ(''); }} secondary onCard /> : null}
            </View>
          </Field>
        </ThemedView>

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="small" themeColor="textSecondary">
            {windowLabel}
          </ThemedText>

          {totalLoading && !windowTotal ? (
            <ActivityIndicator />
          ) : totalError && !windowTotal ? (
            <ThemedText type="small" style={styles.errorText}>
              {totalError}
            </ThemedText>
          ) : windowTotal ? (
            <>
              <ThemedText type="subtitle">{formatMoney(windowTotal.total)}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {windowTotal.count} {windowTotal.count === 1 ? 'expense' : 'expenses'}
              </ThemedText>
            </>
          ) : null}
        </ThemedView>

        {message && <ThemedText style={styles.errorText}>{message}</ThemedText>}
        {notice && <ThemedText style={styles.noticeText}>{notice}</ThemedText>}

        {editing === 'new' ? (
          <ExpenseForm
            session={session}
            categories={categories}
            onSaved={() => onSaved(true)}
            onCancel={() => setEditing(null)}
            onAuthError={handleError}
          />
        ) : (
          <Button
            label="Add expense"
            onPress={() => startEditing('new')}
            disabled={categories.length === 0}
          />
        )}

        {categories.length === 0 && !loading && (
          <ThemedText type="caption" themeColor="textMuted">
            Add a category first - every expense is filed under one.
          </ThemedText>
        )}

        {loading && expenses.length === 0 && <ActivityIndicator style={styles.loader} />}

        {!loading && expenses.length === 0 && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText themeColor="textSecondary">
              Nothing recorded in this window. Tap “Add expense” to record one.
            </ThemedText>
          </ThemedView>
        )}

        {groups.map((group) => (
          <View key={group.date} style={local.dayGroup}>
            <ThemedText type="smallBold" themeColor="textSecondary">
              {formatDate(group.date)}
            </ThemedText>

            {group.items.map((expense) =>
              editing === expense.id ? (
                <ExpenseForm
                  key={expense.id}
                  session={session}
                  categories={categories}
                  expense={expense}
                  onSaved={() => onSaved(false)}
                  onCancel={() => setEditing(null)}
                  onAuthError={handleError}
                />
              ) : (
                <ThemedView key={expense.id} type="backgroundElement" style={styles.card}>
                  <View style={styles.headerRow}>
                    <ThemedText type="smallBold" style={styles.amount}>
                      {formatMoney(expense.amount)}
                    </ThemedText>
                    <View style={styles.row}>
                      <View
                        style={[
                          styles.dot,
                          { backgroundColor: expense.category.color ?? theme.textSecondary },
                        ]}
                      />
                      <ThemedText type="small" themeColor="textSecondary">
                        {expense.category.name}
                      </ThemedText>
                    </View>
                  </View>

                  {expense.description && (
                    <ThemedText type="small" themeColor="textSecondary">
                      {expense.description}
                    </ThemedText>
                  )}

                  <View style={styles.row}>
                    <Button label="Edit" onPress={() => startEditing(expense.id)} secondary onCard />
                    <DeleteButton onConfirm={() => remove(expense)} busy={deletingId === expense.id} />
                  </View>
                </ThemedView>
              ),
            )}
          </View>
        ))}

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
  );
}

/** Add form when `expense` is absent, edit form otherwise. */
function ExpenseForm({
  session,
  categories,
  expense,
  onSaved,
  onCancel,
  onAuthError,
}: {
  session: Session;
  categories: MyCategory[];
  expense?: Expense;
  onSaved: () => void;
  onCancel: () => void;
  onAuthError: (e: unknown) => void;
}) {
  const [amount, setAmount] = useState(expense ? String(expense.amount.amount) : '');
  const [currency, setCurrency] = useState(expense?.amount.currency ?? DEFAULT_CURRENCY);
  const [date, setDate] = useState(expense?.date ?? todayIso());
  const [categoryId, setCategoryId] = useState<string | null>(expense?.category.id ?? null);
  const [description, setDescription] = useState(expense?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    // Same rules as the backend, checked first so the user gets instant feedback.
    const errors = validateAmountAndDate(amount, date);
    if (categoryId === null) errors.categoryId = 'Choose a category';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || categoryId === null) return;

    const body: SaveExpense = {
      amount: toMoney(amount, currency)!,
      date: date.trim(),
      categoryId,
      description: description.trim() || undefined,
    };

    setSaving(true);
    setError(null);
    try {
      if (expense) {
        await updateExpense(session, expense.id, body);
      } else {
        await createExpense(session, body);
      }
      onSaved();
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        onAuthError(e);
      } else {
        setFieldErrors(e instanceof ApiRequestError ? e.fields : {});
        setError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <ThemedText type="smallBold">{expense ? 'Edit expense' : 'New expense'}</ThemedText>

      <Field label="Amount" error={moneyFieldError(fieldErrors)}>
        <TextField placeholder="e.g. 12.50" keyboardType="decimal-pad" value={amount} onChangeText={setAmount} />
      </Field>

      <Field label="Currency">
        <CurrencyPicker value={currency} onChange={setCurrency} />
      </Field>

      <Field label="Date (YYYY-MM-DD)" error={fieldErrors.date}>
        <TextField placeholder={todayIso()} autoCorrect={false} maxLength={10} value={date} onChangeText={setDate} />
      </Field>

      <Field label="Category" error={fieldErrors.categoryId}>
        <View style={styles.row}>
          {categories.map((c) => (
            <Chip
              key={c.id}
              label={c.name}
              selected={categoryId === c.id}
              onPress={() => setCategoryId(c.id)}
              onCard
            />
          ))}
        </View>
      </Field>

      <Field label="Description (optional)" error={fieldErrors.description}>
        <TextField placeholder="What was it for?" maxLength={255} value={description} onChangeText={setDescription} />
      </Field>

      {error && <ThemedText style={styles.errorText}>{error}</ThemedText>}

      <View style={styles.row}>
        <Button label={saving ? 'Saving…' : 'Save'} onPress={save} disabled={saving} />
        <Button label="Cancel" onPress={onCancel} disabled={saving} secondary onCard />
      </View>
    </ThemedView>
  );
}

const local = StyleSheet.create({
  /**
   * A plain column, unlike `financeStyles.contentContainer` which is a row (it centres the page
   * on wide screens). The header and the page stack here, and the page never exceeds the screen.
   */
  content: {
    flexGrow: 1,
    paddingBottom: BottomTabInset + Spacing.four,
  },
  /** The same width rules the Profile tab uses, so every consumer tab lines up. */
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  /** One day's block: the date line, then that day's expenses. */
  dayGroup: {
    gap: Spacing.two,
  },
  /** A filter field that shares its row with another one. */
  grow: {
    flex: 1,
    minWidth: 130,
  },
});
