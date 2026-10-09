import { StyleSheet, type StyleProp, type ViewProps, type ViewStyle } from 'react-native';

import { ThemedView } from '@/components/themed-view';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type CardProps = ViewProps & {
  /** Removes the inner padding, for a card that holds a full-bleed list or image. */
  flush?: boolean;
  style?: StyleProp<ViewStyle>;
};

/**
 * The white (or dark) panel that content sits on, per the Figma "Registration Form Container":
 * 16pt radius, a hairline border and a soft shadow lifting it off the page background.
 */
export function Card({ flush, style, ...rest }: CardProps) {
  const theme = useTheme();

  return (
    <ThemedView
      style={[
        styles.card,
        Shadows.card,
        { borderColor: theme.border, padding: flush ? 0 : Spacing.three },
        style,
      ]}
      {...rest}
    />
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: Radius.card,
    borderWidth: 1,
    width: '100%',
  },
});
