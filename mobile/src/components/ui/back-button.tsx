import { Pressable, StyleSheet, View } from 'react-native';

import { Radius } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The circular back control from the Figma header.
 *
 * The chevron is drawn with two rotated borders rather than pulled in as an icon font, because
 * the project ships no icon dependency. If `@expo/vector-icons` is added later, swap the inner
 * `View`s for an `Ionicons` glyph and leave the touch target exactly as it is.
 */
export function BackButton({ onPress, label = 'Go back' }: { onPress: () => void; label?: string }) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      hitSlop={12}
      style={({ pressed }) => [
        styles.button,
        { backgroundColor: theme.backgroundElement, borderColor: theme.border },
        pressed && styles.pressed,
      ]}>
      <View style={[styles.chevron, { borderColor: theme.text }]} />
    </Pressable>
  );
}

const styles = StyleSheet.create({
  button: {
    width: 40,
    height: 40,
    borderRadius: Radius.pill,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  chevron: {
    width: 9,
    height: 9,
    borderLeftWidth: 1.8,
    borderBottomWidth: 1.8,
    transform: [{ rotate: '45deg' }],
    // Nudged right so the glyph looks optically centred inside the circle.
    marginLeft: -2,
  },
  pressed: {
    opacity: 0.6,
  },
});
