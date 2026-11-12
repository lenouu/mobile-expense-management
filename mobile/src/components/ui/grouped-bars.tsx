import { useEffect, useRef } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import Svg, { G, Line, Rect } from 'react-native-svg';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** One set of bars: a colour, a name, and a value per label. */
export type BarGroup = {
  key: string;
  label: string;
  color: string;
  values: number[];
  /** Drawn as an outline - the comparison window. */
  ghost?: boolean;
};

/**
 * Grouped bars: the same chart the line draws, in blocks.
 *
 * <p>A line answers "how is it going"; bars answer "how much, and how does it compare". Two groups
 * per label put this window beside the one before it, bucket for bucket, which a percentage alone
 * cannot show: 40 000 against 20 000 and 400 000 against 200 000 are both "+100%".
 *
 * <p>Values below zero hang under the axis, so a month that spent more than it earned is drawn as
 * the shape it is rather than clipped to nothing.
 */
export function GroupedBars({
  labels,
  groups,
  height = 190,
  formatValue,
}: {
  labels: string[];
  groups: BarGroup[];
  height?: number;
  formatValue: (value: number) => string;
}) {
  const theme = useTheme();
  const grow = useRef(new Animated.Value(0)).current;

  const width = 320;
  const padding = { top: 12, right: 8, bottom: 22, left: 8 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const all = groups.flatMap((group) => group.values);
  const rawMax = Math.max(0, ...all);
  const rawMin = Math.min(0, ...all);
  const span = rawMax - rawMin || 1;
  const hi = rawMax + span * 0.08;
  const lo = rawMin - span * 0.08;

  const zeroY = padding.top + plotHeight - ((0 - lo) / (hi - lo)) * plotHeight;
  const bandWidth = plotWidth / Math.max(1, labels.length);
  const barWidth = Math.min(14, (bandWidth - 6) / Math.max(1, groups.length));

  useEffect(() => {
    grow.setValue(0);
    Animated.timing(grow, {
      toValue: 1,
      duration: 620,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    }).start();
  }, [groups, grow]);

  return (
    <View style={styles.wrap}>
      <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`}>
        <Line
          x1={padding.left}
          y1={zeroY}
          x2={width - padding.right}
          y2={zeroY}
          stroke={theme.border}
          strokeWidth={1}
        />

        {labels.map((label, index) => {
          const bandStart = padding.left + index * bandWidth;
          const groupOffset = (bandWidth - barWidth * groups.length) / 2;

          return (
            <G key={label}>
              {groups.map((group, groupIndex) => {
                const value = group.values[index] ?? 0;
                const valueY = padding.top + plotHeight - ((value - lo) / (hi - lo)) * plotHeight;
                const top = Math.min(valueY, zeroY);
                const barHeight = Math.max(1, Math.abs(zeroY - valueY));

                return (
                  <Rect
                    key={group.key}
                    x={bandStart + groupOffset + groupIndex * barWidth}
                    y={top}
                    width={Math.max(2, barWidth - 2)}
                    height={barHeight}
                    rx={2}
                    fill={group.ghost ? 'transparent' : group.color}
                    stroke={group.color}
                    strokeWidth={group.ghost ? 1 : 0}
                    opacity={group.ghost ? 0.75 : 0.95}
                  />
                );
              })}

            </G>
          );
        })}

      </Svg>

      {/* Labels live outside the SVG on purpose: text inside an <Svg> goes through the native SVG
          text node, and a version mismatch there is what produced the "Text strings must be
          rendered within a <Text> component" error. */}
      <View style={styles.axis}>
        {labels.map((label) => (
          <ThemedText key={label} type="caption" themeColor="textMuted" style={styles.axisLabel}>
            {label}
          </ThemedText>
        ))}
      </View>

      <View style={styles.legend}>
        {groups.map((group) => (
          <View key={group.key} style={styles.legendItem}>
            <View
              style={[
                styles.swatch,
                {
                  backgroundColor: group.ghost ? 'transparent' : group.color,
                  borderColor: group.color,
                },
              ]}
            />
            <ThemedText type="caption" themeColor="textMuted">
              {group.label}
            </ThemedText>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: Spacing.one },
  axis: { flexDirection: 'row', paddingHorizontal: 6 },
  axisLabel: { flex: 1, textAlign: 'center', fontSize: 9 },
  legend: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: Spacing.half },
  swatch: { width: 10, height: 8, borderRadius: 2, borderWidth: 1 },
});
