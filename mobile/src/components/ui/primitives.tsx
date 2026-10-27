import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Shadows, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// The design system's interactive building blocks, shared by every feature.

export const SUCCESS_COLOR = Status.success;
export const DANGER_COLOR = Status.danger;

type ButtonProps = {
  label: string;
  onPress: () => void;
  /** Filled brand-green call to action. Use it once per screen. */
  primary?: boolean;
  /** Outlined, quieter action. This is the default. */
  secondary?: boolean;
  disabled?: boolean;
  /** Placed on a card, whose surface is already `backgroundElement`. */
  onCard?: boolean;
  style?: StyleProp<ViewStyle>;
};

/**
 * The app's single button.
 *
 * The brand-green `primary` variant should appear once per screen; everything else stays
 * `secondary` so the eye always knows where to go first.
 */
export function Button({ label, onPress, primary, disabled, onCard, style }: ButtonProps) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityState={{ disabled: !!disabled }}
      style={({ pressed }) => [style, (pressed || disabled) && styles.pressed]}>
      {primary ? (
        <View style={[styles.button, styles.primary, Shadows.button]}>
          <ThemedText type="smallBold" style={styles.primaryLabel}>
            {label}
          </ThemedText>
        </View>
      ) : (
        <View
          style={[
            styles.button,
            styles.secondary,
            {
              backgroundColor: onCard ? theme.backgroundSelected : theme.backgroundElement,
              borderColor: theme.border,
            },
          ]}>
          <ThemedText type="smallBold">{label}</ThemedText>
        </View>
      )}
    </Pressable>
  );
}

/** A selectable pill, used for section switchers. */
export function Chip({
  label,
  selected,
  onPress,
  onCard,
}: {
  label: string;
  selected: boolean;
  onPress: () => void;
  onCard?: boolean;
}) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={({ pressed }) => pressed && styles.pressed}>
      <View
        style={[
          styles.chip,
          {
            backgroundColor: selected
              ? theme.backgroundSelected
              : onCard
                ? theme.background
                : theme.backgroundElement,
            borderColor: theme.border,
          },
        ]}>
        <ThemedText type="small" themeColor={selected ? 'text' : 'textSecondary'}>
          {label}
        </ThemedText>
      </View>
    </Pressable>
  );
}

/** A short status word ("ACTIVE", "DOWN") with a matching tint. */
export function Badge({
  label,
  tone = 'neutral',
}: {
  label: string;
  tone?: 'neutral' | 'success' | 'danger';
}) {
  const color =
    tone === 'success' ? Status.success : tone === 'danger' ? Status.danger : Brand.navy;

  return (
    <View style={[styles.badge, { borderColor: color }]}>
      <ThemedText type="caption" style={{ color }}>
        {label}
      </ThemedText>
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    borderRadius: Radius.field,
    paddingHorizontal: Radius.card,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primary: {
    backgroundColor: Brand.green,
  },
  primaryLabel: {
    color: '#FFFFFF',
  },
  secondary: {
    minHeight: 44,
    borderWidth: 1,
  },
  chip: {
    borderRadius: Radius.pill,
    borderWidth: 1,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
  },
  badge: {
    borderRadius: Radius.pill,
    borderWidth: 1,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.half,
    alignSelf: 'flex-start',
  },
  pressed: {
    opacity: 0.6,
  },
});
