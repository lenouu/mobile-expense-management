import { Image } from 'expo-image';
import type { ReactNode } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';

/**
 * The same rule the console header uses: these screens scroll under the status bar rather than
 * inside a navigator header, so they get no safe-area padding of their own. iOS shifts the scroll
 * content down through `contentInsetAdjustmentBehavior`, Android needs the inset added, and web
 * has no status bar at all, so it gets a real page margin instead.
 */
function headerTopPadding(statusBarInset: number): number {
  return (
    Platform.select({
      android: statusBarInset + Spacing.three,
      web: Spacing.five,
      default: Spacing.three,
    }) ?? Spacing.three
  );
}

/**
 * The app's header for the signed-in consumer screens: the FinFlow mark on the left, the screen
 * name beside it, and one optional action on the right.
 *
 * Deliberately a single action slot: every screen gets exactly one primary affordance in this
 * corner (Profile → settings, Expenses → categories), so the top-right corner always means the
 * same thing and never becomes a row of icons.
 */
export function AppHeader({ title, action }: { title: string; action?: ReactNode }) {
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.header, { paddingTop: headerTopPadding(insets.top) }]}>
      <View style={styles.brand}>
        <Image
          source={require('@/assets/images/logo.png')}
          style={styles.logo}
          contentFit="contain"
          accessibilityLabel="FinFlow"
        />
        <ThemedText type="subtitle">{title}</ThemedText>
      </View>

      {action ? <View style={styles.action}>{action}</View> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    // paddingTop is applied inline: it depends on the safe-area inset.
    paddingBottom: Spacing.two,
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    flexShrink: 1,
  },
  logo: {
    width: 34,
    height: 34,
    borderRadius: Radius.small,
  },
  action: {
    flexShrink: 0,
  },
});
