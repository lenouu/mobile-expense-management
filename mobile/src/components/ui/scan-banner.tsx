import { useEffect, useRef } from 'react';
import { ActivityIndicator, Animated, Easing, Pressable, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Brand, Radius, Shadows, Spacing, Status } from '@/constants/theme';
import type { ScanState } from '@/features/finances/lib/receipt-scan';
import { useTheme } from '@/hooks/use-theme';
import { withAlpha } from '@/utils/color';

/** How far above the tab bar the card sits, so it never covers the bar or the "+" button. */
const BOTTOM_GAP = 72;

/**
 * The small notice at the bottom of the app while a receipt is being read.
 *
 * It is deliberately not a pop-up: reading a photo takes a few seconds, and blocking the
 * app for that would be the wrong trade. The card slides in, stays out of the way, and
 * turns into "tap to review" once the model has answered.
 */
export function ScanBanner({
  state,
  onPress,
  onDismiss,
}: {
  state: ScanState;
  onPress: () => void;
  onDismiss: () => void;
}) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const slide = useRef(new Animated.Value(0)).current;
  const visible = state.status !== 'idle';

  useEffect(() => {
    Animated.timing(slide, {
      toValue: visible ? 1 : 0,
      duration: 260,
      easing: visible ? Easing.out(Easing.cubic) : Easing.in(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [visible, slide]);

  if (!visible) return null;

  const scanning = state.status === 'scanning';

  const copy =
    state.status === 'scanning'
      ? {
          title: 'Reading your receipt…',
          hint: 'Carry on — we will tell you when it is ready.',
        }
      : state.status === 'ready'
        ? {
            title: 'Your receipt is ready',
            hint: 'Tap to review the expenses before adding them.',
          }
        : state.status === 'saved'
          ? {
              title:
                state.count === 1 ? '1 expense added' : `${state.count} expenses added`,
              hint: 'Overview, Transactions and Budget are up to date.',
            }
          : { title: 'That receipt could not be read', hint: state.message };

  const accent = state.status === 'error' ? Status.danger : Brand.green;
  const tappable = state.status === 'ready';

  const translateY = slide.interpolate({ inputRange: [0, 1], outputRange: [80, 0] });

  return (
    <Animated.View
      pointerEvents="box-none"
      style={[
        styles.wrap,
        {
          bottom: insets.bottom + BOTTOM_GAP,
          opacity: slide,
          transform: [{ translateY }],
        },
      ]}>
      <Pressable
        onPress={tappable ? onPress : undefined}
        disabled={!tappable}
        accessibilityRole={tappable ? 'button' : 'text'}
        accessibilityLabel={`${copy.title}. ${copy.hint}`}
        style={({ pressed }) => [
          styles.card,
          Shadows.emblem,
          { backgroundColor: theme.backgroundElement, borderColor: theme.border },
          pressed && styles.pressed,
        ]}>
        <View style={[styles.iconWrap, { backgroundColor: withAlpha(accent, 0.14) }]}>
          {scanning ? (
            <ActivityIndicator size="small" color={accent} />
          ) : (
            <Icon
              name={state.status === 'error' ? 'close' : state.status === 'saved' ? 'verified' : 'bolt'}
              size={18}
              color={accent}
            />
          )}
        </View>

        <View style={styles.copy}>
          <ThemedText type="smallBold" numberOfLines={1}>
            {copy.title}
          </ThemedText>
          <ThemedText type="caption" themeColor="textSecondary" numberOfLines={2}>
            {copy.hint}
          </ThemedText>
        </View>

        {state.status === 'ready' ? (
          <Icon name="chevronRight" size={18} color={theme.textSecondary} />
        ) : null}

        {state.status !== 'scanning' ? (
          <Pressable
            onPress={onDismiss}
            accessibilityRole="button"
            accessibilityLabel="Dismiss"
            hitSlop={10}
            style={({ pressed }) => [styles.close, pressed && styles.pressed]}>
            <Icon name="close" size={16} color={theme.textMuted} />
          </Pressable>
        ) : null}
      </Pressable>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  wrap: {
    position: 'absolute',
    left: 0,
    right: 0,
    paddingHorizontal: Spacing.four,
    // Above the tab bar and the floating "+" button.
    zIndex: 30,
    elevation: 30,
  },
  card: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.card,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  iconWrap: {
    width: 34,
    height: 34,
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  copy: {
    flex: 1,
    gap: Spacing.half,
  },
  close: {
    width: 28,
    height: 28,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
});
