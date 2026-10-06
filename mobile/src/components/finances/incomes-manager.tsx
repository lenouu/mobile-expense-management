import { ReactNode, useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, View } from 'react-native';

import { Button, Chip } from '@/components/admin/admin-ui';
import {
  DeleteButton,
  describeError,
  Field,
  financeStyles as styles,
  formatAmount,
  formatDate,
  parseAmount,
  TextField,
  todayIso,
  validateAmountAndDate,
} from '@/components/finances/finance-ui';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useTheme } from '@/hooks/use-theme';
import { ApiRequestError, Session } from '@/services/api-client';
import { createIncome, deleteIncome, fetchIncomes, Income, SaveIncome, updateIncome } from '@/services/finances';

const PAGE_SIZE = 20;

/** Quick picks for the source field; any text can still be typed. */
const SOURCE_PRESETS = ['Salary', 'Business', 'Freelance', 'Gift', 'Other'];

/** null: form closed. 'new': adding. A number: editing that income. */
type Editing = null | 'new' | number;

/**
 * S2-TECH-2: the signed-in user's incomes - list, add, edit and delete.
 */
export function IncomesManager({
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
  const [incomes, setIncomes] = useState<Income[]>([]);
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
      const list = await fetchIncomes(session, { page: 0, size: PAGE_SIZE });
      setIncomes(list.content);
      setPage(0);
      setTotalPages(list.page.totalPages);
      setTotal(list.page.totalElements);
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
      const next = await fetchIncomes(session, { page: page + 1, size: PAGE_SIZE });
      setIncomes((current) => [...current, ...next.content]);
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
    setNotice(created ? 'Income added.' : 'Income updated.');
    load();
  };

  const remove = async (income: Income) => {
    setDeletingId(income.id);
    setMessage(null);
    setNotice(null);
    try {
      await deleteIncome(session, income.id);
      setNotice('Income deleted.');
      load();
    } catch (e) {
      handleError(e);
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
      refreshControl={<RefreshControl refreshing={loading && incomes.length > 0} onRefresh={load} />}>
      <View style={styles.page}>
        {header}
        <View style={styles.headerRow}>
          <View>
            <ThemedText type="subtitle">Incomes</ThemedText>
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
          <IncomeForm
            session={session}
            onSaved={() => onSaved(true)}
            onCancel={() => setEditing(null)}
            onAuthError={handleError}
          />
        ) : (
          <Button label="Add income" onPress={() => startEditing('new')} />
        )}

        <ThemedText type="smallBold">My incomes {!loading ? `(${total})` : ''}</ThemedText>

        {loading && incomes.length === 0 && <ActivityIndicator style={styles.loader} />}

        {!loading && incomes.length === 0 && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText themeColor="textSecondary">No incomes yet. Tap “Add income” to record one.</ThemedText>
          </ThemedView>
        )}

        {incomes.map((income) =>
          editing === income.id ? (
            <IncomeForm
              key={income.id}
              session={session}
              income={income}
              onSaved={() => onSaved(false)}
              onCancel={() => setEditing(null)}
              onAuthError={handleError}
            />
          ) : (
            <ThemedView key={income.id} type="backgroundElement" style={styles.card}>
              <View style={styles.headerRow}>
                <ThemedText type="smallBold" style={styles.amount}>
                  {formatAmount(income.amount)}
                </ThemedText>
                <ThemedText type="small" themeColor="textSecondary">
                  {formatDate(income.date)}
                </ThemedText>
              </View>
              <ThemedText type="small">{income.source}</ThemedText>
              {income.description && (
                <ThemedText type="small" themeColor="textSecondary">
                  {income.description}
                </ThemedText>
              )}
              <View style={styles.row}>
                <Button label="Edit" onPress={() => startEditing(income.id)} secondary onCard />
                <DeleteButton onConfirm={() => remove(income)} busy={deletingId === income.id} />
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

/** Add form when `income` is absent, edit form otherwise. */
function IncomeForm({
  session,
  income,
  onSaved,
  onCancel,
  onAuthError,
}: {
  session: Session;
  income?: Income;
  onSaved: () => void;
  onCancel: () => void;
  onAuthError: (e: unknown) => void;
}) {
  const [amount, setAmount] = useState(income ? String(income.amount) : '');
  const [date, setDate] = useState(income?.date ?? todayIso());
  const [source, setSource] = useState(income?.source ?? '');
  const [description, setDescription] = useState(income?.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    // Same rules as the backend, checked first so the user gets instant feedback.
    const errors = validateAmountAndDate(amount, date);
    if (!source.trim()) errors.source = 'Enter where the money came from';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const body: SaveIncome = {
      amount: parseAmount(amount)!,
      date: date.trim(),
      source: source.trim(),
      description: description.trim() || undefined,
    };

    setSaving(true);
    setError(null);
    try {
      if (income) {
        await updateIncome(session, income.id, body);
      } else {
        await createIncome(session, body);
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
      <ThemedText type="smallBold">{income ? 'Edit income' : 'New income'}</ThemedText>

      <Field label="Amount" error={fieldErrors.amount}>
        <TextField placeholder="e.g. 1500" keyboardType="decimal-pad" value={amount} onChangeText={setAmount} />
      </Field>

      <Field label="Date (YYYY-MM-DD)" error={fieldErrors.date}>
        <TextField placeholder={todayIso()} autoCorrect={false} maxLength={10} value={date} onChangeText={setDate} />
      </Field>

      <Field label="Source" error={fieldErrors.source}>
        <View style={styles.row}>
          {SOURCE_PRESETS.map((preset) => (
            <Chip key={preset} label={preset} selected={source === preset} onPress={() => setSource(preset)} onCard />
          ))}
        </View>
        <TextField placeholder="e.g. Salary" maxLength={100} value={source} onChangeText={setSource} />
      </Field>

      <Field label="Description (optional)" error={fieldErrors.description}>
        <TextField placeholder="Any detail" maxLength={255} value={description} onChangeText={setDescription} />
      </Field>

      {error && <ThemedText style={styles.errorText}>{error}</ThemedText>}

      <View style={styles.row}>
        <Button label={saving ? 'Saving…' : 'Save'} onPress={save} disabled={saving} />
        <Button label="Cancel" onPress={onCancel} disabled={saving} secondary onCard />
      </View>
    </ThemedView>
  );
}
