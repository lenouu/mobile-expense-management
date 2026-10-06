import { useCallback, useState } from 'react';
import { Platform, ScrollView, StyleSheet, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Button, Chip } from '@/components/admin/admin-ui';
import { CategoriesManager } from '@/components/finances/categories-manager';
import { ExpensesManager } from '@/components/finances/expenses-manager';
import { describeError, financeStyles, TextField } from '@/components/finances/finance-ui';
import { IncomesManager } from '@/components/finances/incomes-manager';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { ApiRequestError, Session } from '@/services/api-client';
import { userLogin } from '@/services/finances';

type Section = 'expenses' | 'incomes' | 'categories';

const SECTIONS: { key: Section; label: string }[] = [
  { key: 'expenses', label: 'Expenses' },
  { key: 'incomes', label: 'Incomes' },
  { key: 'categories', label: 'Categories' },
];

/**
 * A regular user's money: expenses and incomes (S2-TECH-2) and their own categories (US17).
 */
export default function FinancesScreen() {
  const [session, setSession] = useState<Session | null>(null);
  const [section, setSection] = useState<Section>('expenses');
  // Why the user was sent back to sign in (e.g. session expired).
  const [signInMessage, setSignInMessage] = useState<string | null>(null);
  const signOut = useCallback((message?: string) => {
    setSession(null);
    setSignInMessage(message ?? null);
  }, []);

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
          <UserSignIn initialError={signInMessage} onSignedIn={setSession} />
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
  signIn: {
    flexGrow: 1,
    maxWidth: 420,
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.five,
  },
});
