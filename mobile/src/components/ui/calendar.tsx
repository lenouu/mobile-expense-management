import { useMemo, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { formatMonthYear } from '@/features/finances/components/finance-ui';
import { useTheme } from '@/hooks/use-theme';

/** Monday first: the week that starts on Monday is the convention the product's dates use. */
const WEEKDAY_LABELS = ['Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa', 'Su'];

function pad(n: number): string {
  return String(n).padStart(2, '0');
}

function toIso(year: number, month: number, day: number): string {
  return `${year}-${pad(month + 1)}-${pad(day)}`;
}

function todayIsoLocal(): string {
  const now = new Date();
  return toIso(now.getFullYear(), now.getMonth(), now.getDate());
}

/** Days laid out Monday-first, with nulls padding the first and last weeks. */
function buildGrid(year: number, month: number): (number | null)[] {
  const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7; // Monday = 0
  const daysInMonth = new Date(year, month + 1, 0).getDate();

  const cells: (number | null)[] = [];
  for (let i = 0; i < firstWeekday; i++) cells.push(null);
  for (let day = 1; day <= daysInMonth; day++) cells.push(day);
  while (cells.length % 7 !== 0) cells.push(null);

  return cells;
}

/**
 * A month grid for picking one date, with its own month navigation.
 *
 * Used inside a drop-down, so it is deliberately compact: no text field, no time, no range - a
 * single day, one tap. `min`/`max` grey out days that would make no sense (a future expense, or a
 * "to" date before the "from" date), which is cheaper than validating afterwards.
 */
export function Calendar({
  value,
  onSelect,
  min,
  max,
}: {
  /** Selected day, `YYYY-MM-DD`. */
  value?: string;
  onSelect: (iso: string) => void;
  /** Earliest selectable day, inclusive. */
  min?: string;
  /** Latest selectable day, inclusive. */
  max?: string;
}) {
  const theme = useTheme();

  const initial = value ?? todayIsoLocal();
  const [year, setYear] = useState(() => Number(initial.slice(0, 4)));
  const [month, setMonth] = useState(() => Number(initial.slice(5, 7)) - 1);

  const grid = useMemo(() => buildGrid(year, month), [year, month]);
  const today = todayIsoLocal();

  const shift = (delta: number) => {
    const next = new Date(year, month + delta, 1);
    setYear(next.getFullYear());
    setMonth(next.getMonth());
  };

  const isDisabled = (iso: string) =>
    (min !== undefined && iso < min) || (max !== undefined && iso > max);

  return (
    <View style={[styles.frame, { borderColor: theme.border, backgroundColor: theme.background }]}>
      <View style={styles.header}>
        <Pressable
          onPress={() => shift(-1)}
          accessibilityRole="button"
          accessibilityLabel="Previous month"
          hitSlop={8}
          style={({ pressed }) => [styles.arrow, pressed && styles.pressed]}>
          <Icon name="chevronLeft" size={18} color={theme.textSecondary} />
        </Pressable>

        <ThemedText type="smallBold">{formatMonthYear(year, month)}</ThemedText>

        <Pressable
          onPress={() => shift(1)}
          accessibilityRole="button"
          accessibilityLabel="Next month"
          hitSlop={8}
          style={({ pressed }) => [styles.arrow, pressed && styles.pressed]}>
          <Icon name="chevronRight" size={18} color={theme.textSecondary} />
        </Pressable>
      </View>

      <View style={styles.weekRow}>
        {WEEKDAY_LABELS.map((label) => (
          <ThemedText key={label} type="caption" themeColor="textMuted" style={styles.weekCell}>
            {label}
          </ThemedText>
        ))}
      </View>

      <View style={styles.grid}>
        {grid.map((day, index) => {
          if (day === null) {
            return <View key={`blank-${index}`} style={styles.cell} />;
          }

          const iso = toIso(year, month, day);
          const selected = iso === value;
          const disabled = isDisabled(iso);
          const isToday = iso === today;

          return (
            <Pressable
              key={iso}
              onPress={() => onSelect(iso)}
              disabled={disabled}
              accessibilityRole="button"
              accessibilityLabel={iso}
              accessibilityState={{ selected, disabled }}
              style={({ pressed }) => [
                styles.cell,
                styles.day,
                selected && { backgroundColor: Brand.green },
                !selected && isToday && { borderColor: Brand.green, borderWidth: 1 },
                (pressed || disabled) && styles.pressed,
              ]}>
              <ThemedText
                type="small"
                style={[
                  selected && styles.daySelected,
                  disabled && styles.dayDisabled,
                  !disabled && !selected && isToday && styles.dayToday,
                ]}>
                {day}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  frame: {
    borderWidth: 1,
    borderRadius: Radius.field,
    padding: Spacing.two,
    gap: Spacing.two,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  arrow: {
    width: 32,
    height: 32,
    alignItems: 'center',
    justifyContent: 'center',
  },
  weekRow: {
    flexDirection: 'row',
  },
  weekCell: {
    flex: 1,
    textAlign: 'center',
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  cell: {
    width: `${100 / 7}%`,
    aspectRatio: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  day: {
    borderRadius: 8,
  },
  daySelected: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  dayToday: {
    color: Brand.green,
    fontWeight: '700',
  },
  dayDisabled: {
    opacity: 0.35,
  },
  pressed: {
    opacity: 0.6,
  },
});
