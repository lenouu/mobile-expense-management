import { router } from 'expo-router';
import { Pressable, StyleSheet } from 'react-native';

import { AppHeader } from '@/components/ui/app-header';
import { Icon } from '@/components/ui/icon';
import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The profile screen's header: the shared app header with a settings affordance on the right.
 *
 * The tile opens `/settings`, which is where the preferences that change what the numbers mean
 * live - the base currency being the first of them.
 */
export function ProfileHeader({ title = 'Profile' }: { title?: string }) {
  const theme = useTheme();

  return (
    <AppHeader
      title={title}
      action={
        <Pressable
          onPress={() => router.push('/settings')}
          accessibilityRole="button"
          accessibilityLabel="Settings"
          style={({ pressed }) => [
            styles.settingsTile,
            { borderColor: theme.border, backgroundColor: theme.backgroundElement },
            pressed && styles.pressed,
          ]}>
          <Icon name="sliders" size={20} color={theme.textSecondary} />
        </Pressable>
      }
    />
  );
}

const styles = StyleSheet.create({
  settingsTile: {
    width: 40,
    height: 40,
    borderRadius: Radius.pill,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.7,
  },
});
