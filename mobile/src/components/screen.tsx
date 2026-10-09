import { Platform, ScrollView, StyleSheet, View, type ScrollViewProps } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type ScreenProps = {
  children: React.ReactNode;
  /**
   * `scroll` for forms and lists, `fixed` for a screen that manages its own scrolling
   * (for example one built around a `FlatList`).
   */
  variant?: 'scroll' | 'fixed';
  /** Reserve room at the bottom for the tab bar. Off for auth screens, which have no tabs. */
  withTabInset?: boolean;
  contentStyle?: ScrollViewProps['contentContainerStyle'];
};

/**
 * The page frame: background colour, safe-area padding, max width on tablets and web.
 *
 * Screens use this instead of a bare `View` so that every page agrees on its gutters and no
 * screen has to know about safe areas or the tab bar height.
 */
export function Screen({
  children,
  variant = 'scroll',
  withTabInset = true,
  contentStyle,
}: ScreenProps) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const padding = {
    paddingTop: Platform.OS === 'web' ? Spacing.four : insets.top + Spacing.three,
    paddingBottom:
      (Platform.OS === 'web' ? Spacing.four : insets.bottom + Spacing.three) +
      (withTabInset ? BottomTabInset : 0),
    paddingLeft: insets.left,
    paddingRight: insets.right,
  };

  if (variant === 'fixed') {
    return (
      <ThemedView style={[styles.fill, padding, contentStyle]}>
        <View style={styles.centered}>{children}</View>
      </ThemedView>
    );
  }

  return (
    <ScrollView
      style={[styles.fill, { backgroundColor: theme.background }]}
      contentContainerStyle={[styles.contentContainer, padding, contentStyle]}
      keyboardShouldPersistTaps="handled"
      keyboardDismissMode="on-drag">
      <View style={styles.centered}>{children}</View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  fill: {
    flex: 1,
  },
  contentContainer: {
    flexGrow: 1,
    alignItems: 'center',
  },
  centered: {
    width: '100%',
    maxWidth: MaxContentWidth,
    gap: Spacing.three,
  },
});
