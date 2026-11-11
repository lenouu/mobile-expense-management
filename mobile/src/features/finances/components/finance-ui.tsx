import { ReactNode, useState } from 'react';
import { StyleSheet, TextInput, TextInputProps, View } from 'react-native';

import { Button, Chip, DANGER_COLOR } from '@/components/ui/primitives';
import { ThemedText } from '@/components/themed-text';
import { Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { DEFAULT_CURRENCY, type Money } from '@/types/api';
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

/** Currencies the app offers. The backend accepts any ISO 4217 code. */
export const SUPPORTED_CURRENCIES = [DEFAULT_CURRENCY, 'EUR', 'USD'] as const;

function formatAmount(amount: number): string {
  return amount.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

/**
 * Renders a money object as e.g. "12,500.00 XAF".
 *
 * The code is shown next to the number rather than as a glyph, because XAF has no widely
 * supported symbol. A bare number is still tolerated, so an older payload renders instead of
 * crashing the list.
 */
export function formatMoney(money: Money | number | null | undefined): string {
  if (money === null || money === undefined) return '—';
  if (typeof money === 'number') return `${formatAmount(money)} ${DEFAULT_CURRENCY}`;
  return `${formatAmount(money.amount)} ${money.currency || DEFAULT_CURRENCY}`;
}

/** Builds the request body's money object from what the user typed, or null when invalid. */
export function toMoney(text: string, currency: string): Money | null {
  const value = parseAmount(text);
  return value === null ? null : { amount: value, currency: currency || DEFAULT_CURRENCY };
}

/**
 * The message for the amount field.
 *
 * The backend validates money as a nested object, so it reports the failure at `amount.amount`;
 * our own client-side checks use the plain key. Both are accepted so the message lands under the
 * input either way.
 */
export function moneyFieldError(fields: Record<string, string>): string | undefined {
  return fields['amount.amount'] ?? fields.amount;
}

export function formatDate(iso: string): string {
  // Built from parts so the date is not shifted by the time zone.
  const [year, month, day] = iso.split('-').map(Number);
  return new Date(year, month - 1, day).toLocaleDateString();
}

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/**
 * "Fri 2 May 2026" - the date spelled out.
 *
 * The numeric form is fine inside a dense row, but a day header has room to say which day it was,
 * and a month written as a word is unambiguous where 02/05 is not: the same digits mean February
 * in one convention and May in another.
 */
export function formatLongDate(iso: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  if (!year || !month || !day) return iso;

  const date = new Date(year, month - 1, day);
  return `${WEEKDAYS[date.getDay()]} ${day} ${MONTHS[month - 1]} ${year}`;
}

/** "May 2026", for a calendar's heading. */
export function formatMonthYear(year: number, month: number): string {
  return `${MONTHS[month]} ${year}`;
}

/** "a, b and c" - the way a list of durations is written in a sentence. */
function joinParts(parts: string[]): string {
  if (parts.length <= 1) return parts.join('');
  return `${parts.slice(0, -1).join(', ')} and ${parts[parts.length - 1]}`;
}

/**
 * How long a window is, in words: "22 days", "1 month and 2 days", "1 year and 3 months".
 *
 * Weeks are deliberately absent: "3 weeks" is read as a month by everyone who has to convert it
 * back, so the units here are the two that span everything between - days, then months, then years.
 * Months are counted as thirty days and years as twelve of those, which is a description of the
 * window rather than a second copy of it; the exact dates are always shown beside the sentence.
 */
export function describeDuration(days: number): string {
  if (days <= 0) return '0 days';
  if (days < 30) return days === 1 ? '1 day' : `${days} days`;

  const years = Math.floor(days / 365);
  const afterYears = days - years * 365;
  const months = Math.floor(afterYears / 30);
  const leftoverDays = afterYears - months * 30;

  const parts: string[] = [];
  if (years > 0) parts.push(years === 1 ? '1 year' : `${years} years`);
  if (months > 0) parts.push(months === 1 ? '1 month' : `${months} months`);
  if (leftoverDays > 0) parts.push(leftoverDays === 1 ? '1 day' : `${leftoverDays} days`);

  return joinParts(parts);
}

/** Whole days from one `YYYY-MM-DD` to another, inclusive of both ends. */
export function daysBetween(from: string, to: string): number {
  const [fromYear, fromMonth, fromDay] = from.split('-').map(Number);
  const [toYear, toMonth, toDay] = to.split('-').map(Number);
  if (!fromYear || !toYear) return 0;

  // UTC so a daylight-saving change cannot turn a 22-day window into 21 or 23.
  const start = Date.UTC(fromYear, fromMonth - 1, fromDay);
  const end = Date.UTC(toYear, toMonth - 1, toDay);
  return Math.round((end - start) / 86_400_000) + 1;
}

/** Re-exported so existing finance imports keep working; the wording lives in one place. */
/**
 * "Edited 10/10/2026" when a record was changed after it was created (US14), otherwise null.
 * The backend stamps both times identically on create and only moves updatedAt on an edit.
 */
export function editedLabel(record: { createdAt: string; updatedAt: string }): string | null {
  if (record.updatedAt === record.createdAt) return null;
  // The backend sends local date-times without an offset, which JS also reads as local time.
  return `Edited ${new Date(record.updatedAt).toLocaleDateString()}`;
}

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

/** A small currency selector; XAF is what the backend assumes when a client omits it. */
export function CurrencyPicker({
  value,
  onChange,
}: {
  value: string;
  onChange: (code: string) => void;
}) {
  return (
    <View style={styles.row}>
      {SUPPORTED_CURRENCIES.map((code) => (
        <Chip key={code} label={code} selected={value === code} onPress={() => onChange(code)} onCard />
      ))}
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