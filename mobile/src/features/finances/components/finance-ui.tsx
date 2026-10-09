import { ReactNode, useState } from 'react';
import { StyleSheet, TextInput, TextInputProps, View } from 'react-native';

import { Button, DANGER_COLOR } from '@/components/ui/primitives';
import { ThemedText } from '@/components/themed-text';
import { Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { describeError as describeSharedError } from '@/utils/errors';

// Pieces shared by the Expenses and Incomes screens (S2-TECH-2).

/** Up to 10 digits and 2 decimals, the same limits as the backend. */
const AMOUNT = /^\d{1,10}(\.\d{1,2})?$/;

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Today as YYYY-MM-DD in the phone's own time zone (toISOString would use UTC). */
export function todayIso(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/** Accepts "12,50" as well as "12.50", since many phones type a comma. */
export function parseAmount(text: string): number | null {
  const normalized = text.trim().replace(',', '.');
  if (!AMOUNT.test(normalized)) return null;
  const value = Number(normalized);
  return value > 0 ? value : null;
}

/** Checks the app-side rules for an amount and a date; returns per-field messages. */
export function validateAmountAndDate(amount: string, date: string): Record<string, string> {
  const errors: Record<string, string> = {};
  if (parseAmount(amount) === null) {
    errors.amount = 'Enter an amount greater than 0, with at most 2 decimals';
  }
  if (!ISO_DATE.test(date.trim()) || Number.isNaN(new Date(date.trim()).getTime())) {
    errors.date = 'Use the format YYYY-MM-DD';
  } else if (date.trim() > todayIso()) {
    errors.date = 'Date cannot be in the future';
  }
  return errors;
}

export function formatAmount(amount: number): string {
  return amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

export function formatDate(iso: string): string {
  // Built from parts so the date is not shifted by the time zone.
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString();
}

/** Re-exported so existing finance imports keep working; the wording lives in one place. */
export function describeError(e: unknown): string {
  return describeSharedError(e, { unauthorized: 'Please sign in again.' });
}

export function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <View style={styles.field}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      {children}
      {error && (
        <ThemedText type="small" style={styles.errorText}>
          {error}
        </ThemedText>
      )}
    </View>
  );
}

export function TextField(props: TextInputProps) {
  const theme = useTheme();
  return (
    <TextInput
      placeholderTextColor={theme.textMuted}
      {...props}
      style={[
        styles.input,
        {
          color: theme.text,
          backgroundColor: theme.inputBackground,
          borderColor: theme.border,
        },
        props.style,
      ]}
    />
  );
}

/** Two-step delete that works the same on phone and web: tap, then confirm. */
export function DeleteButton({ onConfirm, busy }: { onConfirm: () => void; busy: boolean }) {
  const [confirming, setConfirming] = useState(false);
  if (!confirming) {
    return <Button label="Delete" onPress={() => setConfirming(true)} secondary onCard />;
  }
  return (
    <View style={styles.row}>
      <Button label={busy ? 'Deleting…' : 'Confirm delete'} onPress={onConfirm} disabled={busy} />
      <Button label="Keep" onPress={() => setConfirming(false)} disabled={busy} secondary onCard />
    </View>
  );
}

export const financeStyles = StyleSheet.create({
  contentContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    flexGrow: 1,
  },
  page: {
    flexGrow: 1,
    maxWidth: 800,
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.four,
  },
  headerRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: Spacing.two,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  amount: {
    fontSize: 20,
    lineHeight: 28,
  },
  dot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  loader: {
    paddingVertical: Spacing.five,
  },
  errorText: {
    color: DANGER_COLOR,
  },
  noticeText: {
    color: Status.success,
  },
});

const styles = StyleSheet.create({
  field: {
    gap: Spacing.one,
  },
  input: {
    borderWidth: 1,
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
    fontSize: 16,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: Spacing.two,
  },
  errorText: {
    color: DANGER_COLOR,
  },
});