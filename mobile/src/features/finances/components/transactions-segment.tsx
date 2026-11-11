import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Icon } from '@/components/ui/icon';
import { Calendar } from '@/components/ui/calendar';
import { Pagination } from '@/components/ui/pagination';
import { Popup } from '@/components/ui/popup';
import { Button, Chip } from '@/components/ui/primitives';
import type { ToastKind } from '@/components/ui/top-toast';
import { Brand, MaxContentWidth, Radius, Shadows, Spacing } from '@/constants/theme';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { CsvImportPopup } from '@/features/finances/components/csv-import-popup';
import { ExpenseForm } from '@/features/finances/components/expense-form';
import {
  daysBetween,
  describeDuration,
  financeStyles,
  formatLongDate,
  formatMoney,
  TextField,
  todayIso,
} from '@/features/finances/components/finance-ui';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import {
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
} from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const PAGE_SIZE = 20;
const DEFAULT_COLOR = '#01916D';

/**
 * The window is a reporting range, not the financial period (section 6.2 of the vision), so the
 * period is one preset among four rather than a constraint.
 */
type WindowKey = 'period' | 'week' | 'month' | 'custom';

const WINDOWS: { key: WindowKey; label: string }[] = [
  // The period first: it is the window budgets are scored against, so the tab agrees with them.
  { key: 'period', label: 'This period' },
  { key: 'month', label: 'This month so far' },
  { key: 'week', label: 'Last 7 days' },
  { key: 'custom', label: 'Custom range' },
];

/** null: form closed. 'new': adding. An expense: editing that one. */
type Editing = null | 'new' | Expense;

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

/** What to call an expense in a sentence: its description, or failing that its category. */
function describeExpense(expense: Expense): string {
  return expense.description?.trim() || expense.category.name;
}

/**
 * The Transactions segment: the records themselves.
 *
 * One window controls both the list and the total, because a total that disagreed with the rows
 * underneath it would be worse than no total. Rows are compact and grouped by day, and tapping one
 * opens it for editing - deleting lives inside that form rather than on the list, so a stray tap
 * can never destroy a record.
 */
