import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { BackButton } from '@/components/ui/back-button';
import { BrandLockup } from '@/components/ui/brand-lockup';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/primitives';
import { Spacing } from '@/constants/theme';

/**
 * Placeholder sign-in screen, so the "Log In" link on sign-up is never a dead end.
 *
 * It is intentionally not wired to `POST /auth/login` yet: the auth tab still owns its own
 * sign-in flow, and duplicating that here would give the app two competing places to sign in
 * from. When the Money screen is moved onto the session provider, this screen takes over the
 * login call and the placeholder card is replaced by the sign-in form.
 */
export function SignInScreen() {
  return (
    <Screen withTabInset={false} contentStyle={styles.content}>
      <View style={styles.header}>
        <BackButton
          onPress={() => (router.canGoBack() ? router.back() : router.replace('/auth/sign-up'))}
        />
      </View>

      <BrandLockup
        title="Welcome back"
        subtitle="Sign in to pick up where you left off."
      />

      <Card style={styles.card}>
        <ThemedText type="subtitle">Sign-in is coming next</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          Account creation is finished. The sign-in form is the next screen to build — until then,
          the Money and Admin tabs each have their own sign-in step.
        </ThemedText>

        <ThemedText type="small" themeColor="textSecondary">
          Already created an account? Continue to the app and sign in from the tab you need.
        </ThemedText>

        <View style={styles.actions}>
          <Button label="Continue to the app" primary onPress={() => router.replace('/')} />
          <Button label="Create an account" onPress={() => router.replace('/auth/sign-up')} />
        </View>
      </Card>
    </Screen>
  );
}

export default SignInScreen;

const styles = StyleSheet.create({
  content: {
    gap: Spacing.four,
  },
  header: {
    flexDirection: 'row',
  },
  card: {
    gap: Spacing.two,
  },
  actions: {
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
});
