import { useState } from 'react';
import { Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { FadeIn } from '@/components/ui/fade-in';
import { Icon } from '@/components/ui/icon';
import { Radius, Spacing } from '@/constants/theme';
import { isEmojiIcon, legacyIconName } from '@/features/finances/components/category-badge';
import { useTheme } from '@/hooks/use-theme';
import { withAlpha } from '@/utils/color';

/**
 * The emoji a category can be marked with.
 *
 * Emoji rather than a symbol font on purpose: they are already colourful, they render on every
 * device without shipping an asset, and they need no name-to-glyph mapping. The category's name
 * stays the accessible label - emoji carry none.
 *
 * Loose grouping, in the order a shopper would scan them: food, getting around, home, health,
 * sport, leisure, work, money, clothes and beauty, animals, nature and weather, symbols, and the
 * occasions that only come round once a year.
 */
const EMOJIS = [
  // Food and drink
  '🍔', '🍕', '🍟', '🌭', '🥪', '🌮', '🌯', '🥙',
  '🧆', '🍜', '🍝', '🍲', '🍛', '🍣', '🍱', '🥟',
  '🍤', '🍚', '🍙', '🍘', '🥠', '🍡', '🥧', '🍦',
  '🍧', '🍨', '🍩', '🍪', '🎂', '🍰', '🧁', '🥐',
  '🥖', '🥨', '🧀', '🍳', '🥞', '🧇', '🥓', '🍗',
  '🍖', '🥩', '🥜', '🍯', '🥛', '☕', '🍵', '🧃',
  '🥤', '🍶', '🍺', '🍻', '🥂', '🍷', '🥃', '🍸',
  '🍹', '🍾', '🧊', '🥄', '🍴', '🥡', '🧂', '🥫',

  // Getting around
  '🚌', '🚕', '🚗', '🚙', '🚐', '🚚', '🚛', '🚜',
  '🏎️', '🏍️', '🛵', '🚲', '🛴', '🚂', '🚆', '🚇',
  '✈️', '🚀', '🛸', '🚁', '⛵', '🚤', '🛳️', '⛴️',
  '⛽', '🚧', '🚦', '🗺️', '🧭', '🅿️', '🛞', '🚏',

  // Home
  '🏠', '🏡', '🏢', '🏥', '🏦', '🏨', '🏫', '🏬',
  '🏭', '🏗️', '🏚️', '🏰', '⛪', '🕌', '🕍', '💡',
  '🔌', '🔋', '🕯️', '🚿', '🛁', '🧻', '🧴', '🧼',
  '🧽', '🧹', '🧺', '🛋️', '🛏️', '🪑', '🚪', '🔧',
  '🔨', '🧰', '🧯',

  // Health
  '💊', '💉', '🩺', '🩹', '🩸', '🧬', '🦷', '🦴',
  '👓', '🕶️', '🧠', 

  // Sport
  '🏋️', '🤸', '🧘', '🚴', '🏊', '⚽', '🏀', '🏈',
  '⚾', '🎾', '🏐', '🏉', '🎱', '🏓', '🏸', '🥊',
  '🥋', '⛳', '🎿', '🏂', '🏄', '🤿', '🛹', '🤺',

  // Leisure
  '🎯', '🎳', '🎮', '🕹️', '🎲', '🧩', '🎨', '🎭',
  '🎬', '🎤', '🎧', '🎼', '🎹', '🥁', '🎷', '🎺',
  '🎸', '🎻', '📚', '📖', '📝', '✏️', '📐', '📷',
  '📹', '🎥', '📺', '📻', '🎫', '🎟️', '🎪', '🎡',

  // Work and study
  '🎓', '📱', '💻', '🖥️', '⌨️', '🖱️', '🖨️', '📊',
  '📋', '📌', '📎', '🗂️', '📁', '✂️', '📏', '🖊️',

  // Money and shopping
  '💰', '💵', '💳', '🪙', '💸', '📈', '📉', '🧾',
  '💼', '🛒', '🛍️', '🏷️', '🎁', '🎀', '🎈', '🎉',
  '🎊', '💎', '🔑', '🗝️',

  // Clothes and beauty
  '👕', '👖', '👗', '👔', '👠', '👟', '👞', '🧢',
  '🧥', '🧦', '👙', '👜', '🎒', '🧳', '🌂', '☂️',
  '💄', '💅', '💇',

  // Animals
  '🐶', '🐱', '🐭', '🐹', '🐰', '🦊', '🐻', '🐼',
  '🐨', '🐯', '🦁', '🐮', '🐷', '🐸', '🐵', '🐔',
  '🐧', '🐦', '🦆', '🦉', '🐴', '🦄', '🐝', '🦋',
  '🐢', '🐍', '🐙', '🦀', '🐟', '🐬', '🐳', '🦈',

  // Nature and weather
  '🌳', '🌴', '🌵', '🌱', '🌿', '🍀', '🍁', '🍂',
  '🌸', '🌺', '🌻', '🌷', '🌹', '💐', '🌞', '🌙',
  '⭐', '🌟', '✨', '⚡', '🔥', '💧', '❄️', '🌈',
  '🌍', '🏔️', '🏝️', '🏖️', '🏕️', '🌊',

  // Time, symbols and the occasional
  '⏰', '⌚', '🕰️', '⏳', '🗓️', '📅', '🔔', '🔕',
  '❤️', '🧡', '💛', '💚', '💙', '💜', '🤍', '🖤',
  '✅', '❌', '❗', '❓', '♻️', '🆘', '🏳️', '🚩',
  '💍', '👑', '🧸', '🎠', '🏆', '🥇', '🥈', '🥉',
];

/**
 * A dropdown for choosing a category's emoji.
 *
 * Collapsed, it is one row - the current emoji and a chevron - so the form stays short. Open, it
 * shows a scrollable grid below it, and picking one closes it again. That is the whole reason for
 * the dropdown: the grid is a long list, but it should not be part of the form's height.
 */
export function EmojiPicker({
  value,
  onChange,
  accent,
}: {
  value: string;
  onChange: (emoji: string) => void;
  /** The chosen colour, so the selected tile matches the category being built. */
  accent: string;
}) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);

  // A category seeded before emoji existed stores a symbol name such as "cart". Show the mark it
  // stands for rather than the word, and let picking an emoji replace it.
  const legacy = value ? legacyIconName(value) : undefined;
  const emoji = value && isEmojiIcon(value) ? value : '';

  const select = (emojiValue: string) => {
    onChange(emojiValue);
    setOpen(false);
  };

  return (
    <View style={styles.container}>
      <Pressable
        onPress={() => setOpen((current) => !current)}
        accessibilityRole="button"
        accessibilityLabel={value ? 'Change the icon' : 'Choose an icon'}
        accessibilityState={{ expanded: open }}
        style={({ pressed }) => [
          styles.field,
          { borderColor: theme.border, backgroundColor: theme.background },
          pressed && styles.pressed,
        ]}>
        <View
          style={[
            styles.preview,
            { backgroundColor: withAlpha(accent, 0.18), borderColor: withAlpha(accent, 0.4) },
          ]}>
          {emoji ? (
            <Text style={styles.previewGlyph}>{emoji}</Text>
          ) : legacy ? (
            <Icon name={legacy} size={18} color={accent} />
          ) : (
            <View style={[styles.emptyDot, { backgroundColor: accent }]} />
          )}
        </View>

        <ThemedText type="small" themeColor={value ? 'text' : 'textSecondary'} style={styles.fieldLabel}>
          {value ? 'Change icon' : 'Choose an icon'}
        </ThemedText>

        <Icon name={open ? 'chevronUp' : 'chevronDown'} size={20} color={theme.textSecondary} />
      </Pressable>

      {open ? (
        <FadeIn>
          <View
            style={[
              styles.frame,
              { borderColor: theme.border, backgroundColor: theme.background },
            ]}>
            <ScrollView
              style={styles.scroller}
              contentContainerStyle={styles.grid}
              keyboardShouldPersistTaps="handled"
              // The grid scrolls inside the pop-up's own touch area.
              nestedScrollEnabled>
              {EMOJIS.map((emoji) => {
                const selected = value === emoji;

                return (
                  <Pressable
                    key={emoji}
                    onPress={() => select(selected ? '' : emoji)}
                    accessibilityRole="button"
                    accessibilityLabel={`Use ${emoji}`}
                    accessibilityState={{ selected }}
                    style={({ pressed }) => [
                      styles.tile,
                      selected && { borderColor: accent, backgroundColor: withAlpha(accent, 0.2) },
                      pressed && styles.pressed,
                    ]}>
                    <Text style={styles.glyph}>{emoji}</Text>
                  </Pressable>
                );
              })}
            </ScrollView>

            <View style={[styles.footer, { borderTopColor: theme.border }]}>
              <ThemedText type="caption" themeColor="textMuted">
                {value ? 'Tap the chosen emoji again to clear it' : 'Scroll for more'}
              </ThemedText>
            </View>
          </View>
        </FadeIn>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: Spacing.two,
  },
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  fieldLabel: {
    flex: 1,
  },
  preview: {
    width: 36,
    height: 36,
    borderRadius: Radius.pill,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewGlyph: {
    fontSize: 18,
    lineHeight: 24,
  },
  emptyDot: {
    width: 12,
    height: 12,
    borderRadius: 6,
  },
  frame: {
    borderWidth: 1,
    borderRadius: Radius.field,
    overflow: 'hidden',
  },
  /** A fixed height, so a few hundred emoji never push the form taller than the screen. */
  scroller: {
    height: 200,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
    padding: Spacing.two,
  },
  tile: {
    width: 40,
    height: 40,
    borderRadius: Radius.field,
    borderWidth: 1,
    borderColor: 'transparent',
    alignItems: 'center',
    justifyContent: 'center',
  },
  glyph: {
    fontSize: 20,
    lineHeight: 26,
  },
  footer: {
    borderTopWidth: 1,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.one,
  },
  pressed: {
    opacity: 0.6,
  },
});
