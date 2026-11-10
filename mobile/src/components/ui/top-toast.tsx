import { useCallback, useEffect, useRef, useState } from 'react';
import { Animated, Easing, Pressable, StyleSheet, View, type LayoutChangeEvent } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Shadows, Spacing } from '@/constants/theme';
import { Icon, type IconName } from '@/components/ui/icon';
import { useTheme } from '@/hooks/use-theme';
import { withAlpha } from '@/utils/color';

/** Arriving is slower than leaving: the eye should have time to read, not to wait to be rid of it. */
const IN_MS = 320;
const OUT_MS = 180;
/** How long the toast stays on screen once it has landed. */
const STAY_MS = 3000;
/** The line finishes just before the toast goes, so its end is never left hanging. */
const LINE_MS = 2750;

export type ToastKind = 'created' | 'edited' | 'deleted';

/** A toast to show. `id` is what restarts the timer when the same kind fires twice in a row. */
export type ToastMessage = { id: number; message: string; kind: ToastKind };

const ICONS: Record<ToastKind, IconName> = {
  created: 'add',
  edited: 'edit',
  deleted: 'delete',
};

/**
 * The confirmation that slides down from the top after a create, edit or delete.
 *
 * It carries a green line that empties from right to left over its lifetime - a countdown you can
 * read without a clock, the way a loading bar works in reverse. Tapping dismisses it immediately,
 * which cuts the timer short rather than waiting it out.
 *
 * The icon says what happened before the sentence is read: a plus, a pencil, a bin.
 */
export function TopToast({
  toast,
  onDismiss,
}: {
  /** Null when there is nothing to show. */
  toast: ToastMessage | null;
  onDismiss: () => void;
}) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const [mounted, setMounted] = useState(false);
  const [lineWidth, setLineWidth] = useState(0);
  // 0 = above the screen, 1 = in place. Drives both the slide and the fade.
  const slide = useRef(new Animated.Value(0)).current;
  // 1 = full line, 0 = empty. Animating width needs the JS driver, so it is its own value.
  const line = useRef(new Animated.Value(1)).current;

  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const leaving = useRef(false);
  const onDismissRef = useRef(onDismiss);
  onDismissRef.current = onDismiss;

  const hide = useCallback(() => {
    if (leaving.current) return;
    leaving.current = true;

    if (timer.current) {
      clearTimeout(timer.current);
      timer.current = null;
    }

    Animated.timing(slide, {
      toValue: 0,
      duration: OUT_MS,
      easing: Easing.in(Easing.cubic),
      useNativeDriver: true,
    }).start(({ finished }) => {
      if (!finished) return;
      setMounted(false);
      leaving.current = false;
      onDismissRef.current();
    });
  }, [slide]);

  useEffect(() => {
    if (!toast) return;

    setMounted(true);
    leaving.current = false;
    slide.setValue(0);
    line.setValue(1);

    Animated.parallel([
      Animated.timing(slide, {
        toValue: 1,
        duration: IN_MS,
        easing: Easing.out(Easing.cubic),
        useNativeDriver: true,
      }),
      Animated.timing(line, {
        toValue: 0,
        duration: LINE_MS,
        easing: Easing.linear,
        // Width cannot be driven natively; the slide still is, on its own value.
        useNativeDriver: false,
      }),
    ]).start();

    timer.current = setTimeout(hide, STAY_MS);

    return () => {
      if (timer.current) {
        clearTimeout(timer.current);
        timer.current = null;
      }
    };
    // Restart on every new message, even of the same kind.
  }, [toast?.id, hide, line, slide]);

  if (!mounted || !toast) return null;

  const translateY = slide.interpolate({
    inputRange: [0, 1],
    outputRange: [-(insets.top + 120), 0],
  });
  const width = line.interpolate({ inputRange: [0, 1], outputRange: [0, lineWidth] });

  const onTrackLayout = (event: LayoutChangeEvent) => setLineWidth(event.nativeEvent.layout.width);

  return (
    <Animated.View
      pointerEvents="box-none"
      style={[
        styles.wrap,
        { paddingTop: insets.top + Spacing.two, opacity: slide, transform: [{ translateY }] },
      ]}>
      <Pressable
        onPress={hide}
        accessibilityRole="button"
        accessibilityLabel={`${toast.message}. Tap to dismiss.`}
        style={({ pressed }) => [
          styles.card,
          Shadows.emblem,
          { backgroundColor: theme.backgroundElement, borderColor: theme.border },
          pressed && styles.pressed,
        ]}>
        <View style={styles.row}>
          <View style={[styles.iconWrap, { backgroundColor: withAlpha(Brand.green, 0.16) }]}>
            <Icon name={ICONS[toast.kind]} size={18} color={Brand.green} />
          </View>

          <ThemedText type="small" style={styles.message}>
            {toast.message}
          </ThemedText>
        </View>

        <View style={styles.track} onLayout={onTrackLayout}>
          <Animated.View style={[styles.line, { width }]} />
        </View>
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    paddingHorizontal: Spacing.four,
    // Above the floating action button and anything scrolling underneath.
    zIndex: 20,
    elevation: 20,
  },
  card: {
    borderRadius: Radius.card,
    borderWidth: 1,
    paddingTop: Spacing.three,
    paddingHorizontal: Spacing.three,
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  iconWrap: {
    width: 32,
    height: 32,
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  message: {
    flex: 1,
  },
  track: {
    height: 3,
    marginTop: Spacing.three,
    borderRadius: 2,
    overflow: 'hidden',
  },
  line: {
    height: '100%',
    borderRadius: 2,
    backgroundColor: Brand.green,
  },
  pressed: {
    opacity: 0.9,
  },
});
