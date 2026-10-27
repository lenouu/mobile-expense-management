import { Image } from 'expo-image';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, StyleSheet } from 'react-native';

import { Brand } from '@/constants/theme';

// Kept at module scope: the native splash must be held from the moment the bundle loads,
// otherwise the OS shows the app before this component has had a chance to paint.
SplashScreen.preventAutoHideAsync().catch(() => {
  /* Already hidden, or unsupported on this platform. */
});

/**
 * The branded loading overlay shown while the first screen measures itself.
 *
 * It replaces the Expo template's animation, which drew the Expo logo: this one draws the
 * project logo on the brand navy so the hand-off from the native splash is invisible. It hides
 * itself once, then unmounts, so nothing is left animating behind the app.
 */
export function SplashOverlay() {
  const [visible, setVisible] = useState(true);
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    // Wait for the first layout, then reveal the app underneath.
    const animation = Animated.sequence([
      Animated.timing(progress, {
        toValue: 1,
        duration: 320,
        easing: Easing.out(Easing.quad),
        useNativeDriver: true,
      }),
      Animated.delay(120),
      Animated.timing(progress, {
        toValue: 2,
        duration: 260,
        easing: Easing.in(Easing.quad),
        useNativeDriver: true,
      }),
    ]);

    SplashScreen.hideAsync().catch(() => {
      /* Nothing to hide. */
    });

    animation.start(({ finished }) => {
      if (finished) setVisible(false);
    });

    return () => animation.stop();
  }, [progress]);

  if (!visible) return null;

  const opacity = progress.interpolate({ inputRange: [0, 1, 2], outputRange: [0, 1, 0] });
  const scale = progress.interpolate({ inputRange: [0, 1, 2], outputRange: [0.92, 1, 1.04] });

  return (
    <Animated.View style={[styles.overlay, { opacity }]} pointerEvents="none">
      <Animated.View style={{ transform: [{ scale }] }}>
        <Image
          source={require('@/assets/images/logo.png')}
          style={styles.logo}
          contentFit="contain"
          accessibilityLabel="FinFlow"
        />
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  overlay: {
    ...StyleSheet.absoluteFill,
    backgroundColor: Brand.navy,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
  },
  logo: {
    width: 180,
    height: 180,
  },
});
