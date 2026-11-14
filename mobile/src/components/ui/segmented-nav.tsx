import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, View, type LayoutChangeEvent } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const SLIDE_MS = 260;

export type SegmentItem = { key: string; label: string };

/**
 * Underline tabs, for switching between the sections of one tab.
 *
 * Underlines rather than pills on purpose: the app already uses pills for *filters* (the window
 * presets, the categories), and a filter look on a navigation control is what makes a screen feel
 * muddy. The active section is marked three ways at once - its label goes bold, it takes the brand
 * green, and a green rule slides underneath it - so the state is readable even at a glance, and
 * the slide makes the change feel continuous rather than a jump.
 *
 * Tabs share the width equally, which is what allows the indicator to be positioned arithmetically
 * rather than by measuring every label.
 */
export function SegmentedNav({
  items,
  value,
  onChange,
}: {
  items: SegmentItem[];
  value: string;
  onChange: (key: string) => void;
}) {
  const theme = useTheme();
  const [width, setWidth] = useState(0);

  const index = Math.max(
    0,
    items.findIndex((item) => item.key === value),
  );
  const slide = useRef(new Animated.Value(index)).current;

  useEffect(() => {
    Animated.timing(slide, {
      toValue: index,
      duration: SLIDE_MS,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [index, slide]);

  const tabWidth = items.length > 0 ? width / items.length : 0;
  const travel = tabWidth * Math.max(items.length - 1, 1);
  const translateX = slide.interpolate({
    inputRange: [0, Math.max(items.length - 1, 1)],
    outputRange: [0, travel],
  });

  const onLayout = (event: LayoutChangeEvent) => setWidth(event.nativeEvent.layout.width);

  return (
    <View
      onLayout={onLayout}
      style={[styles.bar, { borderBottomColor: theme.border }]}
      accessibilityRole="tablist">
      <View style={styles.row}>
        {items.map((item) => {
          const active = item.key === value;

          return (
            <Pressable
              key={item.key}
              onPress={() => onChange(item.key)}
              accessibilityRole="tab"
              accessibilityState={{ selected: active }}
              accessibilityLabel={item.label}
              hitSlop={Spacing.two}
              style={({ pressed }) => [styles.tab, pressed && styles.pressed]}>
              <ThemedText
                type={active ? 'smallBold' : 'small'}
                // Never wrap: the tabs split the width evenly, so a long label like
                // "Transactions" is wider than its column and used to break onto a second line,
                // orphaning the final "s". One line plus auto-shrink keeps every label intact.
                numberOfLines={1}
                adjustsFontSizeToFit
                minimumFontScale={0.8}
                style={[styles.label, { color: active ? Brand.green : theme.textSecondary }]}>
                {item.label}
              </ThemedText>
            </Pressable>
          );
        })}
      </View>

      {tabWidth > 0 ? (
        <Animated.View
          style={[
            styles.indicator,
            { width: tabWidth, backgroundColor: Brand.green, transform: [{ translateX }] },
          ]}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    borderBottomWidth: 1,
    position: 'relative',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'stretch',
  },
  tab: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: Spacing.three,
    paddingHorizontal: Spacing.one,
  },
  label: {
    // The four tabs split the width evenly, so each one only has ~a quarter of the screen. At the
    // shared 14px step "Transactions" measures ~90px, which does not fit a ~74px tab and broke the
    // final "s" onto its own line. 12px (the type scale's caption/label size) measures ~77px, and
    // dropping the side padding to Spacing.one leaves ~82px of room - enough with margin to spare.
    fontSize: 12,
    // A definite width gives auto-shrink something to measure against, and keeps the label
    // centred on its own tab.
    width: '100%',
    textAlign: 'center',
  },
  indicator: {
    position: 'absolute',
    left: 0,
    bottom: -1,
    height: 3,
    borderTopLeftRadius: 3,
    borderTopRightRadius: 3,
  },
  pressed: {
    opacity: 0.6,
  },
});
