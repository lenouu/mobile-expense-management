import { Image } from 'expo-image';
import { Platform, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

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
 * The app's own header: the FinFlow mark on the left, the screen name beside it, and a settings
 * affordance on the right.
 *
 * The settings tile is drawn but deliberately inert - the settings screen does not exist yet, and a
 * button that silently does nothing is worse than one that is visibly not active. It becomes a
 * link to `/settings` when that screen is built.
 */
export function ProfileHeader({ title = 'Profile' }: { title?: string }) {
  const theme = useTheme();
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

      <View
        style={[
          styles.settingsTile,
          { borderColor: theme.border, backgroundColor: theme.backgroundElement },
        ]}>
        <Icon name="sliders" size={20} color={theme.textSecondary} />
      </View>
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
  settingsTile: {
    width: 40,
    height: 40,
    borderRadius: Radius.pill,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
