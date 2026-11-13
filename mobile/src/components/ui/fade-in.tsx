import { Children, useEffect, useRef, useState, type ReactNode } from 'react';
import {
  AccessibilityInfo,
  Animated,
  Easing,
  type StyleProp,
  type ViewStyle,
} from 'react-native';

/** How long the reveal takes once the screen mounts. */
const DURATION_MS = 320;

/** How far the content rises into place: enough to feel like weight, not like a slide. */
const RISE = 12;

/**
 * Reveals its children with a short fade and rise when the screen first appears.
 *
 * Built on `Animated` from React Native rather than reanimated: the same API the dashboard banner
 * and the splash overlay already use, and it runs on the native driver, so the JS thread is free
 * while the screen measures itself.
 *
 * `delay` staggers siblings - the brand block first, then the card, then the footer - which reads
 * as composed rather than as one flat fade.
 *
 * If the OS "reduce motion" setting is on, the content is shown immediately: an entrance animation
 * is decoration, and decoration is exactly what that setting asks to be spared.
 */
export function FadeIn({
  children,
  delay = 0,
  style,
}: {
  children: ReactNode;
  /** Milliseconds to wait before starting, for staggering siblings. */
  delay?: number;
  style?: StyleProp<ViewStyle>;
}) {
  const progress = useRef(new Animated.Value(0)).current;
  const [motionAllowed, setMotionAllowed] = useState(true);

  useEffect(() => {
    let cancelled = false;

    AccessibilityInfo.isReduceMotionEnabled()
      .then((reduceMotion) => {
        if (!cancelled) setMotionAllowed(!reduceMotion);
      })
      .catch(() => {
        // Unsupported on this platform: assume motion is fine.
      });

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!motionAllowed) {
      // Stop first: a running animation would keep writing values over the snap.
      progress.stopAnimation();
      progress.setValue(1);
      return;
    }

    const animation = Animated.timing(progress, {
      toValue: 1,
      duration: DURATION_MS,
      delay,
      easing: Easing.out(Easing.quad),
      useNativeDriver: true,
    });

    animation.start();
    return () => animation.stop();
  }, [progress, delay, motionAllowed]);

  const translateY = progress.interpolate({ inputRange: [0, 1], outputRange: [RISE, 0] });

  return (
    <Animated.View style={[style, { opacity: progress, transform: [{ translateY }] }]}>
      {guardChildren(children, 'FadeIn')}
    </Animated.View>
  );
}

/**
 * A View cannot render a bare string - React Native refuses it - so a stray text child is dropped
 * and named in development. Text belongs in `ThemedText`; anything that reaches here is a bug
 * upstream, and the warning says exactly which string it was.
 */
export function guardChildren(children: ReactNode, where: string): ReactNode {
  return Children.toArray(children).filter((child) => {
    if (typeof child === 'string' || typeof child === 'number') {
      if (__DEV__ && String(child).trim().length > 0) {
        console.warn(`[${where}] text outside a <Text>:`, JSON.stringify(String(child)));
      }
      return false;
    }
    return true;
  });
}
