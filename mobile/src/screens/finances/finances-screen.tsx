import { useCallback, useState } from 'react';
import { router } from 'expo-router';
import { Platform, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button, Chip } from '@/components/ui/primitives';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, Spacing } from '@/constants/theme';
import { CategoriesManager } from '@/features/finances/components/categories-manager';
import { ExpensesManager } from '@/features/finances/components/expenses-manager';
import { describeError, financeStyles, TextField } from '@/features/finances/components/finance-ui';
import { IncomesManager } from '@/features/finances/components/incomes-manager';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { userLogin } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
import type { Session } from '@/types/api';

type Section = 'expenses' | 'incomes' | 'categories';

const SECTIONS: { key: Section; label: string }[] = [
  { key: 'expenses', label: 'Expenses' },
  { key: 'incomes', label: 'Incomes' },
  { key: 'categories', label: 'Categories' },
];

/**
 * A regular user's money: expenses and incomes (S2-TECH-2) and their own categories (US17).
 */
export function FinancesScreen() {
  const { session: signedIn, signOut: signOutOfApp } = useSession();
  // Fallback for someone who signed in from this tab rather than the login screen. Once the
  // login screen is the only way in, this can go.
  const [localSession, setLocalSession] = useState<Session | null>(null);
  const session = signedIn ?? localSession;
  const [section, setSection] = useState<Section>('expenses');
  // Why the user was sent back to sign in (e.g. session expired).
  const [signInMessage, setSignInMessage] = useState<string | null>(null);
  const signOut = useCallback(
    (message?: string) => {
      setLocalSession(null);
      setSignInMessage(message ?? null);
      // A session that came from the login screen lives in the provider, so it has to be cleared
      // there too - otherwise signing out here would leave the Dashboard convinced you are in.
      if (signedIn) signOutOfApp();
    },
    [signedIn, signOutOfApp],
  );

  const safeAreaInsets = useSafeAreaInsets();
  const theme = useTheme();
  const contentPlatformStyle = Platform.select({
    android: {
      paddingTop: safeAreaInsets.top,
      paddingBottom: safeAreaInsets.bottom + BottomTabInset + Spacing.three,
    },
    ios: { paddingBottom: Spacing.three },
    web: { paddingTop: Spacing.six + Spacing.four, paddingBottom: Spacing.four },
  });

  // An administrator has no money of its own to record. The tab bar already hides this screen
  // from an ADMIN session, so this is the guard for reaching it another way - a deep link, or a
  // URL restored on web - and it stops the admin from being shown a second sign-in form.
  // The hooks above run before this return, so the component's hook order never changes.
  if (signedIn?.type === 'ADMIN') {
    return (
      <ThemedView style={styles.screen}>
        <ScrollView
          style={{ backgroundColor: theme.background }}
          contentContainerStyle={[financeStyles.contentContainer, contentPlatformStyle]}>
          <View style={styles.adminNotice}>
            <ThemedText type="subtitle">Not available for administrators</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Expenses, incomes and personal categories belong to the account that owns them, and an
              administrator account has none. The admin API deliberately exposes no way to read
              another user&apos;s money.
            </ThemedText>
            <Button label="Go to the admin console" primary onPress={() => router.replace('/admin')} />
          </View>
        </ScrollView>
      </ThemedView>
    );
  }

  const sectionSwitcher = (
    <View style={financeStyles.row}>
      {SECTIONS.map((s) => (
        <Chip key={s.key} label={s.label} selected={section === s.key} onPress={() => setSection(s.key)} />
      ))}
    </View>
  );

  return (
    <ThemedView style={styles.screen}>
      {session && section === 'expenses' && (
        <ExpensesManager
          session={session}
          contentStyle={contentPlatformStyle}
          onSignOut={signOut}
          header={sectionSwitcher}
        />
      )}
      {session && section === 'incomes' && (
        <IncomesManager
          session={session}
          contentStyle={contentPlatformStyle}
          onSignOut={signOut}
          header={sectionSwitcher}
        />
      )}
      {session && section === 'categories' && (
        <CategoriesManager
          session={session}
          contentStyle={contentPlatformStyle}
          onSignOut={signOut}
          header={sectionSwitcher}
        />
      )}
      {!session && (
        <ScrollView
          style={{ backgroundColor: theme.background }}
          contentInsetAdjustmentBehavior="automatic"
          contentContainerStyle={[financeStyles.contentContainer, contentPlatformStyle]}
          keyboardShouldPersistTaps="handled">
          <UserSignIn initialError={signInMessage} onSignedIn={setLocalSession} />
        </ScrollView>
      )}
    </ThemedView>
  );
}

function UserSignIn({
  initialError,
  onSignedIn,
}: {
  initialError: string | null;
  onSignedIn: (session: Session) => void;
}) {
  const [usernameOrEmail, setUsernameOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(initialError);
  const [loading, setLoading] = useState(false);

  const signIn = async () => {
    if (!usernameOrEmail || !password) {
      setError('Enter your username (or email) and password.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      onSignedIn(await userLogin(usernameOrEmail, password));
    } catch (e) {
      setError(e instanceof ApiRequestError && e.status === 401 ? 'Wrong username or password.' : describeError(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.signIn}>
      <ThemedText type="subtitle">My money</ThemedText>
      <ThemedText themeColor="textSecondary">Sign in to record your expenses and incomes.</ThemedText>

      <TextField
        placeholder="Username or email"
        autoCapitalize="none"
        autoCorrect={false}
        value={usernameOrEmail}
        onChangeText={setUsernameOrEmail}
      />
      <TextField
        placeholder="Password"
        secureTextEntry
        value={password}
        onChangeText={setPassword}
        onSubmitEditing={signIn}
      />

      {error && <ThemedText style={financeStyles.errorText}>{error}</ThemedText>}

      <Button label={loading ? 'Signing in…' : 'Sign in'} onPress={signIn} disabled={loading} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  adminNotice: {
    flexGrow: 1,
    maxWidth: 420,
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.five,
  },
  signIn: {
    flexGrow: 1,
    maxWidth: 420,
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.five,
  },
});