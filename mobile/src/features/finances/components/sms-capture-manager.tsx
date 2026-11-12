import { ReactNode, useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Button, Chip } from '@/components/ui/primitives';
import type { ToastKind } from '@/components/ui/top-toast';
import { BottomTabInset, Spacing } from '@/constants/theme';
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
  captureSms,
  type CapturedTransaction,
  confirmCapture,
  dismissCapture,
  fetchCaptures,
  type MobileMoneyProvider,
  PROVIDER_LABELS,
} from '@/features/finances/lib/captures-api';
import { fetchMyCategories, type MyCategory, type SaveExpense } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const TYPE_LABELS: Record<CapturedTransaction['type'], string> = {
  PAYMENT: 'Payment',
  TRANSFER: 'Transfer sent',
  WITHDRAWAL: 'Withdrawal',
  UNKNOWN: 'Transaction',
};

/** null means "detect it from the text". */
const PROVIDER_CHOICES: { key: MobileMoneyProvider | null; label: string }[] = [
  { key: null, label: 'Detect' },
  { key: 'MTN_MOMO', label: PROVIDER_LABELS.MTN_MOMO },
  { key: 'ORANGE_MONEY', label: PROVIDER_LABELS.ORANGE_MONEY },
];

/**
 * S6-TECH-2: paste an MTN MoMo or Orange Money SMS, review what was read, and confirm it as an
 * expense or dismiss it.
 *
 * Nothing becomes an expense until the user confirms: a pasted SMS only creates a pending capture,
 * and the review form is where the amount, date, category and description are settled. Fields the
 * SMS did not contain arrive blank and stay blank until the user fills them.
 */
