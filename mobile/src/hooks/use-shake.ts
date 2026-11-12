import { useCallback, useEffect, useRef, useState } from 'react';
import { AccessibilityInfo, Animated, Easing } from 'react-native';

/** How far the card travels on each side of centre. */
const OFFSET = 12;

/** How long the whole gesture takes. Two shakes, settling on the last frame. */
const DURATION_MS = 380;

/**
 * A refusal you can feel: the card shakes left and right once.
 *
 * <p>Used for every rejected save - a split that does not add up, a missing field, a wrong password.
 * The red message is what carries the information; the shake is what makes it register. If the OS
 * "reduce motion" setting is on, nothing moves and only the message appears, which is the whole
 * point of that setting.
 *
 * <p>The value returned is an `Animated.Value` in pixels; bind it to a `translateX`.
 */
export function useShake() {
  const offset = useRef(new Animated.Value(0)).current;
  const [motionAllowed, setMotionAllowed] = useState(true);

  useEffect(() => {
    let cancelled = false;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((reduce) => {
        if (!cancelled) setMotionAllowed(!reduce);
      })
      .catch(() => {
        // Unsupported on this platform: assume motion is fine.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const shake = useCallback(() => {
    if (!motionAllowed) return;

    offset.stopAnimation();
    offset.setValue(0);

    const step = (toValue: number, duration: number) =>
      Animated.timing(offset, {
        toValue,
        duration,
        easing: Easing.inOut(Easing.quad),
        useNativeDriver: true,
      });

    Animated.sequence([
      step(-OFFSET, DURATION_MS * 0.15),
      step(OFFSET, DURATION_MS * 0.2),
      step(-OFFSET * 0.7, DURATION_MS * 0.18),
      step(OFFSET * 0.5, DURATION_MS * 0.15),
      step(0, DURATION_MS * 0.2),
    ]).start();
  }, [offset, motionAllowed]);

  return { offset, shake };
}
