import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type CheckboxProps = {
  checked: boolean;
  onChange: (checked: boolean) => void;
  /** The sentence next to the box; may contain nested `ThemedText` for the links. */
  children: React.ReactNode;
};

/**
 * A 20pt checkbox with a 4pt radius, per the Figma "Terms of Service Checkbox".
 *
 * The whole row is the touch target rather than just the box, because a 20pt square is well
 * under the 44pt minimum for a comfortable tap.
 */
export function Checkbox({ checked, onChange, children }: CheckboxProps) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={() => onChange(!checked)}
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
      <View
        style={[
          styles.box,
          checked
            ? { backgroundColor: Brand.green, borderColor: Brand.green }
            : { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        ]}>
        {checked ? (
          <ThemedText type="caption" style={styles.check}>
            ✓
          </ThemedText>
        ) : null}
      </View>

      <View style={styles.label}>{children}</View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two + Spacing.one,
    paddingVertical: Spacing.one,
  },
  box: {
    width: 20,
    height: 20,
    borderRadius: Radius.small,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: Spacing.half,
  },
  check: {
    color: '#FFFFFF',
    fontSize: 13,
    lineHeight: 16,
    fontWeight: '700',
  },
  label: {
    flex: 1,
  },
  pressed: {
    opacity: 0.7,
  },
});
