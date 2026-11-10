import { StyleSheet, View } from 'react-native';

import { AppHeader } from '@/components/ui/app-header';
import { Icon } from '@/components/ui/icon';
import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The profile screen's header: the shared app header with a settings affordance on the right.
 *
 * The settings tile is drawn but deliberately inert - the settings screen does not exist yet, and
 * a button that silently does nothing is worse than one that is visibly not active. It becomes a
 * link to `/settings` when that screen is built.
 */
export function ProfileHeader({ title = 'Profile' }: { title?: string }) {
  const theme = useTheme();

  return (
    <AppHeader
      title={title}
      action={
        <View
          style={[
            styles.settingsTile,
            { borderColor: theme.border, backgroundColor: theme.backgroundElement },
          ]}>
          <Icon name="sliders" size={20} color={theme.textSecondary} />
        </View>
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
});