export function TransactionsSegment({
  session,
  onToast,
  onSignOut,
}: {
  session: Session;
  /** Confirms a create, edit or delete; the screen hosts the toast so it can overlay its chrome. */
  onToast: (kind: ToastKind, message: string) => void;
  onSignOut: (message?: string) => void;
}) {
  const theme = useTheme();

  const [rows, setRows] = useState<Expense[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState<ExpenseTotal | null>(null);
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [period, setPeriod] = useState<FinancialPeriod | null>(null);
  const [loading, setLoading] = useState(true);
  const [totalLoading, setTotalLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // The window and the filters that narrow it.
  const [windowKey, setWindowKey] = useState<WindowKey>('period');
  const [customFrom, setCustomFrom] = useState(daysAgo(29));
  const [customTo, setCustomTo] = useState(todayIso());
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [q, setQ] = useState('');

  // Pop-ups and the form. `formOpen` is separate from `editing` on purpose: the pop-up animates
  // out while still rendering its content, so `editing` is cleared only once it has gone.
  const [windowOpen, setWindowOpen] = useState(false);
  const [customOpen, setCustomOpen] = useState(false);
  const [openCalendar, setOpenCalendar] = useState<'from' | 'to' | null>(null);
  const [draftFrom, setDraftFrom] = useState(customFrom);
  const [draftTo, setDraftTo] = useState(customTo);
  const [draftError, setDraftError] = useState<string | null>(null);
  const [categoryOpen, setCategoryOpen] = useState(false);
  const [importOpen, setImportOpen] = useState(false);
  const [editing, setEditing] = useState<Editing>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [deleting, setDeleting] = useState(false);

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

  // The current period supplies the "This period" preset's dates and its label.
  useEffect(() => {
    let cancelled = false;
    fetchCurrentPeriod(session)
      .then((current) => {
        if (!cancelled) setPeriod(current);
      })
      .catch(() => {
        // The preset then carries no dates and the backend falls back to the current period.
      });
    return () => {
      cancelled = true;
    };
  }, [session]);

  useEffect(() => {
    let cancelled = false;
    // One large page: the filter list and the form's chips need every category, not the first ten.
    fetchMyCategories(session, { size: 200 })
      .then((result) => {
        if (!cancelled) setCategories(result.data);
      })
      .catch(() => {
        // The screen still works without the filter list; the form explains itself if empty.
      });
    return () => {
      cancelled = true;
    };
  }, [session]);

  const filters: ExpenseFilters = useMemo(() => {
    const next: ExpenseFilters = {};
    if (categoryId) next.categoryId = categoryId;
    if (q) next.q = q;

    if (windowKey === 'period') {
      if (period) {
        next.from = period.startDate;
        next.to = period.endDate;
      }
    } else if (windowKey === 'week') {
      next.from = daysAgo(6);
      next.to = todayIso();
    } else if (windowKey === 'month') {
      next.from = startOfMonth();
      next.to = todayIso();
    } else {
      if (customFrom) next.from = customFrom;
      if (customTo) next.to = customTo;
    }

    return next;
  }, [categoryId, q, windowKey, period, customFrom, customTo]);

  const loadList = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await fetchExpenses(session, { ...filters, page, size: PAGE_SIZE });

      // Deleting the last row of the last page can leave it empty: step back one.
      if (result.data.length === 0 && page > 0 && result.totalPages > 0) {
        setPage(result.totalPages - 1);
        return;
      }

      setRows(result.data);
      setTotalPages(result.totalPages);
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, filters, page, handleError]);
  const loadTotal = useCallback(async () => {
    setTotalLoading(true);
    try {
      setTotal(await fetchExpenseTotal(session, filters));
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) handleError(e);
    } finally {
      setTotalLoading(false);
    }
  }, [session, filters, handleError]);

  // The "This period" preset needs the period's dates before either number means anything. Asking
  // for the list without them would answer with every expense, while the total would answer for
  // the current period - two different questions, so wait for the dates instead.
  const waitingForPeriod = windowKey === 'period' && period === null;

  useEffect(() => {
    if (waitingForPeriod) return;
    loadList();
  }, [loadList, waitingForPeriod]);

  useEffect(() => {
    if (waitingForPeriod) return;
    loadTotal();
  }, [loadTotal, waitingForPeriod]);

  // Typing settles before a request goes out, so a three-word description is one request.
  useEffect(() => {
    const handle = setTimeout(() => {
      const next = searchInput.trim();
      if (next === q) return;
      setQ(next);
      setPage(0);
    }, 350);
    return () => clearTimeout(handle);
  }, [searchInput, q]);

  const reload = () => {
    loadList();
    loadTotal();
  };

  const openForm = (target: Editing) => {
    setEditing(target);
    setFormOpen(true);
  };

  const changeWindow = (key: WindowKey) => {
    setWindowKey(key);
    setPage(0);
    // A preset answers itself, so the pop-up closes; "Custom" needs the two dates first.
    if (key !== 'custom') setWindowOpen(false);
  };

  /** Opens the custom-range pop-up with the calendar on the start date. */
  const openCustomRange = () => {
    setDraftFrom(customFrom);
    setDraftTo(customTo);
    setDraftError(null);
    setOpenCalendar('from');
    setCustomOpen(true);
  };

  /** Commits the draft range; until then the list keeps showing what it already showed. */
  const applyCustomRange = () => {
    if (!draftFrom || !draftTo) {
      setDraftError('Choose both a start and an end date.');
      return;
    }
    if (draftFrom > draftTo) {
      setDraftError('The start date is after the end date.');
      return;
    }

    setCustomFrom(draftFrom);
    setCustomTo(draftTo);
    setWindowKey('custom');
    setPage(0);
    setCustomOpen(false);
  };

  const chooseCategory = (id: string | null) => {
    setCategoryId(id);
    setPage(0);
    setCategoryOpen(false);
  };

  const onSaved = (saved: Expense, created: boolean) => {
    setFormOpen(false);
    onToast(
      created ? 'created' : 'edited',
      `${describeExpense(saved)} has been ${created ? 'added' : 'edited'} successfully.`,
    );
    reload();
  };

  const remove = async (expense: Expense) => {
    setDeleting(true);
    try {
      await deleteExpense(session, expense.id);
      setFormOpen(false);
      onToast('deleted', `${describeExpense(expense)} has been deleted successfully.`);
      reload();
    } catch (e) {
      handleError(e);
    } finally {
      setDeleting(false);
    }
  };

  const groups = useMemo(() => {
    const byDate = new Map<string, Expense[]>();
    for (const expense of rows) {
      const list = byDate.get(expense.date);
      if (list) list.push(expense);
      else byDate.set(expense.date, [expense]);
    }
    return [...byDate.entries()].map(([date, items]) => ({ date, items }));
  }, [rows]);

  /** The window, split so the screen can give each part its own weight. */
  const windowCopy = useMemo(() => {
    if (windowKey === 'period') {
      return {
        title: 'This period',
        dates: period
          ? `${formatLongDate(period.startDate)} – ${formatLongDate(period.endDate)}`
          : 'Your current financial period',
        hint: 'Your financial period · tap to change',
      };
    }
    if (windowKey === 'week') {
      return {
        title: 'Last 7 days',
        dates: `${formatLongDate(daysAgo(6))} – ${formatLongDate(todayIso())}`,
        hint: 'A sliding week · tap to change',
      };
    }
    if (windowKey === 'month') {
      return {
        title: 'This month so far',
        dates: `${formatLongDate(startOfMonth())} – ${formatLongDate(todayIso())}`,
        hint: 'The calendar month · tap to change',
      };
    }
    return {
      title: 'Custom range',
      dates:
        customFrom && customTo
          ? `${formatLongDate(customFrom)} – ${formatLongDate(customTo)}`
          : 'No dates chosen yet',
      hint: 'Any two dates you choose · tap to change',
    };
  }, [windowKey, period, customFrom, customTo]);

  /** The dates each preset would cover, shown under its name so the choice is not a guess. */
  const presetRange = (key: WindowKey): string => {
    if (key === 'period') {
      return period
        ? `${formatLongDate(period.startDate)} – ${formatLongDate(period.endDate)}`
        : 'Your current financial period';
    }
    if (key === 'week') return `${formatLongDate(daysAgo(6))} – ${formatLongDate(todayIso())}`;
    if (key === 'month') return `${formatLongDate(startOfMonth())} – ${formatLongDate(todayIso())}`;
    return customFrom && customTo
      ? `${formatLongDate(customFrom)} – ${formatLongDate(customTo)}`
      : 'Pick a start and an end date';
  };

  const activeCategory = categories.find((category) => category.id === categoryId) ?? null;
  const hasFilters = categoryId !== null || q.length > 0;

  const count = total?.count ?? 0;

  /**
   * How the window is described in the sentence.
   *
   * A period and a calendar month are named cycles that are still running, so they read "so far";
   * a custom range only reads "the last N days" when it actually ends today, and otherwise names
   * its two dates - "the last 22 days" would be a lie about a range that closed three weeks ago.
   */
  const timePhrase = useMemo(() => {
    if (windowKey === 'period') return 'so far this period';
    if (windowKey === 'month') return 'so far this month';
    if (windowKey === 'week') return 'over the last 7 days';

    if (customTo === todayIso()) {
      return `over the last ${describeDuration(daysBetween(customFrom, customTo))}`;
    }
    return `between ${formatLongDate(customFrom)} and ${formatLongDate(customTo)}`;
  }, [windowKey, customFrom, customTo]);

  return (
    <View style={styles.screen}>
      <ScrollView
        style={{ flex: 1, backgroundColor: theme.background }}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={styles.content}
        keyboardShouldPersistTaps="handled"
        refreshControl={<RefreshControl refreshing={loading && rows.length > 0} onRefresh={reload} />}>
        <View style={styles.page}>
          {/* The window: the first thing on the screen, so it is set like a headline. */}
          <Pressable
            onPress={() => setWindowOpen(true)}
            accessibilityRole="button"
            accessibilityLabel={`Window: ${windowCopy.title}, ${windowCopy.dates}. Change it.`}
            style={({ pressed }) => [
              styles.control,
              { borderColor: theme.border, backgroundColor: theme.backgroundElement },
              pressed && styles.pressed,
            ]}>
            <Icon name="clock" size={22} color={theme.textSecondary} />

            <View style={styles.controlCopy}>
              <ThemedText type="hero">{windowCopy.title}</ThemedText>
              <ThemedText type="small" themeColor="textSecondary">
                {windowCopy.dates}
              </ThemedText>
              <ThemedText type="small" themeColor="textMuted">
                {windowCopy.hint}
              </ThemedText>
            </View>
            <Icon name="chevronDown" size={18} color={theme.textSecondary} />
          </Pressable>

          {/* What that window cost, said in a sentence - a bare number does not say what it is. */}
          <ThemedView type="backgroundElement" style={financeStyles.card}>
            {totalLoading && !total ? (
              <ActivityIndicator />
            ) : (
              <>
                {/* The amount is the reason the screen is open, so it gets the hero's weight and
                    the words around it stay quiet. */}
                <ThemedText type="body" themeColor="textSecondary">
                  {count === 0 ? 'You have not spent anything' : 'You have spent'}
                </ThemedText>

                {count === 0 ? null : (
                  <ThemedText type="display" style={styles.amount}>
                    {formatMoney(total?.total ?? { amount: 0, currency: 'XAF' })}
                  </ThemedText>
                )}

                <ThemedText type="body" themeColor="textSecondary">
                  {activeCategory ? `on ${activeCategory.name} ` : ''}
                  {timePhrase}.
                </ThemedText>

                <ThemedText type="small" themeColor="textMuted">
                  {count} {count === 1 ? 'expense' : 'expenses'} ·{' '}
                  {formatLongDate(total?.from ?? customFrom)} – {formatLongDate(total?.to ?? customTo)}
                  {q ? ` · matching “${q}”` : ''}
                </ThemedText>
              </>
            )}
          </ThemedView>

          {/* Filters: a category drop-down and a search toggle, not a wall of chips. */}
          <View style={styles.filterRow}>
            <Pressable
              onPress={() => setCategoryOpen(true)}
              accessibilityRole="button"
              accessibilityLabel={activeCategory ? `Category: ${activeCategory.name}` : 'All categories'}
              style={({ pressed }) => [
                styles.control,
                styles.filterGrow,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                pressed && styles.pressed,
              ]}>
              <ThemedText type="subtitle" style={styles.controlLabel} numberOfLines={1}>
                {activeCategory ? activeCategory.name : 'All categories'}
              </ThemedText>
              <Icon name="chevronDown" size={18} color={theme.textSecondary} />
            </Pressable>

            <Pressable
              onPress={() => {
                // Closing the field clears the search, so the list is never left silently filtered.
                if (searchOpen) {
                  setSearchInput('');
                  setQ('');
                  setPage(0);
                }
                setSearchOpen((current) => !current);
              }}
              accessibilityRole="button"
              accessibilityLabel={searchOpen ? 'Close search' : 'Search expenses'}
              accessibilityState={{ expanded: searchOpen }}
              style={({ pressed }) => [
                styles.iconButton,
                { borderColor: searchOpen ? Brand.green : theme.border, backgroundColor: theme.backgroundElement },
                pressed && styles.pressed,
              ]}>
              <Icon name="search" size={20} color={searchOpen ? Brand.green : theme.textSecondary} />
            </Pressable>
          </View>

          {searchOpen ? (
            <TextField
              placeholder="Search a description, e.g. lunch"
              autoCorrect={false}
              value={searchInput}
              onChangeText={setSearchInput}
              accessibilityLabel="Search expenses by description"
            />
          ) : null}

          {error && <ThemedText style={financeStyles.errorText}>{error}</ThemedText>}

          {loading && rows.length === 0 && <ActivityIndicator style={financeStyles.loader} />}

          {!loading && rows.length === 0 ? (
            <ThemedView type="backgroundElement" style={financeStyles.card}>
              <ThemedText themeColor="textSecondary">
                {hasFilters
                  ? 'Nothing matches these filters. Try a wider window.'
                  : 'Nothing recorded in this window yet.'}
              </ThemedText>
            </ThemedView>
          ) : null}

          {groups.map((group) => (
            <View key={group.date} style={styles.dayGroup}>
              <ThemedText type="smallBold" themeColor="textSecondary">
                {formatLongDate(group.date)}
              </ThemedText>

              {group.items.map((expense) => (
                <Pressable
                  key={expense.id}
                  onPress={() => openForm(expense)}
                  accessibilityRole="button"
                  accessibilityLabel={`Edit ${describeExpense(expense)}, ${formatMoney(expense.amount)}`}
                  style={({ pressed }) => [
                    styles.row,
                    { backgroundColor: theme.backgroundElement, borderColor: theme.border },
                    pressed && styles.rowPressed,
                  ]}>
                  <CategoryBadge
                    icon={expense.category.icon}
                    color={expense.category.color ?? DEFAULT_COLOR}
                    size={36}
                  />

                  <View style={styles.rowCopy}>
                    <ThemedText type="small" numberOfLines={1}>
                      {expense.description?.trim() || expense.category.name}
                    </ThemedText>
                    <ThemedText type="caption" themeColor="textMuted">
                      {expense.category.name}
                    </ThemedText>
                  </View>

                  <ThemedText type="smallBold">{formatMoney(expense.amount)}</ThemedText>
                </Pressable>
              ))}
            </View>
          ))}

          {totalPages > 1 ? (
            <Pagination
              page={page}
              totalPages={totalPages}
              onChange={(next) => setPage(next)}
              disabled={loading}
            />
          ) : null}
        </View>
      </ScrollView>

      {/* Recording, always within thumb reach. */}
      <Pressable
        onPress={() => openForm('new')}
        accessibilityRole="button"
        accessibilityLabel="Add expense"
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}>
        <Icon name="add" size={28} color="#FFFFFF" />
      </Pressable>

      {/* Window picker: one row per preset, with the dates it would cover underneath. */}
      <Popup visible={windowOpen} onClose={() => setWindowOpen(false)}>
        <View style={styles.popupBody}>
          <ThemedText type="hero">Choose a window</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            The interval you are looking at — a preset, or any two dates you choose.
          </ThemedText>

          {WINDOWS.map((option) => {
            const active = windowKey === option.key;

            return (
              <Pressable
                key={option.key}
                onPress={() => {
                  if (option.key === 'custom') {
                    // Custom needs its own pop-up with the two calendars, so hand over to it.
                    setWindowOpen(false);
                    openCustomRange();
                    return;
                  }
                  changeWindow(option.key);
                }}
                accessibilityRole="button"
                accessibilityState={{ selected: active }}
                style={({ pressed }) => [
                  styles.option,
                  {
                    borderColor: active ? Brand.green : theme.border,
                    backgroundColor: theme.background,
                  },
                  pressed && styles.pressed,
                ]}>
                <View style={styles.optionCopy}>
                  <ThemedText type="subtitle">{option.label}</ThemedText>
                  <ThemedText type="small" themeColor="textMuted">
                    {presetRange(option.key)}
                  </ThemedText>
                </View>

                {active ? <Icon name="verified" size={20} color={Brand.green} /> : null}
              </Pressable>
            );
          })}
        </View>
      </Popup>

      {/* Custom range: From at the top with its calendar, To below with its own. */}
      <Popup visible={customOpen} onClose={() => setCustomOpen(false)}>
        <View style={styles.popupBody}>
          <ThemedText type="hero">Custom range</ThemedText>

          <Pressable
            onPress={() => setOpenCalendar(openCalendar === 'from' ? null : 'from')}
            accessibilityRole="button"
            accessibilityLabel={`From ${formatLongDate(draftFrom)}. Change it.`}
            accessibilityState={{ expanded: openCalendar === 'from' }}
            style={({ pressed }) => [
              styles.dateFieldRow,
              { borderColor: openCalendar === 'from' ? Brand.green : theme.border },
              pressed && styles.pressed,
            ]}>
            <View style={styles.optionCopy}>
              <ThemedText type="small" themeColor="textMuted">
                From
              </ThemedText>
              <ThemedText type="smallBold">{formatLongDate(draftFrom)}</ThemedText>
            </View>
            <Icon
              name={openCalendar === 'from' ? 'chevronUp' : 'chevronDown'}
              size={18}
              color={theme.textSecondary}
            />
          </Pressable>

          {openCalendar === 'from' ? (
            <Calendar
              value={draftFrom}
              max={todayIso()}
              onSelect={(iso) => {
                setDraftFrom(iso);
                // A start after the end would be an impossible range: carry the end along.
                if (draftTo < iso) setDraftTo(iso);
                setOpenCalendar(null);
                setDraftError(null);
              }}
            />
          ) : null}

          <Pressable
            onPress={() => setOpenCalendar(openCalendar === 'to' ? null : 'to')}
            accessibilityRole="button"
            accessibilityLabel={`To ${formatLongDate(draftTo)}. Change it.`}
            accessibilityState={{ expanded: openCalendar === 'to' }}
            style={({ pressed }) => [
              styles.dateFieldRow,
              { borderColor: openCalendar === 'to' ? Brand.green : theme.border },
              pressed && styles.pressed,
            ]}>
            <View style={styles.optionCopy}>
              <ThemedText type="small" themeColor="textMuted">
                To
              </ThemedText>
              <ThemedText type="smallBold">{formatLongDate(draftTo)}</ThemedText>
            </View>
            <Icon
              name={openCalendar === 'to' ? 'chevronUp' : 'chevronDown'}
              size={18}
              color={theme.textSecondary}
            />
          </Pressable>

          {openCalendar === 'to' ? (
            <Calendar
              value={draftTo}
              min={draftFrom}
              max={todayIso()}
              onSelect={(iso) => {
                setDraftTo(iso);
                setOpenCalendar(null);
                setDraftError(null);
              }}
            />
          ) : null}

          {draftError ? <ThemedText style={financeStyles.errorText}>{draftError}</ThemedText> : null}

          <View style={financeStyles.row}>
            <Button primary label="Apply" onPress={applyCustomRange} />
            <Button label="Cancel" onPress={() => setCustomOpen(false)} secondary onCard />
          </View>
        </View>
      </Popup>

      {/* Category filter */}
      <Popup visible={categoryOpen} onClose={() => setCategoryOpen(false)}>
        <View style={styles.popupBody}>
          <ThemedText type="subtitle">Filter by category</ThemedText>

          <ScrollView style={styles.categoryList} keyboardShouldPersistTaps="handled" nestedScrollEnabled>
            <Pressable
              onPress={() => chooseCategory(null)}
              accessibilityRole="button"
              accessibilityState={{ selected: categoryId === null }}
              style={({ pressed }) => [
                styles.categoryOption,
                { borderColor: categoryId === null ? Brand.green : theme.border },
                pressed && styles.pressed,
              ]}>
              <ThemedText type="subtitle">All categories</ThemedText>
              {categoryId === null ? <Icon name="verified" size={18} color={Brand.green} /> : null}
            </Pressable>

            {categories.map((category) => (
              <Pressable
                key={category.id}
                onPress={() => chooseCategory(category.id)}
                accessibilityRole="button"
                accessibilityState={{ selected: categoryId === category.id }}
                style={({ pressed }) => [
                  styles.categoryOption,
                  { borderColor: categoryId === category.id ? Brand.green : theme.border },
                  pressed && styles.pressed,
                ]}>
                <CategoryBadge
                  icon={category.icon}
                  color={category.color ?? DEFAULT_COLOR}
                  size={28}
                />
                <ThemedText type="subtitle" style={styles.controlLabel} numberOfLines={1}>
                  {category.name}
                </ThemedText>
                {categoryId === category.id ? <Icon name="verified" size={18} color={Brand.green} /> : null}
              </Pressable>
            ))}
          </ScrollView>
        </View>
      </Popup>

      {/* The form, in the same blurred pop-up the categories use. A half-typed expense is not
          thrown away by a tap on the backdrop; the close button and Cancel do that on purpose. */}
      <Popup
        visible={formOpen}
        onClose={() => setFormOpen(false)}
        onClosed={() => setEditing(null)}
        dismissOnBackdrop={false}>
        {editing ? (
          <ExpenseForm
            session={session}
            categories={categories}
            expense={editing === 'new' ? null : editing}
            onSaved={onSaved}
            onCancel={() => setFormOpen(false)}
            onAuthError={handleError}
            onDelete={editing === 'new' ? undefined : () => remove(editing)}
            deleting={deleting}
            onImportCsv={
              editing === 'new'
                ? () => {
                    // Importing replaces this form: one expense and many are alternatives, so
                    // handing over keeps it one question instead of stacking pop-ups.
                    setFormOpen(false);
                    setImportOpen(true);
                  }
                : undefined
            }
          />
        ) : null}
      </Popup>

      {/* Importing a file: the rules first, then what was found in the file that was chosen. */}
      <CsvImportPopup
        visible={importOpen}
        onClose={() => setImportOpen(false)}
        categoryNames={categories.map((category) => category.name)}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  /** A plain column: the finance content container is a row, which would put the page beside it. */
  content: {
    flexGrow: 1,
    paddingBottom: Spacing.six + 56,
  },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  control: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  controlLabel: {
    flex: 1,
  },
  /** The window's three lines: title, dates, and what the window means. */
  controlCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  /** The hero figure of the screen. */
  amount: {
    color: Brand.green,
  },
  filterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  filterGrow: {
    flex: 1,
  },
  iconButton: {
    width: 44,
    height: 44,
    borderRadius: Radius.field,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayGroup: {
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
    borderWidth: 1,
    borderRadius: Radius.card,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  rowPressed: {
    opacity: 0.7,
  },
  rowCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  popupBody: {
    gap: Spacing.three,
  },
  /** One row per window preset: its name, the dates it covers, and a tick when selected. */
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  optionCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  /** The From / To rows in the custom-range pop-up: label and date, tappable to open a calendar. */
  dateFieldRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  categoryList: {
    maxHeight: 260,
  },
  categoryOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
    marginBottom: Spacing.two,
  },
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
    ...Shadows.button,
  },
  fabPressed: {
    opacity: 0.85,
    transform: [{ scale: 0.94 }],
  },
  pressed: {
    opacity: 0.7,
  },
});
