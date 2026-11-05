import { Image } from 'expo-image';
import { StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Shadows, Spacing } from '@/constants/theme';

/**
 * The logo emblem and page title from the top of the Figma sign-up screen.
 *
 * The 64pt navy tile is the Figma "Logo Emblem"; the artwork inside is the project's own
 * `assets/images/logo.png`. It is shared by sign-up and any future auth screen so the brand
 * block is never redrawn by hand.
 */
export function BrandLockup({ title, subtitle }: { title: string; subtitle?: string }) {
  return (
    <View style={styles.container}>
      <View style={[styles.emblem, Shadows.emblem]}>
        <Image
          source={require('@/assets/images/logo.png')}
          style={styles.logo}
          contentFit="contain"
          accessibilityLabel="FinFlow logo"
        />
      </View>

      <View style={styles.copy}>
        <ThemedText type="heading" style={styles.centered}>
          {title}
        </ThemedText>
        {subtitle ? (
          <ThemedText type="caption" themeColor="textSecondary" style={styles.centered}>
            {subtitle}
          </ThemedText>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    gap: Spacing.three - 3,
  },
  emblem: {
    width: 64,
    height: 64,
    borderRadius: Radius.card,
    borderWidth: 1,
    borderColor: '#F1F5F9',
    backgroundColor: Brand.navy,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.half + 1,
  },
  logo: {
    width: '100%',
    height: '100%',
    borderRadius: Radius.field,
  },
  copy: {
    alignItems: 'center',
    gap: Spacing.half,
    maxWidth: 320,
  },
  centered: {
    textAlign: 'center',
  },
});
