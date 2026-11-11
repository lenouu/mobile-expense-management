import { useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing, StyleSheet, View } from 'react-native';

import { Brand, Radius, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * A horizontal bar that fills to a share of its track.
 *
 * Two details worth the code: the fill animates from zero so the eye reads the number as a
 * proportion rather than discovering a bar already full, and a value past its budget overshoots
 * into red instead of being clamped invisibly at 100% - "you are at 140%" is the point of the bar.
 */
export function ProgressBar({
  value,
  max,
  height = 10,
  delay = 0,
  /** Overrides the colour; used to paint an exceeded budget red. */
  exceeded = false,
}: {
  value: number;
  max: number;
  height?: number;
  delay?: number;
  exceeded?: boolean;
}) {
  const theme = useTheme();
  const progress = useRef(new Animated.Value(0)).current;
  const [motionAllowed, setMotionAllowed] = useState(true);

  useEffect(() => {
    let cancelled = false;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((reduce) => {
        if (!cancelled) setMotionAllowed(!reduce);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  // The share is clamped for the fill width, but the colour tells the truth about going over.
  const share = max > 0 ? Math.min(1, Math.max(0, value / max)) : 0;

  useEffect(() => {
    if (!motionAllowed) {
      progress.stopAnimation();
      progress.setValue(share);
      return;
    }

    const animation = Animated.timing(progress, {
      toValue: share,
      duration: 700,
      delay,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    });

    animation.start();
    return () => animation.stop();
  }, [share, delay, motionAllowed, progress]);

  const width = progress.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] });
  const over = exceeded || (max > 0 && value > max);

  return (
    <View style={[styles.track, { height, borderRadius: height / 2, backgroundColor: theme.backgroundSelected }]}>
      <Animated.View
        style={{
          width,
          height: '100%',
          borderRadius: height / 2,
          backgroundColor: over ? Status.danger : Brand.green,
        }}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: '100%',
    overflow: 'hidden',
    borderRadius: Radius.pill,
  },
});
