import { router } from 'expo-router';
import { useCallback } from 'react';
import { KeyboardAvoidingView, Platform, Pressable, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { BackButton } from '@/components/ui/back-button';
import { BrandLockup } from '@/components/ui/brand-lockup';
import { Card } from '@/components/ui/card';
import { Checkbox } from '@/components/ui/checkbox';
import { PasswordChecklist } from '@/components/ui/password-checklist';
import { Button } from '@/components/ui/primitives';
import { StepBadge } from '@/components/ui/step-badge';
import { TextField } from '@/components/ui/text-field';
import { Radius, Spacing, Status } from '@/constants/theme';
import { useSignUpForm } from '@/features/auth/hooks/use-sign-up-form';
import { passwordRequirements } from '@/features/auth/lib/password';
import type { Account, Session } from '@/types/api';

/**
 * Account creation, laid out to match the FinFlow sign-up design (see figmaInspiration/).
 *
 * The fields are exactly the ones `POST /auth/register` accepts - first name, last name,
 * username, email, password and an optional date of birth. The design's phone-number and
 * base-currency rows are deliberately absent because the backend has no columns for them; add
 * them here once `UserRequests.CreateAccount` grows those fields.
 */
export function SignUpScreen() {
  const onSignedIn = useCallback(({ account, session }: { account: Account; session: Session }) => {
    // `session` is stored by the dashboard, which reads it from the session provider. Passing the
    // first name through the URL keeps the confirmation personal without another request.
    router.replace({
      pathname: '/',
      params: { welcome: account.firstName, user: session.userName },
    });
  }, []);

  const { form, errors, submitting, setField, submit } = useSignUpForm(onSignedIn);

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
        <View style={styles.header}>
          <BackButton onPress={goBack} />
          <StepBadge step={1} total={2} />
          {/* Balances the back button so the badge stays centred. */}
          <View style={styles.headerSpacer} />
        </View>

        <BrandLockup
          title="Create Account"
          subtitle="Start tracking, understanding, and improving your financial life."
        />

        <Card style={styles.formCard}>
          <View style={styles.form}>
            <View style={styles.nameRow}>
              <View style={styles.nameField}>
                <TextField
                  label="First name"
                  placeholder="e.g. Amara"
                  autoCapitalize="words"
                  autoComplete="given-name"
                  textContentType="givenName"
                  returnKeyType="next"
                  value={form.firstName}
                  onChangeText={(value) => setField('firstName', value)}
                  error={errors.firstName}
                />
              </View>
              <View style={styles.nameField}>
                <TextField
                  label="Last name"
                  placeholder="e.g. Johnson"
                  autoCapitalize="words"
                  autoComplete="family-name"
                  textContentType="familyName"
                  returnKeyType="next"
                  value={form.lastName}
                  onChangeText={(value) => setField('lastName', value)}
                  error={errors.lastName}
                />
              </View>
            </View>

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
              label="Date of birth (optional)"
              placeholder="DD/MM/YYYY"
              keyboardType={Platform.OS === 'web' ? undefined : 'numbers-and-punctuation'}
              maxLength={10}
              value={form.dateOfBirth}
              onChangeText={(value) => setField('dateOfBirth', value)}
              error={errors.dateOfBirth}
              hint={errors.dateOfBirth ? undefined : 'Used for age-based insights. You can add it later.'}
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

        <View style={styles.footer}>
          <ThemedText type="caption" themeColor="textSecondary">
            Already have an account?{' '}
          </ThemedText>
          <Pressable
            onPress={() => router.replace('/auth/sign-in')}
            accessibilityRole="link"
            hitSlop={Spacing.two}>
            <ThemedText type="caption" style={styles.link}>
              Log In
            </ThemedText>
          </Pressable>
        </View>
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
    justifyContent: 'space-between',
  },
  headerSpacer: {
    width: 40,
  },
  formCard: {
    gap: 0,
  },
  form: {
    gap: Spacing.three,
  },
  nameRow: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  nameField: {
    flex: 1,
    // Keeps both halves usable on a narrow phone instead of squeezing them to nothing.
    minWidth: 130,
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
