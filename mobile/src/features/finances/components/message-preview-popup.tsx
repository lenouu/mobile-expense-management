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
import { createIncome, type IncomeSource } from '@/features/finances/lib/income-api';
import type { ScanDirection, ScannedTransaction } from '@/features/finances/lib/message-scan';
import { useTheme } from '@/hooks/use-theme';
import { DEFAULT_CURRENCY, type Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_CATEGORY_COLOR = '#01916D';

type Draft = {
  key: string;
  /** The suggested direction; the user can flip it. */
  direction: ScanDirection;
  description: string;
  /** Kept as text: it is edited like any other amount field. */
  amount: string;
  date: string;
  /** Used when the direction is OUT. */
  categoryId: string | null;
  /** Used when the direction is IN. */
  sourceId: string | null;
  /** Used when the direction is OUT and the user put it under a budget. */
  budgetId: string | null;
};

function matchCategory(name: string | null, categories: MyCategory[]): string | null {
  if (categories.length === 0) return null;

  // The backend resolves the answer against the user's real categories, so this normally hits.
  // The fallback is what keeps an expense row from arriving without a category.
  if (!name) return categories[0].id;

  const target = name.trim().toLowerCase();
  if (!target) return categories[0].id;

  const exact = categories.find((category) => category.name.trim().toLowerCase() === target);
  if (exact) return exact.id;

  const partial = categories.find((category) => {
    const candidate = category.name.trim().toLowerCase();
    return candidate.includes(target) || target.includes(candidate);
  });
  return partial?.id ?? categories[0].id;
}

function matchSource(name: string | null, sources: IncomeSource[]): string | null {
  if (sources.length === 0) return null;
  if (!name) return sources[0].id;

  const target = name.trim().toLowerCase();
  const exact = sources.find((source) => source.name.trim().toLowerCase() === target);
  return exact?.id ?? sources[0].id;
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
 * What the AI read from a pasted message, before any of it is real.
 *
 * The same review the receipt scan gets, with one addition: each row carries a direction the
 * user can flip between an outflow (which needs a category, and may count against a budget) and
 * an inflow (which needs an income source). Nothing is written until "Add" is pressed.
 */
export function MessagePreviewPopup({
  visible,
  scanned,
  categories,
  sources,
  session,
  onClose,
  onSaved,
  onAuthError,
}: {
  visible: boolean;
  scanned: ScannedTransaction[];
  categories: MyCategory[];
  sources: IncomeSource[];
  session: Session;
  onClose: () => void;
  onSaved: (count: number) => void;
  onAuthError: () => void;
}) {
  const theme = useTheme();
  const currency = session.baseCurrency || DEFAULT_CURRENCY;

  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [currentBudget, setCurrentBudget] = useState<BudgetSummary | null>(null);
  const [openPicker, setOpenPicker] = useState<{
    key: string;
    kind: 'date' | 'category' | 'source';
  } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;

    setDrafts(
      scanned.map((item, index) => ({
        key: `${index}-${item.description}`,
        direction: item.direction,
        description: item.description,
        amount: String(item.amount),
        date: item.date,
        categoryId: matchCategory(item.categoryName, categories),
        // An inflow needs a source; without a suggestion, the first one is the best default.
        sourceId: matchSource(item.incomeSourceName, sources) ?? sources[0]?.id ?? null,
        budgetId: null,
      })),
    );
    setOpenPicker(null);
    setError(null);
  }, [visible, scanned, categories, sources]);

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;

    fetchBudgets(session)
      .then((list) => {
        if (!cancelled) setCurrentBudget(pickCurrentBudget(list));
      })
      .catch(() => {
        // Recording without a budget is still valid, so this is not an error.
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

  const outflows = drafts.filter((draft) => draft.direction === 'OUT');
  const allInBudget =
    outflows.length > 0 &&
    currentBudget !== null &&
    outflows.every((draft) => draft.budgetId !== null);

  const toggleAll = () => {
    if (!currentBudget) return;
    const budgetId = allInBudget ? null : currentBudget.id;
    setDrafts((previous) =>
      previous.map((draft) =>
        draft.direction === 'OUT' ? { ...draft, budgetId } : draft,
      ),
    );
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
      if (amount === null || amount <= 0) return false;
      return draft.direction === 'OUT' ? draft.categoryId !== null : draft.sourceId !== null;
    });

  const save = async () => {
    if (!valid || saving) return;

    setSaving(true);
    setError(null);
    try {
      for (const draft of drafts) {
        const money = toMoney(draft.amount, currency);
        if (!money) continue;

        if (draft.direction === 'OUT') {
          if (!draft.categoryId) continue;
          await createExpense(session, {
            amount: money,
            date: draft.date,
            categoryId: draft.categoryId,
            description: draft.description.trim() || undefined,
            budgetId: draft.budgetId ?? undefined,
          });
        } else {
          if (!draft.sourceId) continue;
          await createIncome(session, {
            amount: money,
            date: draft.date,
            sourceId: draft.sourceId,
            description: draft.description.trim() || undefined,
          });
        }
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

  const outCount = outflows.length;
  const inCount = drafts.length - outCount;

  return (
    <Popup visible={visible} onClose={onClose} expand dismissOnBackdrop={false}>
      <View style={styles.body}>
        <ThemedText type="hero">Review the message</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {drafts.length === 1 ? 'One movement was found.' : `${drafts.length} movements were found.`}{' '}
          Check each one, flip it between In and Out if the AI read it the wrong way round, then add
          them.
        </ThemedText>

        {outCount > 0 && currentBudget ? (
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
                Count all expenses against {currentBudget.name ?? 'the running budget'}
              </ThemedText>
              <ThemedText type="caption" themeColor="textMuted">
                Or choose one by one below.
              </ThemedText>
            </View>
          </Pressable>
        ) : null}

        {drafts.map((draft, index) => {
          const isOut = draft.direction === 'OUT';
          const category = categories.find((entry) => entry.id === draft.categoryId) ?? null;
          const source = sources.find((entry) => entry.id === draft.sourceId) ?? null;

          const dateOpen = openPicker?.key === draft.key && openPicker.kind === 'date';
          const categoryOpen = openPicker?.key === draft.key && openPicker.kind === 'category';
          const sourceOpen = openPicker?.key === draft.key && openPicker.kind === 'source';
          const inBudget = draft.budgetId !== null;

          return (
            <View
              key={draft.key}
              style={[styles.card, { borderColor: theme.border, backgroundColor: theme.background }]}>
              <View style={styles.cardHead}>
                <ThemedText type="smallBold">Movement {index + 1}</ThemedText>

                {/* In or out: the model suggests, the user decides. */}
                <View style={[styles.directionSwitch, { borderColor: theme.border }]}>
                  {(['OUT', 'IN'] as const).map((direction) => {
                    const active = draft.direction === direction;
                    return (
                      <Pressable
                        key={direction}
                        onPress={() => update(draft.key, { direction })}
                        accessibilityRole="button"
                        accessibilityState={{ selected: active }}
                        accessibilityLabel={direction === 'OUT' ? 'Out, an expense' : 'In, an income'}
                        style={[
                          styles.directionTab,
                          active && { backgroundColor: theme.backgroundSelected },
                        ]}>
                        <ThemedText
                          type={active ? 'smallBold' : 'small'}
                          themeColor={active ? 'text' : 'textMuted'}>
                          {direction === 'OUT' ? 'Out' : 'In'}
                        </ThemedText>
                      </Pressable>
                    );
                  })}
                </View>
              </View>

              <TextField
                placeholder="What was it for?"
                maxLength={255}
                value={draft.description}
                onChangeText={(text) => update(draft.key, { description: text })}
                accessibilityLabel={`Description of movement ${index + 1}`}
              />

              <View style={styles.amountRow}>
                <TextField
                  placeholder="0.00"
                  keyboardType="decimal-pad"
                  value={draft.amount}
                  onChangeText={(text) => update(draft.key, { amount: text })}
                  style={styles.amountInput}
                  accessibilityLabel={`Amount of movement ${index + 1} in ${currency}`}
                />
                <ThemedText type="subtitle" themeColor="textMuted">
                  {currency}
                </ThemedText>
              </View>

              <Pressable
                onPress={() => setOpenPicker(dateOpen ? null : { key: draft.key, kind: 'date' })}
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

              {isOut ? (
                <>
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
                      style={styles.optionList}
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
                              styles.option,
                              {
                                borderColor: active ? Brand.green : theme.border,
                                backgroundColor: active
                                  ? theme.backgroundSelected
                                  : theme.background,
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
                      accessibilityLabel={`Count movement ${index + 1} against ${currentBudget.name ?? 'the running budget'}`}
                      style={({ pressed }) => [styles.checkRow, pressed && styles.pressed]}>
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
                </>
              ) : (
                <>
                  <Pressable
                    onPress={() =>
                      setOpenPicker(sourceOpen ? null : { key: draft.key, kind: 'source' })
                    }
                    accessibilityRole="button"
                    accessibilityLabel={
                      source ? `Income source ${source.name}. Change it.` : 'Choose an income source'
                    }
                    style={({ pressed }) => [
                      styles.pickerRow,
                      {
                        borderColor: sourceOpen ? Brand.green : theme.border,
                        backgroundColor: theme.inputBackground,
                      },
                      pressed && styles.pressed,
                    ]}>
                    <Icon name="wallet" size={18} color={theme.textSecondary} />
                    <ThemedText
                      type="small"
                      themeColor={source ? 'text' : 'textMuted'}
                      style={styles.pickerLabel}
                      numberOfLines={1}>
                      {source ? source.name : 'Choose where it came from'}
                    </ThemedText>
                    <Icon
                      name={sourceOpen ? 'chevronUp' : 'chevronDown'}
                      size={18}
                      color={theme.textSecondary}
                    />
                  </Pressable>

                  {sourceOpen ? (
                    <ScrollView
                      style={styles.optionList}
                      nestedScrollEnabled
                      keyboardShouldPersistTaps="handled">
                      {sources.map((entry) => {
                        const active = entry.id === draft.sourceId;
                        return (
                          <Pressable
                            key={entry.id}
                            onPress={() => {
                              update(draft.key, { sourceId: entry.id });
                              setOpenPicker(null);
                            }}
                            accessibilityRole="button"
                            accessibilityState={{ selected: active }}
                            style={({ pressed }) => [
                              styles.option,
                              {
                                borderColor: active ? Brand.green : theme.border,
                                backgroundColor: active
                                  ? theme.backgroundSelected
                                  : theme.background,
                              },
                              pressed && styles.pressed,
                            ]}>
                            <ThemedText type="small" style={styles.pickerLabel} numberOfLines={1}>
                              {entry.name}
                            </ThemedText>
                            {active ? <Icon name="verified" size={16} color={Brand.green} /> : null}
                          </Pressable>
                        );
                      })}
                    </ScrollView>
                  ) : null}

                  {sources.length === 0 ? (
                    <ThemedText type="caption" themeColor="textMuted">
                      You have no income sources yet. Add one in the Income section first.
                    </ThemedText>
                  ) : null}
                </>
              )}
            </View>
          );
        })}

        {!valid && drafts.length > 0 ? (
          <ThemedText type="caption" themeColor="textMuted">
            Every movement needs an amount, and an expense needs a category while an income needs
            a source.
          </ThemedText>
        ) : null}

        {drafts.length > 1 ? (
          <ThemedText type="caption" themeColor="textMuted">
            {outCount} out · {inCount} in
          </ThemedText>
        ) : null}

        {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}

        <View style={styles.actions}>
          <Button
            primary
            label={
              saving
                ? 'Adding…'
                : `Add ${drafts.length === 1 ? 'movement' : `${drafts.length} movements`}`
            }
            onPress={save}
            disabled={!valid || saving}
            style={styles.grow}
          />
          <Button
            label="Cancel"
            onPress={onClose}
            secondary
            onCard
            disabled={saving}
            style={styles.grow}
          />
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
    gap: Spacing.two,
  },
  /** Out / In: the same pill pair the ledger switch uses. */
  directionSwitch: {
    flexDirection: 'row',
    gap: 3,
    padding: 3,
    borderWidth: 1,
    borderRadius: 999,
  },
  directionTab: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.half,
    borderRadius: 999,
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
  checkRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingVertical: Spacing.one,
  },
  optionList: {
    maxHeight: 190,
  },
  option: {
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
