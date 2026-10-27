import { Image } from 'expo-image';
import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { Pill } from '@/features/admin/components/console-ui';
import { useTheme } from '@/hooks/use-theme';

/**
 * The admin console's top bar, as drawn in every admin mock-up: the FinFlow mark, an "Admin
 * console" label, the current screen's name under it, then a bell and the signed-in avatar.
 *
 * It lives above each screen rather than in the navigator's header so the screens keep
 * `headerShown: false` and stay in control of their own scroll behaviour.
 */
export function ConsoleHeader({
  title,
  initials,
  onBellPress,
}: {
  /** The screen name, e.g. "Home" or "User Accounts". */
  title: string;
  /** Avatar letters, from the signed-in account. */
  initials: string;
  onBellPress?: () => void;
}) {
  const theme = useTheme();

  return (
    <View style={styles.header}>
      <View style={styles.brand}>
        <Image
          source={require('@/assets/images/logo.png')}
          style={styles.logo}
          contentFit="contain"
          accessibilityLabel="FinFlow"
        />
        <View style={styles.brandCopy}>
          <Pill label="ADMIN CONSOLE" tint="brand" />
          <ThemedText type="small" themeColor="textSecondary">
            {title}
          </ThemedText>
        </View>
      </View>

      <View style={styles.actions}>
        <Pressable
          onPress={onBellPress}
          accessibilityRole="button"
          accessibilityLabel="Notifications"
          hitSlop={8}
          style={({ pressed }) => pressed && styles.pressed}>
          <Icon name="bell" size={22} color={theme.text} />
        </Pressable>

        <View style={[styles.avatar, { borderColor: theme.border }]}>
          <ThemedText type="caption" style={styles.avatarText}>
            {initials}
          </ThemedText>
        </View>
      </View>
    </View>
  );
}

/** `Amina Haddad` -> `AH`. Kept here so the header has no opinion about where the name came from. */
export function initialsFrom(userName: string): string {
  const parts = userName.split(/[.\s_-]+/).filter(Boolean);
  if (parts.length === 0) return 'AD';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
}

const styles = StyleSheet.create({
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.three,
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
  brandCopy: {
    gap: Spacing.half,
    flexShrink: 1,
  },
  actions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  avatar: {
    width: 36,
    height: 36,
    borderRadius: Radius.pill,
    borderWidth: 2,
    backgroundColor: Brand.navy,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    color: '#FFFFFF',
    fontWeight: '700',
  },
  pressed: {
    opacity: 0.6,
  },
});
