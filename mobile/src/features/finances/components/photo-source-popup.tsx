import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Icon, type IconName } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type PhotoSource = 'camera' | 'library';

const OPTIONS: { source: PhotoSource; label: string; hint: string; icon: IconName }[] = [
  {
    source: 'camera',
    label: 'Take a picture',
    hint: 'Photograph the receipt now.',
    icon: 'camera',
  },
  {
    source: 'library',
    label: 'Upload from gallery',
    hint: 'Pick a picture you already have.',
    icon: 'upload',
  },
];

/**
 * The one question the picture route asks: where does the photo come from?
 *
 * It exists so the form can offer a single "add with a picture" entry instead of two
 * tiles, and the answer is chosen a beat later, once it is actually being asked.
 */
export function PhotoSourcePopup({
  visible,
  onClose,
  onPick,
}: {
  visible: boolean;
  onClose: () => void;
  onPick: (source: PhotoSource) => void;
}) {
  const theme = useTheme();

  return (
    <Popup visible={visible} onClose={onClose}>
      <View style={styles.body}>
        <ThemedText type="hero">Add from a picture</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          We will read the expenses from the photo and show them to you before anything is saved.
        </ThemedText>

        {OPTIONS.map((option) => (
          <Pressable
            key={option.source}
            onPress={() => onPick(option.source)}
            accessibilityRole="button"
            accessibilityLabel={option.label}
            style={({ pressed }) => [
              styles.option,
              { borderColor: theme.border, backgroundColor: theme.background },
              pressed && styles.pressed,
            ]}>
            <View style={[styles.iconWrap, { backgroundColor: theme.backgroundSelected }]}>
              <Icon name={option.icon} size={20} color={Brand.green} />
            </View>

            <View style={styles.copy}>
              <ThemedText type="subtitle">{option.label}</ThemedText>
              <ThemedText type="caption" themeColor="textMuted">
                {option.hint}
              </ThemedText>
            </View>

            <Icon name="chevronRight" size={18} color={theme.textSecondary} />
          </Pressable>
        ))}

        <Button label="Cancel" onPress={onClose} secondary onCard />
      </View>
    </Popup>
  );
}

const styles = StyleSheet.create({
  body: {
    gap: Spacing.three,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  copy: {
    flex: 1,
    gap: Spacing.half,
  },
  pressed: {
    opacity: 0.7,
  },
});