export function SmsCaptureManager({
  session,
  onSignOut,
  onToast,
  header,
}: {
  session: Session;
  onSignOut: (message?: string) => void;
  onToast: (kind: ToastKind, message: string) => void;
  header?: ReactNode;
}) {
  const theme = useTheme();
  const [text, setText] = useState('');
  const [provider, setProvider] = useState<MobileMoneyProvider | null>(null);
  const [reading, setReading] = useState(false);
  const [readError, setReadError] = useState<string | null>(null);

  const [pending, setPending] = useState<CapturedTransaction[]>([]);
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  /** The capture whose review form is open. */
  const [reviewingId, setReviewingId] = useState<string | null>(null);
  const [dismissingId, setDismissingId] = useState<string | null>(null);

  const handleError = useCallback(
    (e: unknown, show: (text: string) => void = setMessage) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        onSignOut('Your session has expired. Please sign in again.');
        return;
      }
      show(describeError(e));
    },
    [onSignOut],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const [captures, myCategories] = await Promise.all([
        fetchCaptures(session, { status: 'PENDING', size: 50 }),
        fetchMyCategories(session, { size: 200 }),
      ]);
      setPending(captures.data);
      setCategories(myCategories.data);
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  const read = async () => {
    if (!text.trim()) {
      setReadError('Paste the text of the SMS first.');
      return;
    }
    setReading(true);
    setReadError(null);
    try {
      const capture = await captureSms(session, text.trim(), provider ?? undefined);
      setText('');
      setPending((current) => [capture, ...current]);
      // Straight to its review: that is what the user pasted it for.
      setReviewingId(capture.id);
    } catch (e) {
      handleError(e, setReadError);
    } finally {
      setReading(false);
    }
  };

  const onConfirmed = (capture: CapturedTransaction) => {
    setPending((current) => current.filter((c) => c.id !== capture.id));
    setReviewingId(null);
    onToast('created', 'Expense added from the SMS.');
  };

  const dismiss = async (capture: CapturedTransaction) => {
    setDismissingId(capture.id);
    setMessage(null);
    try {
      await dismissCapture(session, capture.id);
      setPending((current) => current.filter((c) => c.id !== capture.id));
      if (reviewingId === capture.id) setReviewingId(null);
      onToast('deleted', 'Transaction dismissed. No expense was added.');
    } catch (e) {
      handleError(e);
    } finally {
      setDismissingId(null);
    }
  };

  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: theme.background }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.contentContainer, local.content]}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={loading && pending.length > 0} onRefresh={load} />}>
      <View style={styles.page}>
        {header}

        <ThemedView type="backgroundElement" style={styles.card}>
          <ThemedText type="smallBold">Add an expense from a mobile money SMS</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Copy an MTN MoMo or Orange Money message from your SMS app and paste it below. You will
            check the details before anything is saved.
          </ThemedText>

          <Field label="Service">
            <View style={styles.row}>
              {PROVIDER_CHOICES.map((choice) => (
                <Chip
                  key={choice.label}
                  label={choice.label}
                  selected={provider === choice.key}
                  onPress={() => setProvider(choice.key)}
                  onCard
                />
              ))}
            </View>
          </Field>

          <Field label="SMS text" error={readError ?? undefined}>
            <TextField
              placeholder="Long-press here and choose Paste"
              multiline
              textAlignVertical="top"
              maxLength={2000}
              value={text}
              onChangeText={(value) => {
                setText(value);
                setReadError(null);
              }}
              style={local.smsInput}
            />
          </Field>

          <View style={styles.row}>
            <Button primary label={reading ? 'Reading…' : 'Read SMS'} onPress={read} disabled={reading} />
            {text ? <Button label="Clear" onPress={() => setText('')} disabled={reading} secondary onCard /> : null}
          </View>
        </ThemedView>

        {message && <ThemedText style={styles.errorText}>{message}</ThemedText>}

        <ThemedText type="smallBold">Waiting for review {!loading ? `(${pending.length})` : ''}</ThemedText>

        {loading && pending.length === 0 && <ActivityIndicator style={styles.loader} />}

        {!loading && pending.length === 0 && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText themeColor="textSecondary">Nothing to review. Paste an SMS above to start.</ThemedText>
          </ThemedView>
        )}

        {pending.map((capture) =>
          reviewingId === capture.id ? (
            <ReviewForm
              key={capture.id}
              session={session}
              capture={capture}
              categories={categories}
              onConfirmed={onConfirmed}
              onDismiss={() => dismiss(capture)}
              dismissing={dismissingId === capture.id}
              onCancel={() => setReviewingId(null)}
              onError={handleError}
            />
          ) : (
            <ThemedView key={capture.id} type="backgroundElement" style={styles.card}>
              <CaptureSummary capture={capture} />
              <View style={styles.row}>
                <Button label="Review" onPress={() => setReviewingId(capture.id)} secondary onCard />
                <DismissButton onConfirm={() => dismiss(capture)} busy={dismissingId === capture.id} />
              </View>
            </ThemedView>
          ),
        )}
      </View>
    </ScrollView>
  );
}

/** What was read from the SMS, as read: missing fields are said to be missing. */
function CaptureSummary({ capture }: { capture: CapturedTransaction }) {
  const when = capture.transactionDate
    ? `${formatDate(capture.transactionDate)}${capture.transactionTime ? ` ${capture.transactionTime.slice(0, 5)}` : ''}`
    : 'Date not in the SMS';

  return (
    <View style={local.summary}>
      <View style={styles.headerRow}>
        <ThemedText type="smallBold" style={styles.amount}>
          {formatMoney(capture.amount)}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {PROVIDER_LABELS[capture.provider]} · {TYPE_LABELS[capture.type]}
        </ThemedText>
      </View>
      <ThemedText type="small">{capture.counterparty ?? 'Recipient not in the SMS'}</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        {when}
        {capture.fee ? ` · Fee ${formatMoney(capture.fee)}` : ''}
        {capture.reference ? ` · Ref ${capture.reference}` : ''}
      </ThemedText>
    </View>
  );
}

