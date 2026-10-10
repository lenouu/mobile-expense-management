import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Spacing, Status } from '@/constants/theme';
import type { ChartBar } from '@/features/admin/lib/admin-stats';
import { useTheme } from '@/hooks/use-theme';

/**
 * The column chart shared by every console screen, drawn with plain views.
 *
 * Two series stand side by side per column, which is how the mock-ups compare "New Registrations"
 * against "Active Sessions". A native chart library is not an option here: it would need a native
 * module, and Expo Go cannot load one without a rebuild.
 */
export function BarChart({
  bars,
  series,
  height = 150,
  emptyLabel = 'Nothing recorded in this period',
}: {
  bars: ChartBar[];
  /** One entry for a single-series chart, two for a comparison. */
  series: { label: string; color: string }[];
  height?: number;
  emptyLabel?: string;
}) {
  const theme = useTheme();
  const grandTotal = bars.reduce((sum, bar) => sum + bar.value + (bar.compare ?? 0), 0);
  const max = Math.max(...bars.flatMap((bar) => [bar.value, bar.compare ?? 0]), 1);

  return (
    <View style={[styles.chart, { height }]}>
      {grandTotal === 0 ? (
        <View style={styles.empty}>
          <ThemedText type="small" themeColor="textMuted">
            {emptyLabel}
          </ThemedText>
        </View>
      ) : (
        <>
          <View style={[styles.baseline, { backgroundColor: theme.border }]} />
          {bars.map((bar, index) => (
            <View key={`${bar.label}-${index}`} style={styles.column}>
              <View style={styles.barArea}>
                {series.map((entry, seriesIndex) => {
                  const value = seriesIndex === 0 ? bar.value : (bar.compare ?? 0);
                  // A non-zero value always gets a visible sliver, so "one happened here" reads
                  // differently from "none happened here".
                  const barHeight =
                    value === 0 ? 0 : Math.max(3, Math.round((value / max) * (height - 26)));
                  return (
                    <View
                      key={entry.label}
                      style={[
                        styles.bar,
                        {
                          height: barHeight,
                          backgroundColor: entry.color,
                          opacity: seriesIndex === 0 ? 1 : 0.6,
                        },
                      ]}
                    />
                  );
                })}
              </View>
              <ThemedText
                type="caption"
                themeColor="textMuted"
                numberOfLines={1}
                style={styles.columnLabel}>
                {bar.label}
              </ThemedText>
            </View>
          ))}
        </>
      )}
    </View>
  );
}

/**
 * The legend under a chart: a dot, a label and that series' total.
 *
 * Passing `onToggle` makes an entry pressable, which is how the Home screen switches the chart
 * between registrations and errors without a second chart.
 */
export function ChartLegend({
  entries,
  totals,
}: {
  entries: { label: string; color: string; onToggle?: () => void; dimmed?: boolean }[];
  totals?: number[];
}) {
  return (
    <View style={styles.legend}>
      {entries.map((entry, index) => (
        <ThemedText
          key={entry.label}
          type="caption"
          themeColor={entry.dimmed ? 'textMuted' : 'textSecondary'}
          onPress={entry.onToggle}
          style={styles.legendItem}>
          <ThemedText type="caption" style={{ color: entry.color }}>
            ●
          </ThemedText>
          {` ${entry.label}`}
          {totals && totals[index] !== undefined ? ` · ${totals[index]}` : ''}
        </ThemedText>
      ))}
    </View>
  );
}

/** The colours the console uses for its chart series. */
export const CHART_COLORS = {
  primary: Brand.green,
  compare: Brand.navy,
  danger: Status.danger,
} as const;

const styles = StyleSheet.create({
  chart: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: Spacing.one,
    position: 'relative',
  },
  column: {
    flex: 1,
    alignItems: 'center',
    gap: Spacing.half,
    height: '100%',
    justifyContent: 'flex-end',
  },
  barArea: {
    flex: 1,
    width: '100%',
    flexDirection: 'row',
    alignItems: 'flex-end',
    justifyContent: 'center',
    gap: 2,
  },
  bar: {
    flexGrow: 1,
    maxWidth: 14,
    borderRadius: Radius.small,
    minWidth: 4,
  },
  baseline: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 18,
    height: StyleSheet.hairlineWidth,
  },
  columnLabel: {
    fontSize: 10,
    lineHeight: 14,
    textAlign: 'center',
  },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  legend: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.three,
  },
  legendItem: {
    fontWeight: '600',
  },
});
