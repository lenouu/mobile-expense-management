import { router } from 'expo-router';
import { useCallback, useState } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { BackButton } from '@/components/ui/back-button';
import { BrandLockup } from '@/components/ui/brand-lockup';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/primitives';
import { TextField } from '@/components/ui/text-field';
import { WelcomeModal } from '@/components/ui/welcome-modal';
import { Radius, Spacing, Status } from '@/constants/theme';
import { useLoginForm } from '@/features/auth/hooks/use-login-form';
import { homeRouteFor, welcomeMessageFor } from '@/features/auth/lib/login';
import { useSession } from '@/providers';
import type { Session } from '@/types/api';

/** How long the confirmation stays up before the redirect. Long enough to read, short enough not to annoy. */
const WELCOME_DISMISS_MS = 1800;

const ROUTE_LABELS: Record<string, string> = {
  '/admin': 'the Admin area',
  '/finances': 'your Money',
};

/**
 * Sign in, for both account types.
 *
 * One screen rather than two, because the role is a property of the account, not of the form:
 * `POST /auth/login` answers with the account's `type`, and this screen routes on it. That is why
 * an admin cannot end up on the user screens (or the reverse) by opening the wrong tab - the
 * decision is made from the token, not from which link was tapped.
 *
 * Laid out to match the sign-up screen, so the two read as one flow.
 */
export function SignInScreen() {
  const { signIn } = useSession();
  const [pending, setPending] = useState<Session | null>(null);

  const onAuthenticated = useCallback(
    (session: Session) => {
      // Store it first: the destination screens read the session from the provider, so it has to
      // exist before the redirect lands there.
      signIn(session);
      setPending(session);
    },
    [signIn],
  );

  const { form, errors, submitting, setField, submit } = useLoginForm(onAuthenticated);

  const finishRedirect = useCallback(() => {
    if (!pending) return;
    setPending(null);
    router.replace(homeRouteFor(pending.type));
  }, [pending]);

  const goBack = useCallback(() => {
    if (router.canGoBack()) {
      router.back();
      return;
    }
    router.replace('/');
  }, []);

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <Screen withTabInset={false} contentStyle={styles.content}>
        <View style={styles.header}>
          <BackButton onPress={goBack} />
        </View>

        <BrandLockup title="Welcome back" subtitle="Sign in to pick up where you left off." />

        <Card style={styles.formCard}>
          <View style={styles.form}>
            <TextField
              label="Username or email"
              placeholder="e.g. amara.johnson or name@example.com"
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="username"
              textContentType="username"
              returnKeyType="next"
              value={form.identifier}
              onChangeText={(value) => setField('identifier', value)}
              error={errors.identifier}
            />

            <TextField
              label="Password"
              placeholder="Your password"
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="current-password"
              textContentType="password"
              returnKeyType="go"
              revealable
              secureTextEntry
              value={form.password}
              onChangeText={(value) => setField('password', value)}
              error={errors.password}
              onSubmitEditing={submit}
            />

            {errors.form ? (
              <View style={styles.banner}>
                <ThemedText type="caption" style={styles.bannerText}>
                  {errors.form}
                </ThemedText>
              </View>
            ) : null}

            <Button
              primary
              label={submitting ? 'Signing in…' : 'Log In'}
              onPress={submit}
              disabled={submitting}
            />
          </View>
        </Card>

        <View style={styles.footer}>
          <ThemedText type="caption" themeColor="textSecondary">
            Don&apos;t have an account?{' '}
          </ThemedText>
          <Pressable
            onPress={() => router.replace('/auth/sign-up')}
            accessibilityRole="link"
            hitSlop={Spacing.two}>
            <ThemedText type="caption" style={styles.link}>
              Create one
            </ThemedText>
          </Pressable>
        </View>
      </Screen>

      <WelcomeModal
        visible={pending !== null}
        tone={pending?.type === 'ADMIN' ? 'admin' : 'user'}
        title={pending ? welcomeMessageFor(pending) : ''}
        message={
          pending?.type === 'ADMIN'
            ? 'Administrator account detected — sending you to the admin area.'
            : 'Signed in successfully.'
        }
        destination={
          pending ? (ROUTE_LABELS[String(homeRouteFor(pending.type))] ?? 'your account') : ''
        }
        autoDismissMs={WELCOME_DISMISS_MS}
        onDismiss={finishRedirect}
      />
    </KeyboardAvoidingView>
  );
}

export default SignInScreen;

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
  content: {
    gap: Spacing.four,
  },
  header: {
    flexDirection: 'row',
  },
  formCard: {
    gap: 0,
  },
  form: {
    gap: Spacing.three,
  },
  link: {
    color: '#01916D',
  },
  banner: {
    borderRadius: Radius.field,
    borderWidth: 1,
    borderColor: Status.danger,
    backgroundColor: 'rgba(217, 48, 37, 0.06)',
    padding: Spacing.two + Spacing.half,
  },
  bannerText: {
    color: Status.danger,
  },
  footer: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
  },
});
