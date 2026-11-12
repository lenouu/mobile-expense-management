import { Image } from 'expo-image';
import { useEffect, useRef } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Shadows, Spacing } from '@/constants/theme';

/** One full up-and-down cycle: slow enough to read as breathing rather than as movement. */
const FLOAT_HALF_CYCLE_MS = 1500;

/** How far the emblem rises, in points. Far enough to be noticed, small enough not to drift. */
const FLOAT_DISTANCE = 10;

/**
 * The logo emblem and page title from the top of the Figma sign-up screen.
 *
 * The 64pt navy tile is the Figma "Logo Emblem"; the artwork inside is the project's own
 * `assets/images/logo.png`. It is shared by sign-up and any future auth screen so the brand
 * block is never redrawn by hand.
 *
 * `floating` adds a continuous, gentle up-and-down motion to the emblem. It is opt-in so only the
 * screen that asked for it animates, and the loop is stopped on unmount (or when the prop turns
 * off) so nothing keeps running in the background.
 */
export function BrandLockup({
  title,
  subtitle,
  floating = false,
}: {
  title: string;
  subtitle?: string;
  floating?: boolean;
}) {
  const drift = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    if (!floating) return;

    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(drift, {
          toValue: 1,
          duration: FLOAT_HALF_CYCLE_MS,
          easing: Easing.inOut(Easing.quad),
          useNativeDriver: true,
        }),
        Animated.timing(drift, {
          toValue: 0,
          duration: FLOAT_HALF_CYCLE_MS,
          easing: Easing.inOut(Easing.quad),
          useNativeDriver: true,
        }),
      ]),
    );

    loop.start();
    return () => {
      loop.stop();
      drift.setValue(0);
    };
  }, [floating, drift]);

  const lift = drift.interpolate({
    inputRange: [0, 1],
    outputRange: [0, -FLOAT_DISTANCE],
  });

  return (
    <View style={styles.container}>
      <Animated.View style={floating ? { transform: [{ translateY: lift }] } : undefined}>
        <View style={[styles.emblem, Shadows.emblem]}>
          <Image
            source={require('@/assets/images/logo.png')}
            style={styles.logo}
            contentFit="contain"
            accessibilityLabel="FinFlow logo"
          />
        </View>
      </Animated.View>

      <View style={styles.copy}>
        <ThemedText type="heading" style={styles.centered}>
          {title}
        </ThemedText>
        {subtitle ? (
          <ThemedText type="caption" themeColor="textSecondary" style={styles.centered}>
            {subtitle}
          </ThemedText>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    gap: Spacing.three - 3,
  },
  emblem: {
    width: 64,
    height: 64,
    borderRadius: Radius.card,
    borderWidth: 1,
    borderColor: '#F1F5F9',
    backgroundColor: Brand.navy,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.half + 1,
  },
  logo: {
    width: '100%',
    height: '100%',
    borderRadius: Radius.field,
  },
  copy: {
    alignItems: 'center',
    gap: Spacing.half,
    maxWidth: 320,
  },
  centered: {
    textAlign: 'center',
  },
});
