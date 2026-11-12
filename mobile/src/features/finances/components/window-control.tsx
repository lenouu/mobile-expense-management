import { useMemo, useState } from 'react';
import { Animated, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Calendar } from '@/components/ui/calendar';
import { Icon } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { formatLongDate } from '@/features/finances/components/finance-ui';
import type { FinancialPeriod } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';

/** The four windows the app offers. `custom` needs two dates before it applies. */
export type WindowKey = 'period' | 'week' | 'month' | 'custom';

export type WindowValue = {
  key: WindowKey;
  /** Only meaningful for {@link WindowKey.custom}. */
  from?: string;
  to?: string;
};

const OPTIONS: { key: WindowKey; label: string }[] = [
  { key: 'period', label: 'This period' },
  { key: 'month', label: 'This month so far' },
  { key: 'week', label: 'Last 7 days' },
  { key: 'custom', label: 'Custom range' },
];

function iso(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function daysAgo(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() - days);
  return iso(date);
}

function startOfMonth(): string {
  const now = new Date();
  return iso(new Date(now.getFullYear(), now.getMonth(), 1));
}

function todayIso(): string {
  return iso(new Date());
}

/**
 * The window selector, shared by every screen that looks at a range of expenses.
 *
 * One control that names the window and its dates, and one pop-up that offers the presets with the
 * range each would cover, plus a two-calendar custom range. Sharing it is the point: the Overview
 * and the Transactions segment have to describe the same days, or the numbers disagree.
 */
