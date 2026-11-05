import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type PasswordRequirement = {
  key: string;
  label: string;
  met: boolean;
};

/**
 * The live password checklist from the Figma "Password Security Checklist".
 *
 * Shown grey until the rule is satisfied and green afterwards, so the user can see progress
 * while typing instead of discovering the rules one 400 response at a time. The rules are
 * produced by `passwordRequirements` in `features/auth/lib/password.ts`, which is the single
 * place they are defined.
 */
export function PasswordChecklist({
  requirements,
  title = 'Security requirements',
}: {
  requirements: PasswordRequirement[];
  title?: string;
}) {
  const theme = useTheme();

  return (
    <View style={[styles.container, { backgroundColor: theme.surfaceSubtle, borderColor: theme.border }]}>
      <ThemedText type="overline" themeColor="textSecondary">
        {title}
      </ThemedText>

      <View style={styles.grid}>
        {requirements.map((requirement) => (
          <View key={requirement.key} style={styles.item}>
            <View
              style={[
                styles.dot,
                { backgroundColor: requirement.met ? Status.success : theme.backgroundSelected },
              ]}>
              <ThemedText style={styles.dotMark}>{requirement.met ? '✓' : ''}</ThemedText>
            </View>
            <ThemedText
              type="caption"
              themeColor={requirement.met ? 'textSecondary' : 'textMuted'}
              style={styles.itemLabel}>
              {requirement.label}
            </ThemedText>
          </View>
        ))}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    borderRadius: Radius.field,
    borderWidth: 1,
    paddingHorizontal: Spacing.two + Spacing.half,
    paddingTop: Spacing.three - 2,
    paddingBottom: Spacing.two + Spacing.half,
    gap: Spacing.one + Spacing.half,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    rowGap: Spacing.one + Spacing.half,
  },
  item: {
    // Two columns, as in the design. `flexBasis` rather than a fixed width so the row still
    // works on a very narrow phone or a wide tablet.
    flexBasis: '50%',
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one + Spacing.half,
    paddingRight: Spacing.two,
  },
  dot: {
    width: 14,
    height: 14,
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dotMark: {
    color: '#FFFFFF',
    fontSize: 9,
    lineHeight: 12,
    fontWeight: '700',
  },
  itemLabel: {
    flexShrink: 1,
  },
});
