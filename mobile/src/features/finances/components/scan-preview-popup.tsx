import { useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Calendar } from '@/components/ui/calendar';
import { Icon } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import {
  formatLongDate,
  parseAmount,
  TextField,
  todayIso,
  toMoney,
} from '@/features/finances/components/finance-ui';
import { fetchBudgets, type BudgetSummary } from '@/features/finances/lib/budget-api';
import { createExpense, type MyCategory } from '@/features/finances/lib/finances-api';
import type { ScannedExpense } from '@/features/finances/lib/receipt-scan';
import { useTheme } from '@/hooks/use-theme';
import { DEFAULT_CURRENCY, type Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_CATEGORY_COLOR = '#01916D';

type Draft = {
  key: string;
  description: string;
  /** Kept as text: the user edits it like any other amount field. */
  amount: string;
  date: string;
  categoryId: string | null;
  /** Null means "not counted against a budget". */
  budgetId: string | null;
};

/** The category the model named, found among the ones the user actually has. */
function matchCategory(name: string | null, categories: MyCategory[]): string | null {
  if (categories.length === 0) return null;

  // The backend already resolves the model's answer against this list, so a row normally
  // arrives with a category. The fallback is what keeps it from ever arriving blank.
  if (!name) return categories[0].id;

  const target = name.trim().toLowerCase();
  if (!target) return categories[0].id;

  const exact = categories.find((category) => category.name.trim().toLowerCase() === target);
  if (exact) return exact.id;

  // A near miss is still useful as a starting point; the user can change it below.
  const partial = categories.find((category) => {
    const candidate = category.name.trim().toLowerCase();
    return candidate.includes(target) || target.includes(candidate);
  });
  return partial?.id ?? categories[0].id;
}

/** The budget an expense belongs to "by default": one that is running today. */
function pickCurrentBudget(list: BudgetSummary[]): BudgetSummary | null {
  const today = todayIso();
  return (
    list.find(
      (entry) =>
        (entry.status === 'ACTIVE' || entry.status === 'FIXED') &&
        today >= entry.startDate &&
        today <= entry.endDate,
    ) ??
    list.find((entry) => entry.status === 'ACTIVE') ??
    null
  );
}

/**
 * What the AI read, before any of it is real.
 *
 * Every row is editable the same way the expense form is - amount, date, category, note -
 * and each one can be counted against the running budget either together or one at a time.
 * Nothing is written until "Add" is pressed, so a bad guess costs a tap, not a record.
 */
export function ScanPreviewPopup({
  visible,
  scanned,
  categories,
  session,
  onClose,
  onSaved,
  onAuthError,
}: {
  visible: boolean;
  scanned: ScannedExpense[];
  categories: MyCategory[];
  session: Session;
  onClose: () => void;
  onSaved: (count: number) => void;
  onAuthError: () => void;
}) {
  const theme = useTheme();
  const currency = session.baseCurrency || DEFAULT_CURRENCY;

  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [currentBudget, setCurrentBudget] = useState<BudgetSummary | null>(null);
  const [openPicker, setOpenPicker] = useState<{ key: string; kind: 'date' | 'category' } | null>(
    null,
  );
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Rebuild the editable rows each time a new scan arrives.
  useEffect(() => {
    if (!visible) return;
    setDrafts(
      scanned.map((item, index) => ({
        key: `${index}-${item.description}`,
        description: item.description,
        amount: String(item.amount),
        date: item.date,
        categoryId: matchCategory(item.categoryName, categories),
        budgetId: null,
      })),
    );
    setOpenPicker(null);
    setError(null);
  }, [visible, scanned, categories]);

  // The budget these expenses would count against, if one is running today.
  useEffect(() => {
    if (!visible) return;
    let cancelled = false;

    fetchBudgets(session)
      .then((list) => {
        if (!cancelled) setCurrentBudget(pickCurrentBudget(list));
      })
      .catch(() => {
        // Recording without a budget is still perfectly valid, so this is not an error.
      });

    return () => {
      cancelled = true;
    };
  }, [visible, session]);

  const update = (key: string, patch: Partial<Draft>) => {
    setDrafts((previous) =>
      previous.map((draft) => (draft.key === key ? { ...draft, ...patch } : draft)),
    );
  };

  const allInBudget =
    drafts.length > 0 && currentBudget !== null && drafts.every((draft) => draft.budgetId !== null);

  const toggleAll = () => {
    if (!currentBudget) return;
    const budgetId = allInBudget ? null : currentBudget.id;
    setDrafts((previous) => previous.map((draft) => ({ ...draft, budgetId })));
  };

  const toggleBudget = (key: string) => {
    if (!currentBudget) return;
    const currently = drafts.find((draft) => draft.key === key)?.budgetId ?? null;
    update(key, { budgetId: currently ? null : currentBudget.id });
  };

  const valid =
    drafts.length > 0 &&
    drafts.every((draft) => {
      const amount = parseAmount(draft.amount);
      return amount !== null && amount > 0 && draft.categoryId !== null;
    });

  const save = async () => {
    if (!valid || saving) return;

    setSaving(true);
    setError(null);
    try {
      for (const draft of drafts) {
        const money = toMoney(draft.amount, currency);
        if (!money || draft.categoryId === null) continue;

        await createExpense(session, {
          amount: money,
          date: draft.date,
          categoryId: draft.categoryId,
          description: draft.description.trim() || undefined,
          budgetId: draft.budgetId ?? undefined,
        });
      }
      onSaved(drafts.length);
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        onAuthError();
        return;
      }
      setError(describeError(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <Popup visible={visible} onClose={onClose} expand dismissOnBackdrop={false}>
      <View style={styles.body}>
        <ThemedText type="hero">Review the receipt</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {drafts.length === 1
            ? 'One expense was found. Check it, then add it.'
            : `${drafts.length} expenses were found. Check them, then add them.`}
        </ThemedText>

        {/* All or nothing, in one tap - then individual rows can still opt out. */}
        {currentBudget ? (
          <Pressable
            onPress={toggleAll}
            accessibilityRole="checkbox"
            accessibilityState={{ checked: allInBudget }}
            accessibilityLabel={`Count every expense against ${currentBudget.name ?? 'the running budget'}`}
            style={({ pressed }) => [
              styles.masterRow,
              {
                borderColor: allInBudget ? Brand.green : theme.border,
                backgroundColor: allInBudget ? theme.backgroundSelected : theme.background,
              },
              pressed && styles.pressed,
            ]}>
            <View
              style={[
                styles.checkbox,
                {
                  borderColor: allInBudget ? Brand.green : theme.border,
                  backgroundColor: allInBudget ? Brand.green : 'transparent',
                },
              ]}>
              {allInBudget ? <Icon name="verified" size={12} color="#FFFFFF" /> : null}
            </View>

            <View style={styles.copy}>
              <ThemedText type="smallBold">
                Count all of them against {currentBudget.name ?? 'the running budget'}
              </ThemedText>
              <ThemedText type="caption" themeColor="textMuted">
                Or choose one by one below.
              </ThemedText>
            </View>
          </Pressable>
        ) : (
          <ThemedText type="caption" themeColor="textMuted">
            No budget is running today, so these will be recorded without one.
          </ThemedText>
        )}

        {drafts.map((draft, index) => {
          const category = categories.find((entry) => entry.id === draft.categoryId) ?? null;
          const dateOpen = openPicker?.key === draft.key && openPicker.kind === 'date';
          const categoryOpen = openPicker?.key === draft.key && openPicker.kind === 'category';
          const inBudget = draft.budgetId !== null;

          return (
            <View
              key={draft.key}
              style={[styles.card, { borderColor: theme.border, backgroundColor: theme.background }]}>
              <View style={styles.cardHead}>
                <ThemedText type="smallBold">Expense {index + 1}</ThemedText>
              </View>

              <TextField
                placeholder="What was it for?"
                maxLength={255}
                value={draft.description}
                onChangeText={(text) => update(draft.key, { description: text })}
                accessibilityLabel={`Description of expense ${index + 1}`}
              />

              <View style={styles.amountRow}>
                <TextField
                  placeholder="0.00"
                  keyboardType="decimal-pad"
                  value={draft.amount}
                  onChangeText={(text) => update(draft.key, { amount: text })}
                  style={styles.amountInput}
                  accessibilityLabel={`Amount of expense ${index + 1} in ${currency}`}
                />
                <ThemedText type="subtitle" themeColor="textMuted">
                  {currency}
                </ThemedText>
              </View>

              <Pressable
                onPress={() =>
                  setOpenPicker(dateOpen ? null : { key: draft.key, kind: 'date' })
                }
                accessibilityRole="button"
                accessibilityLabel={`Date ${formatLongDate(draft.date)}. Change it.`}
                style={({ pressed }) => [
                  styles.pickerRow,
                  {
                    borderColor: dateOpen ? Brand.green : theme.border,
                    backgroundColor: theme.inputBackground,
                  },
                  pressed && styles.pressed,
                ]}>
                <Icon name="clock" size={18} color={theme.textSecondary} />
                <ThemedText type="small" style={styles.pickerLabel}>
                  {formatLongDate(draft.date)}
                </ThemedText>
                <Icon
                  name={dateOpen ? 'chevronUp' : 'chevronDown'}
                  size={18}
                  color={theme.textSecondary}
                />
              </Pressable>

              {dateOpen ? (
                <Calendar
                  value={draft.date}
                  onSelect={(iso) => {
                    update(draft.key, { date: iso });
                    setOpenPicker(null);
                  }}
                />
              ) : null}

              <Pressable
                onPress={() =>
                  setOpenPicker(categoryOpen ? null : { key: draft.key, kind: 'category' })
                }
                accessibilityRole="button"
                accessibilityLabel={
                  category ? `Category ${category.name}. Change it.` : 'Choose a category'
                }
                style={({ pressed }) => [
                  styles.pickerRow,
                  {
                    borderColor: categoryOpen ? Brand.green : theme.border,
                    backgroundColor: theme.inputBackground,
                  },
                  pressed && styles.pressed,
                ]}>
                {category ? (
                  <CategoryBadge
                    icon={category.icon}
                    color={category.color ?? DEFAULT_CATEGORY_COLOR}
                    size={24}
                  />
                ) : (
                  <Icon name="categories" size={18} color={theme.textSecondary} />
                )}
                <ThemedText
                  type="small"
                  themeColor={category ? 'text' : 'textMuted'}
                  style={styles.pickerLabel}
                  numberOfLines={1}>
                  {category ? category.name : 'Choose a category'}
                </ThemedText>
                <Icon
                  name={categoryOpen ? 'chevronUp' : 'chevronDown'}
                  size={18}
                  color={theme.textSecondary}
                />
              </Pressable>

              {categoryOpen ? (
                <ScrollView
                  style={styles.categoryList}
                  nestedScrollEnabled
                  keyboardShouldPersistTaps="handled">
                  {categories.map((entry) => {
                    const active = entry.id === draft.categoryId;

                    return (
                      <Pressable
                        key={entry.id}
                        onPress={() => {
                          update(draft.key, { categoryId: entry.id });
                          setOpenPicker(null);
                        }}
                        accessibilityRole="button"
                        accessibilityState={{ selected: active }}
                        style={({ pressed }) => [
                          styles.categoryOption,
                          {
                            borderColor: active ? Brand.green : theme.border,
                            backgroundColor: active ? theme.backgroundSelected : theme.background,
                          },
                          pressed && styles.pressed,
                        ]}>
                        <CategoryBadge
                          icon={entry.icon}
                          color={entry.color ?? DEFAULT_CATEGORY_COLOR}
                          size={22}
                        />
                        <ThemedText type="small" style={styles.pickerLabel} numberOfLines={1}>
                          {entry.name}
                        </ThemedText>
                        {active ? <Icon name="verified" size={16} color={Brand.green} /> : null}
                      </Pressable>
                    );
                  })}
                </ScrollView>
              ) : null}

              {currentBudget ? (
                <Pressable
                  onPress={() => toggleBudget(draft.key)}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: inBudget }}
                  accessibilityLabel={`Count expense ${index + 1} against ${currentBudget.name ?? 'the running budget'}`}
                  style={({ pressed }) => [styles.budgetRow, pressed && styles.pressed]}>
                  <View
                    style={[
                      styles.checkbox,
                      {
                        borderColor: inBudget ? Brand.green : theme.border,
                        backgroundColor: inBudget ? Brand.green : 'transparent',
                      },
                    ]}>
                    {inBudget ? <Icon name="verified" size={12} color="#FFFFFF" /> : null}
                  </View>
                  <ThemedText type="small" style={styles.pickerLabel}>
                    Count against {currentBudget.name ?? 'the running budget'}
                  </ThemedText>
                </Pressable>
              ) : null}
            </View>
          );
        })}

        {!valid && drafts.length > 0 ? (
          <ThemedText type="caption" themeColor="textMuted">
            Every expense needs an amount and a category before it can be added.
          </ThemedText>
        ) : null}

        {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}

        <View style={styles.actions}>
          <Button
            primary
            label={saving ? 'Adding…' : `Add ${drafts.length === 1 ? 'expense' : `${drafts.length} expenses`}`}
            onPress={save}
            disabled={!valid || saving}
            style={styles.grow}
          />
          <Button label="Cancel" onPress={onClose} secondary onCard disabled={saving} style={styles.grow} />
        </View>

        {saving ? <ActivityIndicator style={styles.busy} /> : null}
      </View>
    </Popup>
  );
}

const styles = StyleSheet.create({
  body: {
    gap: Spacing.three,
  },
  masterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  checkbox: {
    width: 22,
    height: 22,
    borderRadius: 6,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  copy: {
    flex: 1,
    gap: Spacing.half,
  },
  card: {
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.card,
    padding: Spacing.three,
  },
  cardHead: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  amountInput: {
    flex: 1,
  },
  pickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    minHeight: 44,
  },
  pickerLabel: {
    flex: 1,
  },
  budgetRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingVertical: Spacing.one,
  },
  categoryList: {
    maxHeight: 190,
  },
  categoryOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
    marginBottom: Spacing.one,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  grow: {
    flex: 1,
  },
  error: {
    color: '#D93025',
  },
  busy: {
    marginTop: Spacing.one,
  },
  pressed: {
    opacity: 0.7,
  },
});
