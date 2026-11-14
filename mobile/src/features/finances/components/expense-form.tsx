import { useEffect, useState } from 'react';
import { Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Calendar } from '@/components/ui/calendar';
import { Icon } from '@/components/ui/icon';
import { Button } from '@/components/ui/primitives';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import {
  DeleteButton,
  financeStyles,
  formatLongDate,
  moneyFieldError,
  TextField,
  todayIso,
  toMoney,
  validateAmountAndDate,
} from '@/features/finances/components/finance-ui';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import type { Expense, MyCategory, SaveExpense } from '@/features/finances/lib/finances-api';
import { createExpense, updateExpense } from '@/features/finances/lib/finances-api';
import { fetchBudgets } from '@/features/finances/lib/budget-api';
import { useTheme } from '@/hooks/use-theme';
import { DEFAULT_CURRENCY, type Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_CATEGORY_COLOR = '#01916D';

/**
 * The add/edit expense form, without a surface of its own.
 *
 * It is shared by the Transactions segment (inside a pop-up) and by the older Money screen (inside
 * a card), so it draws only its contents. The ordering is the order the question is asked in -
 * how much, when, on what, and then anything worth remembering - with the amount set as the
 * largest thing in the form because it is the only field that cannot be guessed from context.
 *
 * Date and category are pickers rather than text: a typed "2026-13-45" is a rejection the user has
 * to absorb, while a calendar cannot offer it in the first place.
 */
export function ExpenseForm({
  session,
  categories,
  expense,
  onSaved,
  onCancel,
  onAuthError,
  onDelete,
  deleting = false,
  onImportCsv,
  onScanReceipt,
}: {
  session: Session;
  categories: MyCategory[];
  /** Present when editing, absent when adding. */
  expense?: Expense | null;
  onSaved: (saved: Expense, created: boolean) => void;
  onCancel: () => void;
  onAuthError: (e: unknown) => void;
  /** Shown inside the form when editing; deleting is a two-step action. */
  onDelete?: () => void;
  deleting?: boolean;
  /** Adding many at once is an alternative to this form, so it is offered from here. */
  onImportCsv?: () => void;
  /**
   * The picture route: hands over to the camera/gallery chooser.
   *
   * It is one affordance rather than two because "take a photo" and "choose a photo" are the
   * same intention - add an expense from a picture - and the how is answered in its own pop-up.
   */
  onScanReceipt?: () => void;
}) {
  const theme = useTheme();

  const [amount, setAmount] = useState(expense ? String(expense.amount.amount) : '');
  // One unit per account (chosen in Settings): a picker here would let a second currency into a
  // sum that has no rate to convert it.
  const currency = session.baseCurrency || DEFAULT_CURRENCY;
  const [date, setDate] = useState(expense?.date ?? todayIso());
  const [categoryId, setCategoryId] = useState<string | null>(expense?.category.id ?? null);
  const [budgetId, setBudgetId] = useState<string | null>(expense?.budget?.id ?? null);
  const [budgets, setBudgets] = useState<
    { id: string; name: string | null; startDate: string; endDate: string }[]
  >([]);
  const [description, setDescription] = useState(expense?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  // Only one of the two pickers is open at a time: they occupy the same space under their row.
  const [openPicker, setOpenPicker] = useState<'date' | 'category' | 'budget' | null>(null);

  // Only the budgets that could accept this expense: running or upcoming, and covering its date.
  useEffect(() => {
    let cancelled = false;
    fetchBudgets(session)
      .then((list) => {
        if (!cancelled) {
          setBudgets(list.filter((entry) => entry.status !== 'CLOSED'));
        }
      })
      .catch(() => {
        // A budget list that will not load must not stop an expense being recorded.
      });
    return () => {
      cancelled = true;
    };
  }, [session]);

  const selected = categories.find((category) => category.id === categoryId) ?? null;
  const isEdit = Boolean(expense);

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
      budgetId: budgetId ?? undefined,
    };

    setSaving(true);
    setError(null);
    try {
      const saved = expense
        ? await updateExpense(session, expense.id, body)
        : await createExpense(session, body);

      onSaved(saved, !expense);
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
    <View style={styles.shell}>
      <View style={styles.header}>
        <ThemedText type="hero" style={styles.headerTitle}>
          {isEdit ? 'Edit expense' : 'New expense'}
        </ThemedText>

        <Pressable
          onPress={onCancel}
          accessibilityRole="button"
          accessibilityLabel="Close without saving"
          hitSlop={10}
          style={({ pressed }) => [styles.closeButton, pressed && styles.pressed]}>
          <Icon name="close" size={20} color={theme.textSecondary} />
        </Pressable>
      </View>

      {/* How much: the only field that is set large, because it is the one being answered. */}
      <View style={styles.block}>
        <ThemedText type="small" themeColor="textSecondary">
          Amount
        </ThemedText>

        <View style={styles.amountRow}>
          <TextField
            placeholder="0.00"
            keyboardType="decimal-pad"
            value={amount}
            onChangeText={setAmount}
            style={styles.amountInput}
            accessibilityLabel={`Amount in ${currency}`}
          />

          <ThemedText type="hero" themeColor="textMuted">
            {currency}
          </ThemedText>
        </View>

        {moneyFieldError(fieldErrors) ? (
          <ThemedText type="small" style={financeStyles.errorText}>
            {moneyFieldError(fieldErrors)}
          </ThemedText>
        ) : null}
      </View>

      {/* When */}
      <View style={styles.block}>
        <ThemedText type="small" themeColor="textSecondary">
          Date
        </ThemedText>

        <Pressable
          onPress={() => setOpenPicker(openPicker === 'date' ? null : 'date')}
          accessibilityRole="button"
          accessibilityLabel={`Date ${formatLongDate(date)}. Change it.`}
          accessibilityState={{ expanded: openPicker === 'date' }}
          style={({ pressed }) => [
            styles.pickerRow,
            {
              borderColor: openPicker === 'date' ? Brand.green : theme.border,
              backgroundColor: theme.inputBackground,
            },
            pressed && styles.pressed,
          ]}>
          <Icon name="clock" size={18} color={theme.textSecondary} />
          <ThemedText type="subtitle" style={styles.pickerLabel}>
            {formatLongDate(date)}
          </ThemedText>
          <Icon
            name={openPicker === 'date' ? 'chevronUp' : 'chevronDown'}
            size={18}
            color={theme.textSecondary}
          />
        </Pressable>

        {openPicker === 'date' ? (
          <Calendar
            value={date}
            max={todayIso()}
            onSelect={(iso) => {
              setDate(iso);
              setOpenPicker(null);
            }}
          />
        ) : null}

        {fieldErrors.date ? (
          <ThemedText type="small" style={financeStyles.errorText}>
            {fieldErrors.date}
          </ThemedText>
        ) : null}
      </View>

      {/* On what */}
      <View style={styles.block}>
        <ThemedText type="small" themeColor="textSecondary">
          Category
        </ThemedText>

        <Pressable
          onPress={() => setOpenPicker(openPicker === 'category' ? null : 'category')}
          accessibilityRole="button"
          accessibilityLabel={selected ? `Category ${selected.name}. Change it.` : 'Choose a category'}
          accessibilityState={{ expanded: openPicker === 'category' }}
          style={({ pressed }) => [
            styles.pickerRow,
            {
              borderColor: openPicker === 'category' ? Brand.green : theme.border,
              backgroundColor: theme.inputBackground,
            },
            pressed && styles.pressed,
          ]}>
          {selected ? (
            <CategoryBadge
              icon={selected.icon}
              color={selected.color ?? DEFAULT_CATEGORY_COLOR}
              size={26}
            />
          ) : (
            <Icon name="categories" size={18} color={theme.textSecondary} />
          )}

          <ThemedText
            type="subtitle"
            themeColor={selected ? 'text' : 'textMuted'}
            style={styles.pickerLabel}
            numberOfLines={1}>
            {selected ? selected.name : 'Choose a category'}
          </ThemedText>

          <Icon
            name={openPicker === 'category' ? 'chevronUp' : 'chevronDown'}
            size={18}
            color={theme.textSecondary}
          />
        </Pressable>

        {openPicker === 'category' ? (
          <ScrollView style={styles.categoryList} nestedScrollEnabled keyboardShouldPersistTaps="handled">
            {categories.map((category) => {
              const active = category.id === categoryId;

              return (
                <Pressable
                  key={category.id}
                  onPress={() => {
                    setCategoryId(category.id);
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
                    icon={category.icon}
                    color={category.color ?? DEFAULT_CATEGORY_COLOR}
                    size={24}
                  />
                  <ThemedText type="small" style={styles.pickerLabel} numberOfLines={1}>
                    {category.name}
                  </ThemedText>
                  {active ? <Icon name="verified" size={18} color={Brand.green} /> : null}
                </Pressable>
              );
            })}
          </ScrollView>
        ) : null}

        {fieldErrors.categoryId ? (
          <ThemedText type="small" style={financeStyles.errorText}>
            {fieldErrors.categoryId}
          </ThemedText>
        ) : null}
      </View>

      {/* Counting against a budget is a choice, not a side effect: the ledger takes everything,
          a budget only sees what is put under it. */}
      <View style={styles.block}>
        <ThemedText type="small" themeColor="textSecondary">
          Budget (optional)
        </ThemedText>

        <Pressable
          onPress={() => setOpenPicker(openPicker === 'budget' ? null : 'budget')}
          accessibilityRole="button"
          accessibilityLabel={
            budgetId ? 'Change the budget this expense counts against' : 'Count this against a budget'
          }
          accessibilityState={{ expanded: openPicker === 'budget' }}
          style={({ pressed }) => [
            styles.pickerRow,
            {
              borderColor: openPicker === 'budget' ? Brand.green : theme.border,
              backgroundColor: theme.inputBackground,
            },
            pressed && styles.pressed,
          ]}>
          <Icon name="wallet" size={18} color={theme.textSecondary} />

          <ThemedText
            type="small"
            themeColor={budgetId ? 'text' : 'textMuted'}
            style={styles.pickerLabel}
            numberOfLines={1}>
            {(() => {
              const chosen = budgets.find((entry) => entry.id === budgetId);
              if (!chosen) return 'Not under a budget';
              return chosen.name ?? `${formatLongDate(chosen.startDate)} – ${formatLongDate(chosen.endDate)}`;
            })()}
          </ThemedText>

          <Icon
            name={openPicker === 'budget' ? 'chevronUp' : 'chevronDown'}
            size={18}
            color={theme.textSecondary}
          />
        </Pressable>

        {openPicker === 'budget' ? (
          <View style={styles.budgetList}>
            <Pressable
              onPress={() => {
                setBudgetId(null);
                setOpenPicker(null);
              }}
              accessibilityRole="button"
              accessibilityLabel="Do not count this against a budget"
              style={({ pressed }) => [
                styles.budgetOption,
                { borderColor: budgetId === null ? Brand.green : theme.border },
                pressed && styles.pressed,
              ]}>
              <ThemedText type="small" style={styles.pickerLabel}>
                Not under a budget
              </ThemedText>
              {budgetId === null ? <Icon name="verified" size={18} color={Brand.green} /> : null}
            </Pressable>

            {/* Only the budgets that could take this expense: their window has to hold its date. */}
            {budgets
              .filter((entry) => expense?.budget?.id === entry.id || (date >= entry.startDate && date <= entry.endDate))
              .map((entry) => {
                const active = entry.id === budgetId;
                const label = entry.name ?? `${formatLongDate(entry.startDate)} – ${formatLongDate(entry.endDate)}`;

                return (
                  <Pressable
                    key={entry.id}
                    onPress={() => {
                      setBudgetId(entry.id);
                      setOpenPicker(null);
                    }}
                    accessibilityRole="button"
                    accessibilityLabel={`Count this against ${label}`}
                    style={({ pressed }) => [
                      styles.budgetOption,
                      { borderColor: active ? Brand.green : theme.border },
                      pressed && styles.pressed,
                    ]}>
                    <ThemedText type="small" style={styles.pickerLabel} numberOfLines={1}>
                      {label}
                    </ThemedText>
                    {active ? <Icon name="verified" size={18} color={Brand.green} /> : null}
                  </Pressable>
                );
              })}

            {budgets.filter((entry) => date >= entry.startDate && date <= entry.endDate).length === 0 ? (
              <ThemedText type="caption" themeColor="textMuted">
                No budget covers {formatLongDate(date)}. Recording normally is fine.
              </ThemedText>
            ) : null}
          </View>
        ) : null}
      </View>

      {/* Anything worth remembering */}
      <View style={styles.block}>
        <ThemedText type="small" themeColor="textSecondary">
          Note (optional)
        </ThemedText>

        <TextField
          placeholder="What was it for?"
          maxLength={255}
          value={description}
          onChangeText={setDescription}
          accessibilityLabel="Note"
        />

        {fieldErrors.description ? (
          <ThemedText type="small" style={financeStyles.errorText}>
            {fieldErrors.description}
          </ThemedText>
        ) : null}
      </View>

      {/* One picture entry, not two. "Take a photo" and "Choose a photo" were the same
          intention asked twice; the how is now answered in its own pop-up. */}
      {!isEdit && onScanReceipt ? (
        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            Or add it from a picture
          </ThemedText>

          <Pressable
            onPress={onScanReceipt}
            accessibilityRole="button"
            accessibilityLabel="Add an expense with a picture"
            style={({ pressed }) => [
              styles.scanTile,
              { borderColor: theme.border, backgroundColor: theme.inputBackground },
              pressed && styles.pressed,
            ]}>
            <Icon name="camera" size={22} color={Brand.green} />

            <View style={styles.scanCopy}>
              <ThemedText type="smallBold">Add an expense with a picture</ThemedText>
              <ThemedText type="caption" themeColor="textMuted">
                Take a photo or upload one — we will read the expenses from it.
              </ThemedText>
            </View>

            <Icon name="chevronRight" size={18} color={theme.textSecondary} />
          </Pressable>
        </View>
      ) : null}

      {error ? <ThemedText style={financeStyles.errorText}>{error}</ThemedText> : null}

      {/* Adding a whole file of expenses is the other way to do this, so it belongs beside Save
          rather than buried in a menu. */}
      {onImportCsv ? (
        <Pressable
          onPress={onImportCsv}
          accessibilityRole="button"
          accessibilityLabel="Import expenses from a CSV file instead"
          style={({ pressed }) => [styles.importRow, pressed && styles.pressed]}>
          <Icon name="document" size={18} color={theme.textSecondary} />
          <ThemedText type="small" themeColor="textSecondary" style={styles.importLabel}>
            Import several expenses from a CSV file
          </ThemedText>
          <Icon name="chevronRight" size={16} color={theme.textSecondary} />
        </Pressable>
      ) : null}

      <View style={styles.footer}>
        <Button
          primary
          label={saving ? 'Saving…' : isEdit ? 'Save changes' : 'Add expense'}
          onPress={save}
          disabled={saving}
          style={styles.stretch}
        />

        <View style={styles.footerRow}>
          <Button
            label="Cancel"
            onPress={onCancel}
            disabled={saving}
            secondary
            onCard
            style={styles.flex}
          />
          {onDelete ? (
            <View style={styles.flex}>
              <DeleteButton onConfirm={onDelete} busy={deleting} />
            </View>
          ) : null}
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  shell: {
    gap: Spacing.three,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  headerTitle: {
    flex: 1,
  },
  closeButton: {
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  block: {
    gap: Spacing.two,
  },
  /** The amount and its unit on one line: the unit reads as part of the number. */
  amountRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  /** The single picture entry: an icon, what it does, and a chevron into the chooser. */
  scanTile: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.three,
  },
  scanCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  amountInput: {
    flex: 1,
    fontSize: 30,
    lineHeight: 38,
    fontWeight: '700',
    letterSpacing: -0.5,
    textAlign: 'left',
    minHeight: 56,
  },
  pickerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
    minHeight: 48,
  },
  /** The budget choices, kept inline so the form does not open a third layer. */
  budgetList: {
    gap: Spacing.one,
  },
  budgetOption: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  pickerLabel: {
    flex: 1,
  },
  /** The CSV route out of this form: a quiet row, not a third button competing with Save. */
  importRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingVertical: Spacing.two,
  },
  importLabel: {
    flex: 1,
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
  footer: {
    gap: Spacing.two,
    marginTop: Spacing.one,
  },
  footerRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  stretch: {
    alignSelf: 'stretch',
  },
  flex: {
    flex: 1,
  },
  pressed: {
    opacity: 0.7,
  },
});
