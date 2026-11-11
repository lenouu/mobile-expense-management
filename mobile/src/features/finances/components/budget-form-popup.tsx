import { useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Animated, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Calendar } from '@/components/ui/calendar';
import { Icon } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { Brand, Radius, Spacing, Status } from '@/constants/theme';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { financeStyles, formatLongDate, formatMoney, TextField, todayIso } from '@/features/finances/components/finance-ui';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { type Budget, createBudget, updateBudget } from '@/features/finances/lib/budget-api';
import { fetchMyCategories, type MyCategory } from '@/features/finances/lib/finances-api';
import { useShake } from '@/hooks/use-shake';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_CATEGORY_COLOR = '#01916D';

/** One line of the split: a category and the amount the user typed for it. */
type SplitRow = { categoryId: string; amount: string };

function parseAmount(text: string): number {
  const cleaned = text.replace(/\s/g, '').replace(',', '.');
  const value = Number(cleaned);
  return Number.isFinite(value) && value > 0 ? value : 0;
}

function isoPlusDays(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

function daysBetween(from: string, to: string): number {
  const [fy, fm, fd] = from.split('-').map(Number);
  const [ty, tm, td] = to.split('-').map(Number);
  return Math.round((Date.UTC(ty, tm - 1, td) - Date.UTC(fy, fm - 1, fd)) / 86_400_000) + 1;
}

/**
 * Setting or changing a budget.
 *
 * <p>The form asks for the three things that make a plan: a window of its own, an amount, and
 * optionally a split of that amount across categories. **The split has to add up.** That is a
 * frontend refusal - the button turns red and the card shakes - with the server checking the same
 * rule as a safety net.
 */
export function BudgetFormPopup({
  visible,
  onClose,
  session,
  budget,
  defaultStart,
  onSaved,
  onAuthError,
}: {
  visible: boolean;
  onClose: () => void;
  session: Session;
  /** Present when changing an existing plan; absent when setting the first one. */
  budget: Budget | null;
  /** Where a new plan may begin: the day after the one in force ends. Defaults to today. */
  defaultStart?: string;
  onSaved: (message: string) => void;
  onAuthError: () => void;
}) {
  const theme = useTheme();
  const { offset, shake } = useShake();

  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [name, setName] = useState('');
  const [start, setStart] = useState(todayIso());
  const [end, setEnd] = useState(isoPlusDays(6));
  const [overall, setOverall] = useState('');
  const [rows, setRows] = useState<SplitRow[]>([]);
  const [openCalendar, setOpenCalendar] = useState<'start' | 'end' | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;

    setError(null);
    setPickerOpen(false);
    setOpenCalendar(null);

    if (budget) {
      setName(budget.name ?? '');
      setStart(budget.startDate);
      setEnd(budget.endDate);
      setOverall(String(budget.overallAmount));
      setRows(
        budget.allocations.map((allocation) => ({
          categoryId: allocation.category.id,
          amount: String(allocation.allocatedAmount),
        })),
      );
    } else {
      setName('');
      const from = defaultStart ?? todayIso();
      setStart(from);
      setEnd(isoPlusDaysFrom(from, 6));
      setOverall('');
      setRows([]);
    }
  }, [visible, budget, defaultStart]);

  useEffect(() => {
    if (!visible) return;

    let cancelled = false;
    fetchMyCategories(session, { size: 200 })
      .then((page) => {
        if (!cancelled) setCategories(page.data);
      })
      .catch((e) => {
        if (e instanceof ApiRequestError && e.status === 401) onAuthError();
      });
    return () => {
      cancelled = true;
    };
  }, [visible, session, onAuthError]);

  const planned = parseAmount(overall);
  const allocated = useMemo(
    () => rows.reduce((sum, row) => sum + parseAmount(row.amount), 0),
    [rows],
  );

  /** What is wrong with the plan, or null when it is ready. */
  const problem = useMemo(() => {
    if (planned <= 0) return 'Enter the amount you want to stay under.';
    if (end < start) return 'The end date has to come after the start date.';

    const days = daysBetween(start, end);
    if (days < 7) return 'A budget has to last at least a week.';
    if (days > 366) return 'A budget can last at most a year.';
    if (start < todayIso()) return 'A budget cannot start in the past.';

    if (rows.length > 0 && allocated !== planned) {
      const difference = planned - allocated;
      return difference > 0
        ? `The categories add up to ${formatMoney({ amount: allocated, currency: session.baseCurrency })}, ${formatMoney({ amount: difference, currency: session.baseCurrency })} short of the total.`
        : `The categories add up to ${formatMoney({ amount: allocated, currency: session.baseCurrency })}, ${formatMoney({ amount: -difference, currency: session.baseCurrency })} over the total.`;
    }

    return null;
  }, [planned, start, end, rows, allocated, session.baseCurrency]);

  const available = categories.filter(
    (category) => !rows.some((row) => row.categoryId === category.id),
  );

  const save = async () => {
    if (problem) {
      setError(problem);
      shake();
      return;
    }

    setSaving(true);
    setError(null);

    const plan = {
      name: name.trim() || undefined,
      startDate: start,
      endDate: end,
      overallAmount: planned,
      allocations: rows
        .map((row) => ({ categoryId: row.categoryId, allocatedAmount: parseAmount(row.amount) }))
        .filter((allocation) => allocation.allocatedAmount > 0),
    };

    try {
      if (budget) {
        await updateBudget(session, budget.id, plan);
        onSaved('Your budget has been updated.');
      } else {
        await createBudget(session, plan);
        onSaved(
          `A budget of ${formatMoney({ amount: planned, currency: session.baseCurrency })} is set for ${formatLongDate(start)} – ${formatLongDate(end)}.`,
        );
      }
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        onAuthError();
      } else {
        setError(describeError(e));
        shake();
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Popup visible={visible} onClose={onClose} expand>
      <Animated.View style={[styles.body, { transform: [{ translateX: offset }] }]}>
        <ThemedText type="hero">{budget ? 'Edit your budget' : 'Set a budget'}</ThemedText>

        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            Name (optional)
          </ThemedText>
          <TextField
            placeholder="e.g. October plan"
            maxLength={60}
            value={name}
            onChangeText={setName}
            accessibilityLabel="Budget name"
          />
        </View>

        {/* The window, chosen here rather than inherited from a financial period. */}
        <View style={styles.dateRow}>
          <Pressable
            onPress={() => setOpenCalendar(openCalendar === 'start' ? null : 'start')}
            accessibilityRole="button"
            accessibilityLabel={`Start ${formatLongDate(start)}. Change it.`}
            style={({ pressed }) => [
              styles.dateField,
              { borderColor: openCalendar === 'start' ? Brand.green : theme.border },
              pressed && styles.pressed,
            ]}>
            <ThemedText type="caption" themeColor="textMuted">
              Starts
            </ThemedText>
            <ThemedText type="small">{formatLongDate(start)}</ThemedText>
          </Pressable>

          <Pressable
            onPress={() => setOpenCalendar(openCalendar === 'end' ? null : 'end')}
            accessibilityRole="button"
            accessibilityLabel={`Ends ${formatLongDate(end)}. Change it.`}
            style={({ pressed }) => [
              styles.dateField,
              { borderColor: openCalendar === 'end' ? Brand.green : theme.border },
              pressed && styles.pressed,
            ]}>
            <ThemedText type="caption" themeColor="textMuted">
              Ends
            </ThemedText>
            <ThemedText type="small">{formatLongDate(end)}</ThemedText>
          </Pressable>
        </View>

        {openCalendar === 'start' ? (
          <Calendar
            value={start}
            min={todayIso()}
            onSelect={(iso) => {
              setStart(iso);
              // Keep at least a week without making the user do the arithmetic.
              if (daysBetween(iso, end) < 7) setEnd(isoPlusDaysFrom(iso, 6));
              setOpenCalendar(null);
            }}
          />
        ) : null}

        {openCalendar === 'end' ? (
          <Calendar value={end} min={start} onSelect={(iso) => { setEnd(iso); setOpenCalendar(null); }} />
        ) : null}

        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            Amount to stay under
          </ThemedText>

          <View style={styles.amountRow}>
            <TextField
              placeholder="0"
              keyboardType="decimal-pad"
              value={overall}
              onChangeText={setOverall}
              style={styles.amountInput}
              accessibilityLabel="Budget amount"
            />
            <ThemedText type="hero" themeColor="textMuted">
              {session.baseCurrency}
            </ThemedText>
          </View>
        </View>

        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            Split across categories (optional)
          </ThemedText>

          {rows.map((row) => {
            const category = categories.find((entry) => entry.id === row.categoryId);
            if (!category) return null;

            return (
              <View key={row.categoryId} style={[styles.splitRow, { borderColor: theme.border }]}>
                <CategoryBadge
                  icon={category.icon}
                  color={category.color ?? DEFAULT_CATEGORY_COLOR}
                  size={26}
                />
                <ThemedText type="small" style={styles.splitName} numberOfLines={1}>
                  {category.name}
                </ThemedText>
                <TextField
                  placeholder="0"
                  keyboardType="decimal-pad"
                  value={row.amount}
                  onChangeText={(text) =>
                    setRows((current) =>
                      current.map((entry) =>
                        entry.categoryId === row.categoryId ? { ...entry, amount: text } : entry,
                      ),
                    )
                  }
                  style={styles.splitInput}
                  accessibilityLabel={`Amount for ${category.name}`}
                />
                <Pressable
                  onPress={() =>
                    setRows((current) => current.filter((entry) => entry.categoryId !== row.categoryId))
                  }
                  accessibilityRole="button"
                  accessibilityLabel={`Remove ${category.name} from the plan`}
                  hitSlop={8}
                  style={({ pressed }) => [styles.removeButton, pressed && styles.pressed]}>
                  <Icon name="close" size={16} color={theme.textMuted} />
                </Pressable>
              </View>
            );
          })}

          <Pressable
            onPress={() => setPickerOpen(true)}
            disabled={available.length === 0}
            accessibilityRole="button"
            accessibilityLabel="Add a category to the split"
            style={({ pressed }) => [
              styles.addRow,
              { borderColor: theme.border },
              available.length === 0 && styles.disabled,
              pressed && available.length > 0 && styles.pressed,
            ]}>
            <Icon name="add" size={18} color={theme.textSecondary} />
            <ThemedText type="small" themeColor="textSecondary" style={styles.splitName}>
              {available.length === 0 ? 'Every category is in the plan' : 'Add a category'}
            </ThemedText>
            <Icon name="chevronDown" size={18} color={theme.textSecondary} />
          </Pressable>

          <View style={styles.totalRow}>
            <ThemedText type="small" themeColor="textSecondary">
              Allocated
            </ThemedText>
            <ThemedText
              type="smallBold"
              style={rows.length > 0 && allocated !== planned ? { color: Status.danger } : undefined}>
              {formatMoney({ amount: allocated, currency: session.baseCurrency })}
              {planned > 0 ? ` of ${formatMoney({ amount: planned, currency: session.baseCurrency })}` : ''}
            </ThemedText>
          </View>
        </View>

        {budget ? (
          <ThemedText type="caption" themeColor="textMuted">
            {budget.editsRemaining} of 3 edits left. Saving this uses one.
          </ThemedText>
        ) : (
          <ThemedText type="caption" themeColor="textMuted">
            Afterwards you get three edits, and only before the start or in the first 6% of the
            window.
          </ThemedText>
        )}

        {error ? <ThemedText style={financeStyles.errorText}>{error}</ThemedText> : null}

        <View style={styles.actions}>
          <Button
            primary={problem === null}
            label={saving ? 'Saving…' : budget ? 'Save changes' : 'Create budget'}
            onPress={save}
            disabled={saving}
            style={styles.grow}
          />
          <Button label="Cancel" onPress={onClose} secondary onCard style={styles.grow} />
        </View>

        {saving ? <ActivityIndicator /> : null}
      </Animated.View>

      {/* The drop-down of categories not yet in the plan. */}
      <Popup visible={pickerOpen} onClose={() => setPickerOpen(false)}>
        <View style={styles.body}>
          <ThemedText type="hero">Add a category</ThemedText>

          {available.map((category) => (
            <Pressable
              key={category.id}
              onPress={() => {
                setRows((current) => [...current, { categoryId: category.id, amount: '' }]);
                setPickerOpen(false);
              }}
              accessibilityRole="button"
              accessibilityLabel={`Add ${category.name}`}
              style={({ pressed }) => [styles.pickerRow, { borderColor: theme.border }, pressed && styles.pressed]}>
              <CategoryBadge
                icon={category.icon}
                color={category.color ?? DEFAULT_CATEGORY_COLOR}
                size={26}
              />
              <ThemedText type="small" style={styles.splitName} numberOfLines={1}>
                {category.name}
              </ThemedText>
              <Icon name="add" size={18} color={Brand.green} />
            </Pressable>
          ))}
        </View>
      </Popup>
    </Popup>
  );
}

/** The same day arithmetic as `isoPlusDays`, from a given starting date. */
function isoPlusDaysFrom(from: string, days: number): string {
  const [year, month, day] = from.split('-').map(Number);
  const date = new Date(year, month - 1, day + days);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`;
}

const styles = StyleSheet.create({
  body: {
    gap: Spacing.three,
  },
  block: {
    gap: Spacing.two,
  },
  dateRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  dateField: {
    flex: 1,
    gap: Spacing.half,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  amountInput: {
    flex: 1,
    fontSize: 30,
    lineHeight: 38,
    fontWeight: '700',
    letterSpacing: -0.5,
    minHeight: 56,
  },
  splitRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  splitName: {
    flex: 1,
  },
  splitInput: {
    width: 92,
    textAlign: 'right',
  },
  removeButton: {
    width: 24,
    height: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  addRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.three,
  },
  pickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  totalRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  grow: {
    flex: 1,
  },
  disabled: {
    opacity: 0.5,
  },
  pressed: {
    opacity: 0.7,
  },
});