export function WindowControl({
  period,
  value,
  onChange,
}: {
  /** The current financial period, for the first preset's label. */
  period: FinancialPeriod | null;
  value: WindowValue;
  /** Called once a window is chosen, with the dates it resolves to. */
  onChange: (next: WindowValue & { from: string; to: string }) => void;
}) {
  const theme = useTheme();

  const [listOpen, setListOpen] = useState(false);
  const [customOpen, setCustomOpen] = useState(false);
  const [openCalendar, setOpenCalendar] = useState<'from' | 'to' | null>(null);
  const [draftFrom, setDraftFrom] = useState(value.from ?? daysAgo(29));
  const [draftTo, setDraftTo] = useState(value.to ?? todayIso());
  const [draftError, setDraftError] = useState<string | null>(null);

  /** The dates each preset stands for, so the pop-up can show them. */
  const rangeOf = (key: WindowKey): { from: string; to: string } => {
    if (key === 'period') {
      return period
        ? { from: period.startDate, to: period.endDate }
        : { from: daysAgo(29), to: todayIso() };
    }
    if (key === 'week') return { from: daysAgo(6), to: todayIso() };
    if (key === 'month') return { from: startOfMonth(), to: todayIso() };
    return { from: draftFrom, to: draftTo };
  };

  const label = useMemo(() => {
    const { from, to } = rangeOf(value.key);
    if (value.key === 'period') {
      return period ? `This period · ${formatLongDate(period.startDate)} – ${formatLongDate(period.endDate)}` : 'This period';
    }
    if (value.key === 'week') return 'Last 7 days';
    if (value.key === 'month') return 'This month so far';
    return `${formatLongDate(from)} – ${formatLongDate(to)}`;
  }, [value.key, period, draftFrom, draftTo]);

  const choose = (key: WindowKey) => {
    if (key === 'custom') {
      setListOpen(false);
      setDraftFrom(value.from ?? daysAgo(29));
      setDraftTo(value.to ?? todayIso());
      setDraftError(null);
      setOpenCalendar('from');
      setCustomOpen(true);
      return;
    }

    const range = rangeOf(key);
    setListOpen(false);
    onChange({ key, ...range });
  };

  const applyCustom = () => {
    if (!draftFrom || !draftTo) {
      setDraftError('Choose both a start and an end date.');
      return;
    }
    if (draftFrom > draftTo) {
      setDraftError('The start date is after the end date.');
      return;
    }

    setCustomOpen(false);
    onChange({ key: 'custom', from: draftFrom, to: draftTo });
  };

  return (
    <>
      <Pressable
        onPress={() => setListOpen(true)}
        accessibilityRole="button"
        accessibilityLabel={`Window: ${label}. Change it.`}
        style={({ pressed }) => [
          styles.control,
          { borderColor: theme.border, backgroundColor: theme.backgroundElement },
          pressed && styles.pressed,
        ]}>
        <Icon name="clock" size={18} color={theme.textSecondary} />

        <View style={styles.copy}>
          <ThemedText type="small" style={styles.title} numberOfLines={1}>
            {label}
          </ThemedText>
          <ThemedText type="caption" themeColor="textMuted">
            Tap to change the window
          </ThemedText>
        </View>

        <Icon name="chevronDown" size={18} color={theme.textSecondary} />
      </Pressable>

      {/* The presets, each with the range it covers. */}
      <Popup visible={listOpen} onClose={() => setListOpen(false)}>
        <View style={styles.body}>
          <ThemedText type="hero">Choose a window</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            The interval you are looking at — a preset, or any two dates you choose.
          </ThemedText>

          {OPTIONS.map((option) => {
            const active = option.key === value.key;
            const range = rangeOf(option.key);

            return (
              <Pressable
                key={option.key}
                onPress={() => choose(option.key)}
                accessibilityRole="button"
                accessibilityState={{ selected: active }}
                style={({ pressed }) => [
                  styles.option,
                  {
                    borderColor: active ? Brand.green : theme.border,
                    backgroundColor: theme.background,
                  },
                  pressed && styles.pressed,
                ]}>
                <View style={styles.optionCopy}>
                  <ThemedText type="smallBold">{option.label}</ThemedText>
                  <ThemedText type="caption" themeColor="textMuted" numberOfLines={1}>
                    {formatLongDate(range.from)} – {formatLongDate(range.to)}
                  </ThemedText>
                </View>

                {active ? <Icon name="verified" size={18} color={Brand.green} /> : null}
              </Pressable>
            );
          })}
        </View>
      </Popup>

      {/* The custom range: from at the top with its calendar, to below with its own. */}
      <Popup visible={customOpen} onClose={() => setCustomOpen(false)}>
        <View style={styles.body}>
          <ThemedText type="hero">Custom range</ThemedText>

          <Pressable
            onPress={() => setOpenCalendar(openCalendar === 'from' ? null : 'from')}
            accessibilityRole="button"
            accessibilityLabel={`From ${formatLongDate(draftFrom)}. Change it.`}
            style={({ pressed }) => [
              styles.dateRow,
              { borderColor: openCalendar === 'from' ? Brand.green : theme.border },
              pressed && styles.pressed,
            ]}>
            <View style={styles.optionCopy}>
              <ThemedText type="caption" themeColor="textMuted">
                From
              </ThemedText>
              <ThemedText type="smallBold">{formatLongDate(draftFrom)}</ThemedText>
            </View>
            <Icon
              name={openCalendar === 'from' ? 'chevronUp' : 'chevronDown'}
              size={18}
              color={theme.textSecondary}
            />
          </Pressable>

          {openCalendar === 'from' ? (
            <Calendar
              value={draftFrom}
              max={todayIso()}
              onSelect={(isoDate) => {
                setDraftFrom(isoDate);
                if (draftTo < isoDate) setDraftTo(isoDate);
                setOpenCalendar(null);
                setDraftError(null);
              }}
            />
          ) : null}

          <Pressable
            onPress={() => setOpenCalendar(openCalendar === 'to' ? null : 'to')}
            accessibilityRole="button"
            accessibilityLabel={`To ${formatLongDate(draftTo)}. Change it.`}
            style={({ pressed }) => [
              styles.dateRow,
              { borderColor: openCalendar === 'to' ? Brand.green : theme.border },
              pressed && styles.pressed,
            ]}>
            <View style={styles.optionCopy}>
              <ThemedText type="caption" themeColor="textMuted">
                To
              </ThemedText>
              <ThemedText type="smallBold">{formatLongDate(draftTo)}</ThemedText>
            </View>
            <Icon
              name={openCalendar === 'to' ? 'chevronUp' : 'chevronDown'}
              size={18}
              color={theme.textSecondary}
            />
          </Pressable>

          {openCalendar === 'to' ? (
            <Calendar
              value={draftTo}
              min={draftFrom}
              max={todayIso()}
              onSelect={(isoDate) => {
                setDraftTo(isoDate);
                setOpenCalendar(null);
                setDraftError(null);
              }}
            />
          ) : null}

          {draftError ? (
            <ThemedText type="small" style={styles.error}>
              {draftError}
            </ThemedText>
          ) : null}

          <View style={styles.actions}>
            <Button primary label="Apply" onPress={applyCustom} style={styles.grow} />
            <Button label="Cancel" onPress={() => setCustomOpen(false)} secondary onCard style={styles.grow} />
          </View>
        </View>
      </Popup>
    </>
  );
}

const styles = StyleSheet.create({
  control: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  copy: {
    flex: 1,
    gap: Spacing.half,
  },
  title: {
    flexShrink: 1,
  },
  body: {
    gap: Spacing.three,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  optionCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  dateRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
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
  pressed: {
    opacity: 0.7,
  },
});
