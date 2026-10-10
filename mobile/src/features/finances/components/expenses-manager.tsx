import { ReactNode, useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, View } from 'react-native';

import { Button, Chip } from '@/components/ui/primitives';
import {
  DeleteButton,
  Field,
  financeStyles as styles,
  formatAmount,
  formatDate,
  parseAmount,
  TextField,
  todayIso,
  validateAmountAndDate,
} from '@/features/finances/components/finance-ui';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useTheme } from '@/hooks/use-theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { describeError } from '@/utils/errors';
import type { Session } from '@/types/api';
import {
  createExpense,
  deleteExpense,
  Expense,
  fetchExpenses,
  fetchMyCategories,
  MyCategory,
  SaveExpense,
  updateExpense,
} from '@/features/finances/lib/finances-api';

const PAGE_SIZE = 20;

/** null: form closed. 'new': adding. A number: editing that expense. */
type Editing = null | 'new' | number;

/**
 * S2-TECH-2: the signed-in user's expenses - list, add, edit and delete.
 * Each expense is filed under one of the user's own categories (S2-TECH-1).
 */
export function ExpensesManager({
  session,
  contentStyle,
  onSignOut,
  header,
}: {
  session: Session;
  contentStyle: object | undefined;
  onSignOut: (message?: string) => void;
  header: ReactNode;
}) {
  const theme = useTheme();
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editing, setEditing] = useState<Editing>(null);
  const [deletingId, setDeletingId] = useState<number | null>(null);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        // Tokens last 60 minutes and there is no refresh token: sign in again.
        onSignOut('Your session has expired. Please sign in again.');
        return;
      }
      setMessage(describeError(e));
    },
    [onSignOut],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const [list, mine] = await Promise.all([
        fetchExpenses(session, { page: 0, size: PAGE_SIZE }),
        fetchMyCategories(session),
      ]);
      setExpenses(list.content);
      setPage(0);
      setTotalPages(list.page.totalPages);
      setTotal(list.page.totalElements);
      setCategories(mine);
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const next = await fetchExpenses(session, { page: page + 1, size: PAGE_SIZE });
      setExpenses((current) => [...current, ...next.content]);
      setPage(next.page.number);
      setTotalPages(next.page.totalPages);
    } catch (e) {
      handleError(e);
    } finally {
      setLoadingMore(false);
    }
  };

  const onSaved = (created: boolean) => {
    setEditing(null);
    setNotice(created ? 'Expense added.' : 'Expense updated.');
    load();
  };

  const remove = async (expense: Expense) => {
    setDeletingId(expense.id);
    setMessage(null);
    setNotice(null);
    try {
      await deleteExpense(session, expense.id);
      setNotice(`Expense of ${formatAmount(expense.amount)} deleted.`);
      load();
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 404) {
        // Already gone (deleted on another device, or a double tap): the goal is reached, so
        // say so and drop the stale card rather than showing an error.
        setNotice('This expense was already deleted.');
        load();
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

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.contentContainer, contentStyle]}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={loading && expenses.length > 0} onRefresh={load} />}>
      <View style={styles.page}>
        {header}
        <View style={styles.headerRow}>
          <View>
            <ThemedText type="subtitle">Expenses</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Signed in as {session.userName}
            </ThemedText>
          </View>
          <View style={styles.row}>
            <Button label="Refresh" onPress={load} disabled={loading} secondary />
            <Button label="Sign out" onPress={() => onSignOut()} secondary />
          </View>
        </View>

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
          <Button label="Add expense" onPress={() => startEditing('new')} disabled={categories.length === 0} />
        )}

        <ThemedText type="smallBold">My expenses {!loading ? `(${total})` : ''}</ThemedText>

        {loading && expenses.length === 0 && <ActivityIndicator style={styles.loader} />}

        {!loading && expenses.length === 0 && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText themeColor="textSecondary">No expenses yet. Tap “Add expense” to record one.</ThemedText>
          </ThemedView>
        )}

        {expenses.map((expense) =>
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
                  {formatAmount(expense.amount)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatDate(expense.date)}
                </ThemedText>
              </View>
              <View style={styles.row}>
                <View style={[styles.dot, { backgroundColor: expense.category.color ?? theme.textSecondary }]} />
                <ThemedText type="small">{expense.category.name}</ThemedText>
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
  const [amount, setAmount] = useState(expense ? String(expense.amount) : '');
  const [date, setDate] = useState(expense?.date ?? todayIso());
  const [categoryId, setCategoryId] = useState<number | null>(expense?.category.id ?? null);
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
      amount: parseAmount(amount)!,
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
        setFieldErrors(e instanceof ApiRequestError ? e.fieldErrors : {});
        setError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <ThemedText type="smallBold">{expense ? 'Edit expense' : 'New expense'}</ThemedText>

      <Field label="Amount" error={fieldErrors.amount}>
        <TextField placeholder="e.g. 12.50" keyboardType="decimal-pad" value={amount} onChangeText={setAmount} />
      </Field>

      <Field label="Date (YYYY-MM-DD)" error={fieldErrors.date}>
        <TextField placeholder={todayIso()} autoCorrect={false} maxLength={10} value={date} onChangeText={setDate} />
      </Field>

      <Field label="Category" error={fieldErrors.categoryId}>
        <View style={styles.row}>
          {categories.map((c) => (
            <Chip key={c.id} label={c.name} selected={categoryId === c.id} onPress={() => setCategoryId(c.id)} onCard />
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