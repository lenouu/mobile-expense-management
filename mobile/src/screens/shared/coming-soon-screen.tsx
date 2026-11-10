import { StyleSheet, View } from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { Icon, type IconName } from '@/components/ui/icon';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The stand-in for a tab whose screen has not been built yet.
 *
 * Deliberately plain and honest: it says what the screen will do, rather than showing fake
 * figures or an empty list that looks like a bug.
 */
export function ComingSoonScreen({
  icon,
  title,
  message,
}: {
  icon: IconName;
  title: string;
  /** One or two sentences on what this screen will hold. */
  message: string;
}) {
  const theme = useTheme();

  return (
    <Screen contentStyle={styles.content}>
      <View
        style={[
          styles.card,
          { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        ]}>
        <View style={[styles.iconTile, { backgroundColor: theme.iconTile }]}>
          <Icon name={icon} size={26} color={theme.text} />
        </View>

        <ThemedText type="title">{title}</ThemedText>
        <ThemedText type="small" themeColor="textSecondary" style={styles.message}>
          {message}
        </ThemedText>
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: Spacing.three,
  },
  card: {
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.four,
    gap: Spacing.two,
    alignItems: 'center',
  },
  iconTile: {
    width: 56,
    height: 56,
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: Spacing.one,
  },
  message: {
    textAlign: 'center',
  },
});