/** The same tap-then-confirm pattern as Delete, worded for dismissing. */
function DismissButton({ onConfirm, busy }: { onConfirm: () => void; busy: boolean }) {
  const [confirming, setConfirming] = useState(false);
  if (!confirming) {
    return <Button label="Dismiss" onPress={() => setConfirming(true)} secondary onCard />;
  }
  return (
    <View style={styles.row}>
      <Button label={busy ? 'Dismissing…' : 'Confirm dismiss'} onPress={onConfirm} disabled={busy} />
      <Button label="Keep" onPress={() => setConfirming(false)} disabled={busy} secondary onCard />
    </View>
  );
}

/**
 * The review step (US37): the values read from the SMS, editable, plus the category the user must
 * choose. Confirming creates the expense; nothing is saved before that.
 */
function ReviewForm({
  session,
  capture,
  categories,
  onConfirmed,
  onDismiss,
  dismissing,
  onCancel,
  onError,
}: {
  session: Session;
  capture: CapturedTransaction;
  categories: MyCategory[];
  onConfirmed: (capture: CapturedTransaction) => void;
  onDismiss: () => void;
  dismissing: boolean;
  onCancel: () => void;
  onError: (e: unknown, show: (text: string) => void) => void;
}) {
  // The total is what left the wallet, so it is what the expense starts from.
  const [amount, setAmount] = useState(String(capture.total.amount));
  const [currency, setCurrency] = useState(capture.total.currency);
  // Blank when the SMS had no date: the user fills it rather than us assuming today.
  const [date, setDate] = useState(capture.transactionDate ?? '');
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [description, setDescription] = useState(capture.suggestedDescription);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const confirm = async () => {
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
      onConfirmed(await confirmCapture(session, capture.id, body));
    } catch (e) {
      setFieldErrors(e instanceof ApiRequestError ? e.fields : {});
      onError(e, setError);
    } finally {
      setSaving(false);
    }
  };

  const busy = saving || dismissing;

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <ThemedText type="smallBold">Review before adding</ThemedText>
      <CaptureSummary capture={capture} />

      <Field label="Amount" error={moneyFieldError(fieldErrors)}>
        <TextField keyboardType="decimal-pad" value={amount} onChangeText={setAmount} />
        {capture.fee && capture.fee.amount > 0 ? (
          <ThemedText type="small" themeColor="textSecondary">
            Includes the {formatMoney(capture.fee)} fee. Use {formatMoney(capture.amount)} to leave it out.
          </ThemedText>
        ) : null}
      </Field>

      <Field label="Currency">
        <CurrencyPicker value={currency} onChange={setCurrency} />
      </Field>

      <Field label="Date (YYYY-MM-DD)" error={fieldErrors.date}>
        <TextField placeholder={todayIso()} autoCorrect={false} maxLength={10} value={date} onChangeText={setDate} />
        {!capture.transactionDate && !date ? (
          <View style={styles.row}>
            <ThemedText type="small" themeColor="textSecondary">
              The SMS has no date.
            </ThemedText>
            <Chip label="Use today" selected={false} onPress={() => setDate(todayIso())} onCard />
          </View>
        ) : null}
      </Field>

      <Field label="Category" error={fieldErrors.categoryId}>
        <View style={styles.row}>
          {categories.map((c) => (
            <Chip key={c.id} label={c.name} selected={categoryId === c.id} onPress={() => setCategoryId(c.id)} onCard />
          ))}
        </View>
      </Field>

      <Field label="Description (optional)" error={fieldErrors.description}>
        <TextField maxLength={255} value={description} onChangeText={setDescription} />
      </Field>

      {error && <ThemedText style={styles.errorText}>{error}</ThemedText>}

      <View style={styles.row}>
        <Button primary label={saving ? 'Adding…' : 'Confirm expense'} onPress={confirm} disabled={busy} />
        <DismissButton onConfirm={onDismiss} busy={dismissing} />
        <Button label="Later" onPress={onCancel} disabled={busy} secondary onCard />
      </View>
    </ThemedView>
  );
}

const local = StyleSheet.create({
  content: {
    paddingBottom: BottomTabInset + Spacing.four,
  },
  smsInput: {
    minHeight: 120,
  },
  summary: {
    gap: Spacing.one,
  },
});
