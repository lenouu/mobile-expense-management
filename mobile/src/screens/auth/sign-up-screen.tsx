import { router } from 'expo-router';
import { useCallback, useEffect } from 'react';
import { Animated, KeyboardAvoidingView, Platform, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { BackButton } from '@/components/ui/back-button';
import { BrandLockup } from '@/components/ui/brand-lockup';
import { Card } from '@/components/ui/card';
import { FadeIn } from '@/components/ui/fade-in';
import { Checkbox } from '@/components/ui/checkbox';
import { PasswordChecklist } from '@/components/ui/password-checklist';
import { Button } from '@/components/ui/primitives';
import { TextField } from '@/components/ui/text-field';
import { Radius, Spacing, Status } from '@/constants/theme';
import { useShake } from '@/hooks/use-shake';
import { useSignUpForm } from '@/features/auth/hooks/use-sign-up-form';
import { passwordRequirements } from '@/features/auth/lib/password';
import type { Account, Session } from '@/types/api';

/**
 * Account creation, laid out to match the FinFlow sign-up design (see figmaInspiration/).
 *
 * Only what the account cannot exist without: email, username, password and the terms. First
 * name, last name and date of birth are optional on the server and are filled in later from the
 * Profile screen, so the first screen a new user sees is four answers rather than eight.
 */
export function SignUpScreen() {
  const onSignedIn = useCallback(({ account, session }: { account: Account; session: Session }) => {
    // `session` is stored by the dashboard, which reads it from the session provider. The name is
    // usually empty at sign-up now, so the greeting falls back to the username.
    router.replace({
      pathname: '/',
      params: { welcome: account.firstName || session.userName, user: session.userName },
    });
  }, []);

  const { form, errors, submitting, setField, submit } = useSignUpForm(onSignedIn);

  // A refused sign-up says no out loud: the card shakes once, the message stays red.
  const { offset, shake } = useShake();
  const hasErrors = Object.keys(errors).length > 0;

  useEffect(() => {
    if (hasErrors) shake();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hasErrors]);

  const goBack = useCallback(() => {
    if (router.canGoBack()) {
      router.back();
      return;
    }
    // Reached directly (deep link, or a reload on web), so there is nothing to go back to.
    router.replace('/auth/sign-in');
  }, []);

  return (
    <KeyboardAvoidingView
      style={styles.flex}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <Screen withTabInset={false} contentStyle={styles.content}>
        <FadeIn>
          <View style={styles.header}>
            <BackButton onPress={goBack} />
          </View>
        </FadeIn>

        <FadeIn delay={60}>
          <BrandLockup
            floating
            title="Create Account"
            subtitle="Start tracking, understanding, and improving your financial life."
          />
        </FadeIn>

        <FadeIn delay={120}>
        <Animated.View style={{ transform: [{ translateX: offset }] }}>
          <Card style={styles.formCard}>
          <View style={styles.form}>
            <TextField
              label="Email address"
              placeholder="name@example.com"
              keyboardType="email-address"
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="email"
              textContentType="emailAddress"
              returnKeyType="next"
              value={form.email}
              onChangeText={(value) => setField('email', value)}
              error={errors.email}
            />

            <TextField
              label="Username"
              placeholder="e.g. amara.johnson"
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="username"
              textContentType="username"
              returnKeyType="next"
              value={form.userName}
              onChangeText={(value) => setField('userName', value)}
              error={errors.userName}
              hint={errors.userName ? undefined : 'At least 3 characters, no spaces'}
            />

            <TextField
              label="Password"
              placeholder="Create a strong password"
              autoCapitalize="none"
              autoCorrect={false}
              autoComplete="new-password"
              textContentType="newPassword"
              returnKeyType="done"
              revealable
              secureTextEntry
              value={form.password}
              onChangeText={(value) => setField('password', value)}
              error={errors.password}
              onSubmitEditing={submit}
            />

            <PasswordChecklist requirements={passwordRequirements(form.password)} />

            <View>
              <Checkbox
                checked={form.acceptedTerms}
                onChange={(checked) => setField('acceptedTerms', checked)}>
                <ThemedText type="caption" themeColor="textSecondary">
                  I agree to the{' '}
                  <ThemedText type="caption" style={styles.link}>
                    Terms of Service
                  </ThemedText>{' '}
                  and{' '}
                  <ThemedText type="caption" style={styles.link}>
                    Privacy Policy
                  </ThemedText>
                  .
                </ThemedText>
              </Checkbox>
              {errors.acceptedTerms ? (
                <ThemedText type="caption" style={styles.error}>
                  {errors.acceptedTerms}
                </ThemedText>
              ) : null}
            </View>

            {errors.form ? (
              <View style={styles.banner}>
                <ThemedText type="caption" style={styles.bannerText}>
                  {errors.form}
                </ThemedText>
              </View>
            ) : null}

            <Button
              primary
              label={submitting ? 'Creating account…' : 'Create Account'}
              onPress={submit}
              disabled={submitting}
            />
          </View>
          </Card>
        </Animated.View>
        </FadeIn>

        <FadeIn delay={180}>
          <View style={styles.footer}>
            <ThemedText type="caption" themeColor="textSecondary">
              Already have an account?{' '}
            </ThemedText>
            <Pressable
              onPress={() => router.push('/auth/sign-in')}
              accessibilityRole="link"
              hitSlop={Spacing.two}>
              <ThemedText type="caption" style={styles.link}>
                Log In
              </ThemedText>
            </Pressable>
          </View>
        </FadeIn>
      </Screen>
    </KeyboardAvoidingView>
  );
}

// Route files import the screen by this name; see src/app/auth/sign-up.tsx.
export default SignUpScreen;

const styles = StyleSheet.create({
  flex: {
    flex: 1,
  },
  content: {
    gap: Spacing.four,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
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
  error: {
    color: Status.danger,
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
