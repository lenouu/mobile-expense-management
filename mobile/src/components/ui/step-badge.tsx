import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The "STEP 1 OF 2" pill from the Figma header.
 *
 * Sign-up is a single screen today, so the step count is passed in and the badge stays a
 * presentational component: when the second step (base currency, phone number) is added it can
 * be reused without touching this file.
 */
export function StepBadge({ step, total }: { step: number; total: number }) {
  const theme = useTheme();

  return (
    <View
      style={[
        styles.pill,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
      ]}>
      <View style={styles.dot} />
      <ThemedText type="overline" themeColor="textSecondary">
        Step {step} of {total}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.pill,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one + Spacing.half,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: Radius.pill,
    backgroundColor: '#01916D',
  },
});
