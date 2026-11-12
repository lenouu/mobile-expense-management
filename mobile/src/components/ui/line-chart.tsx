import { useEffect, useMemo, useRef } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import Svg, { Line, Path } from 'react-native-svg';

import { ThemedText } from '@/components/themed-text';
import { Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const AnimatedPath = Animated.createAnimatedComponent(Path);

/** One line: what it is called, its colour, and a value per label. */
export type Series = {
  key: string;
  label: string;
  color: string;
  values: number[];
  /** Draws the same line as a faint dashed ghost - the comparison window. */
  dashed?: boolean;
};

/**
 * A multi-series line chart, drawn with SVG.
 *
 * <p>Three things make it worth the dependency over a row of bars: the lines can cross, the zero
 * axis is drawn so a negative balance is visible rather than inferred, and the values at the right
 * edge let the chart and the headline agree.
 *
 * <p>Lines animate in by drawing themselves - a stroke offset from full length to zero - because a
 * trend that appears is read; a trend that is simply there is skimmed. The animation is skipped
 * when the OS asks for reduced motion.
 */
export function LineChart({
  labels,
  series,
  height = 190,
  formatValue,
}: {
  labels: string[];
  series: Series[];
  height?: number;
  /** Formats a value for the axis and the end labels. */
  formatValue: (value: number) => string;
}) {
  const theme = useTheme();
  const draw = useRef(new Animated.Value(0)).current;

  const width = 320; // laid out by the container; the viewBox scales it
  const padding = { top: 14, right: 44, bottom: 22, left: 8 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;

  const { min, max, points } = useMemo(() => {
    const all = series.flatMap((line) => line.values);
    const rawMin = Math.min(0, ...all);
    const rawMax = Math.max(0, ...all);
    // A little headroom so the highest point never touches the frame.
    const span = rawMax - rawMin || 1;
    const lo = rawMin - span * 0.08;
    const hi = rawMax + span * 0.08;

    const step = labels.length > 1 ? plotWidth / (labels.length - 1) : 0;
    const mapped = series.map((line) => ({
      ...line,
      coords: line.values.map((value, index) => ({
        x: padding.left + index * step,
        y: padding.top + plotHeight - ((value - lo) / (hi - lo)) * plotHeight,
      })),
    }));

    return { min: lo, max: hi, points: mapped };
  }, [series, labels.length, plotWidth, plotHeight, padding.left, padding.top]);

  const zeroY =
    padding.top + plotHeight - ((0 - min) / (max - min || 1)) * plotHeight;

  useEffect(() => {
    draw.setValue(0);
    Animated.timing(draw, {
      toValue: 1,
      duration: 720,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    }).start();
  }, [series, draw]);

  const pathOf = (coords: { x: number; y: number }[]) =>
    coords
      .map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`)
      .join(' ');

  return (
    <View style={styles.wrap}>
      <Svg width="100%" height={height} viewBox={`0 0 ${width} ${height}`}>
        {/* The zero axis: where saving turns into owing. */}
        <Line
          x1={padding.left}
          y1={zeroY}
          x2={width - padding.right}
          y2={zeroY}
          stroke={theme.border}
          strokeWidth={1}
        />

        {points.map((line) => {
          const path = pathOf(line.coords);
          return (
            <AnimatedPath
              key={line.key}
              d={path}
              fill="none"
              stroke={line.color}
              strokeWidth={line.dashed ? 1.5 : 2.5}
              strokeDasharray={line.dashed ? '4 4' : undefined}
              strokeLinecap="round"
              strokeLinejoin="round"
              opacity={line.dashed ? 0.55 : 1}
              // Drawing itself: the dash offset runs from "fully hidden" to zero.
              strokeDashoffset={line.dashed ? 0 : draw.interpolate({ inputRange: [0, 1], outputRange: [1200, 0] })}
              {...(line.dashed ? {} : { strokeDasharray: 1200 })}
            />
          );
        })}

      </Svg>

      {/* Text stays outside the SVG: strings inside an <Svg> go through the native SVG text node,
          and that is what raised "Text strings must be rendered within a <Text> component". */}
      <View style={styles.axis}>
        <ThemedText type="caption" themeColor="textMuted" style={styles.axisLabel}>
          {labels[0] ?? ''}
        </ThemedText>
        <ThemedText type="caption" themeColor="textMuted" style={styles.axisLabelEnd}>
          {labels[labels.length - 1] ?? ''}
        </ThemedText>
      </View>

      <View style={styles.legend}>
        {series.map((line) => (
          <View key={line.key} style={styles.legendItem}>
            <View
              style={[
                styles.swatch,
                { backgroundColor: line.dashed ? 'transparent' : line.color, borderColor: line.color },
              ]}
            />
            <ThemedText type="caption" themeColor="textMuted">
              {line.label}
            </ThemedText>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrap: { gap: Spacing.one },
  axis: { flexDirection: 'row', justifyContent: 'space-between', paddingHorizontal: 4 },
  axisLabel: { fontSize: 9 },
  axisLabelEnd: { fontSize: 9 },
  legend: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  legendItem: { flexDirection: 'row', alignItems: 'center', gap: Spacing.half },
  swatch: { width: 10, height: 3, borderRadius: 2, borderWidth: 1 },
});
