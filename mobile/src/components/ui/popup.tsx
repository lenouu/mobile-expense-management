import { BlurView } from 'expo-blur';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  Animated,
  Easing,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
  useWindowDimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MaxContentWidth, Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

const ENTER_MS = 340;
const EXIT_MS = 240;
/** The backdrop starts first, then the card follows: that beat is what makes a pop read as one. */
const CARD_DELAY = 60;

/**
 * A centred pop-up over a blurred copy of the screen behind it.
 *
 * The blur is what makes it read as a layer rather than another page: the content stays visible
 * but goes out of focus, so the user keeps their place. A dim wash sits under the blur as well,
 * because blur strength varies by platform (on older Android it is a flat tint) and the contrast
 * has to be right either way.
 *
 * Both directions animate: the card fades, scales up from 92% and rises 24pt on the way in, and
 * reverses on the way out. `onClosed` fires only when the exit has finished, which lets the caller
 * keep the form's data alive until the card is gone - otherwise the pop-up would empty mid-flight.
 */
export function Popup({
  visible,
  onClose,
  onClosed,
  children,
  dismissOnBackdrop = true,
  expand = false,
}: {
  visible: boolean;
  onClose: () => void;
  /** Called after the exit animation completes, once the card is off screen. */
  onClosed?: () => void;
  children: ReactNode;
  /** Set false for a pop-up the user must answer (a confirm, or a form in progress). */
  dismissOnBackdrop?: boolean;
  /**
   * Uses all the height the screen has to offer instead of keeping a comfortable margin.
   *
   * <p>For a form whose fields must all be reachable without scrolling. The card still stays inside
   * the screen's safe area, so nothing is ever cut off at the edges.
   */
  expand?: boolean;
}) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();
  const { height: windowHeight } = useWindowDimensions();
  // Three values rather than one: the backdrop fades on its own timing, the card's opacity and
  // its transform can then be shaped separately - which is what allows the scale to overshoot
  // slightly without the opacity doing the same.
  const backdrop = useRef(new Animated.Value(0)).current;
  const cardFade = useRef(new Animated.Value(0)).current;
  const pop = useRef(new Animated.Value(0)).current;

  const [mounted, setMounted] = useState(visible);
  // Kept in refs so a new inline callback on the parent's render cannot restart the animation,
  // and so the "is it on screen" answer is available without re-running the effect.
  const mountedRef = useRef(visible);
  const onClosedRef = useRef(onClosed);
  onClosedRef.current = onClosed;

  useEffect(() => {
    if (visible) {
      mountedRef.current = true;
      setMounted(true);

      Animated.parallel([
        Animated.timing(backdrop, {
          toValue: 1,
          duration: ENTER_MS,
          easing: Easing.out(Easing.quad),
          useNativeDriver: true,
        }),
        Animated.timing(cardFade, {
          toValue: 1,
          duration: ENTER_MS - CARD_DELAY,
          delay: CARD_DELAY,
          easing: Easing.out(Easing.quad),
          useNativeDriver: true,
        }),
        Animated.timing(pop, {
          toValue: 1,
          duration: ENTER_MS - CARD_DELAY,
          delay: CARD_DELAY,
          // A slight overshoot: the card arrives rather than merely appearing.
          easing: Easing.out(Easing.back(1.3)),
          useNativeDriver: true,
        }),
      ]).start();
      return;
    }

    // Nothing on screen yet: do not fire a close for a pop-up that never opened.
    if (!mountedRef.current) return;
    mountedRef.current = false;

    // Leaving: the card goes first, the backdrop clears behind it, and only then do we unmount
    // and let the caller drop the content - so the card never empties mid-flight.
    Animated.parallel([
      Animated.timing(cardFade, {
        toValue: 0,
        duration: EXIT_MS - 60,
        easing: Easing.in(Easing.quad),
        useNativeDriver: true,
      }),
      Animated.timing(pop, {
        toValue: 0,
        duration: EXIT_MS - 60,
        easing: Easing.in(Easing.quad),
        useNativeDriver: true,
      }),
      Animated.timing(backdrop, {
        toValue: 0,
        duration: EXIT_MS,
        easing: Easing.in(Easing.quad),
        useNativeDriver: true,
      }),
    ]).start(({ finished }) => {
      if (!finished) return;
      setMounted(false);
      onClosedRef.current?.();
    });
  }, [visible, backdrop, cardFade, pop]);

  if (!mounted) return null;

  const scale = pop.interpolate({ inputRange: [0, 1], outputRange: [0.86, 1] });
  const translateY = pop.interpolate({ inputRange: [0, 1], outputRange: [32, 0] });
  // Leaves room for the status and navigation areas plus a margin, so the card is always whole.
  // An expanded card gives up only the screen's own padding, not the extra comfort margin.
  const margin = expand ? Spacing.four * 2 : Spacing.six * 2;
  const maxCardHeight = Math.max(
    240,
    windowHeight - insets.top - insets.bottom - margin,
  );

  return (
    <Modal visible transparent animationType="none" statusBarTranslucent onRequestClose={onClose}>
      <View style={styles.fill}>
        <Animated.View style={[StyleSheet.absoluteFill, { opacity: backdrop }]}>
          <View style={styles.dim} />
          <BlurView
            intensity={45}
            tint="dark"
            // Android only blurs with this method; elsewhere it degrades to the dim wash above.
            experimentalBlurMethod="dimezisBlurView"
            style={StyleSheet.absoluteFill}
          />
        </Animated.View>

        <Pressable
          style={styles.fill}
          onPress={dismissOnBackdrop ? onClose : undefined}
          accessibilityRole="button"
          accessibilityLabel="Close">
          <KeyboardAvoidingView
            style={[styles.fill, styles.center]}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
            <Animated.View
              style={[
                styles.card,
                Shadows.emblem,
                {
                  backgroundColor: theme.backgroundElement,
                  borderColor: theme.border,
                  opacity: cardFade,
                  transform: [{ scale }, { translateY }],
                  // Never taller than the window: long content scrolls inside the card instead
                  // of the card running off the top and bottom of the screen.
                  maxHeight: maxCardHeight,
                },
              ]}>
              {/* Taps inside the card must not reach the backdrop's close handler. */}
              <Pressable onPress={() => {}} style={styles.cardInner}>
                <ScrollView
                  style={styles.cardScroll}
                  contentContainerStyle={styles.cardContent}
                  keyboardShouldPersistTaps="handled"
                  showsVerticalScrollIndicator={false}>
                  {children}
                </ScrollView>
              </Pressable>
            </Animated.View>
          </KeyboardAvoidingView>
        </Pressable>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
  },
  dim: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(2, 40, 76, 0.45)',
  },
  center: {
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
  },
  card: {
    width: '100%',
    maxWidth: Math.min(MaxContentWidth, 460),
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.four,
    gap: Spacing.three,
    // A column that may shrink: the scroll view inside gives way, not the card.
    flexShrink: 1,
  },
  /** Lets the scroll view shrink inside the card instead of pushing it past its maxHeight. */
  cardInner: {
    flexShrink: 1,
    minHeight: 0,
  },
  /**
   * The scroll view itself has to be allowed to shrink too: without this it keeps its content
   * height, overflows the card's maxHeight and is simply clipped - which is what made a tall
   * pop-up (the CSV importer) impossible to scroll to its buttons.
   */
  cardScroll: {
    flexShrink: 1,
  },
  cardContent: {
    gap: Spacing.three,
  },
});
