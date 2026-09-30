import { Pressable, StyleSheet } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { ApiRequestError } from '@/services/admin-monitoring';

// Small building blocks shared by the admin sections (US10 monitoring, US09 categories).

export const SUCCESS_COLOR = '#1F9D55';
export const DANGER_COLOR = '#D93025';

export function Button({
  label,
  onPress,
  disabled,
  secondary,
  onCard,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  secondary?: boolean;
  /** Secondary button placed on a card, which already uses the backgroundElement color. */
  onCard?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [(pressed || disabled) && styles.pressed]}>
      <ThemedView
        type={secondary ? (onCard ? 'backgroundSelected' : 'backgroundElement') : 'text'}
        style={[styles.button, secondary && styles.buttonSecondary]}>
        <ThemedText type="smallBold" themeColor={secondary ? 'text' : 'background'}>
          {label}
        </ThemedText>
      </ThemedView>
    </Pressable>
  );
}

export function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => pressed && styles.pressed}>
      <ThemedView type={selected ? 'backgroundSelected' : 'backgroundElement'} style={styles.chip}>
        <ThemedText type="small" themeColor={selected ? 'text' : 'textSecondary'}>
          {label}
        </ThemedText>
      </ThemedView>
    </Pressable>
  );
}

export function describeError(e: unknown): string {
  if (e instanceof ApiRequestError) {
    if (e.status === 401) return 'Please sign in again.';
    if (e.status === 403) return 'This account is not an administrator.';
    return e.message;
  }
  return 'Something went wrong.';
}

const styles = StyleSheet.create({
  button: {
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two + Spacing.one,
    alignItems: 'center',
  },
  buttonSecondary: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  chip: {
    borderRadius: Spacing.four,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
  },
  pressed: {
    opacity: 0.6,
  },
});
