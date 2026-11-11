import { StyleSheet, Text, View } from 'react-native';

import { Icon, type IconName } from '@/components/ui/icon';
import { Radius } from '@/constants/theme';
import { withAlpha } from '@/utils/color';

/**
 * The names the seeded default categories use for their icon.
 *
 * They were stored as symbol names before users could choose an emoji, so they are translated
 * here rather than migrated: an existing category keeps the mark it always had, and anything that
 * cannot be translated falls back to the colour dot.
 */
const LEGACY_ICONS: Record<string, IconName> = {
  cart: 'cart',
  restaurant: 'restaurant',
  car: 'car',
  home: 'home',
  bolt: 'bolt',
  heart: 'heart',
  book: 'document',
  film: 'camera',
  bag: 'wallet',
  dots: 'more',
};

/** True when the stored value is a pictograph rather than a symbol name. */
export function isEmojiIcon(value: string): boolean {
  return [...value].some((character) => (character.codePointAt(0) ?? 0) >= 0x2190);
}

/**
 * The symbol a stored legacy name stands for, or undefined when it is not one.
 *
 * Exported because the icon picker needs the same translation: a seeded category's icon is a name
 * like "cart", and the picker must show that mark rather than the word.
 */
export function legacyIconName(value: string): IconName | undefined {
  return isEmojiIcon(value) ? undefined : LEGACY_ICONS[value];
}

/**
 * A category's mark: its emoji on a tint of its colour, its legacy symbol, or just the colour.
 *
 * This is the one place the two stored fields - `icon` and `color` - are shown together, which is
 * what makes picking them in the form feel worth doing.
 */
export function CategoryBadge({
  icon,
  color,
  size = 40,
}: {
  icon: string | null;
  /** `#RRGGBB`. */
  color: string;
  size?: number;
}) {
  const glyphSize = Math.round(size * 0.5);
  const legacy = icon ? legacyIconName(icon) : undefined;

  return (
    <View
      style={[
        styles.badge,
        {
          width: size,
          height: size,
          borderRadius: Radius.pill,
          backgroundColor: withAlpha(color, 0.18),
          borderColor: withAlpha(color, 0.4),
        },
      ]}>
      {icon && isEmojiIcon(icon) ? (
        <Text style={{ fontSize: glyphSize, lineHeight: glyphSize + 6 }}>{icon}</Text>
      ) : legacy ? (
        <Icon name={legacy} size={glyphSize} color={color} />
      ) : (
        <View
          style={{
            width: size * 0.35,
            height: size * 0.35,
            borderRadius: size * 0.175,
            backgroundColor: color,
          }}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  badge: {
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
