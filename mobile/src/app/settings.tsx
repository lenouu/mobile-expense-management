import { router } from 'expo-router';
import { useEffect } from 'react';
import { Alert } from 'react-native';

import { useCurrentSession, useSession } from '@/providers';
import { SettingsScreen } from '@/screens/settings/settings-screen';

/**
 * Settings, opened from the gear on the Profile screen.
 *
 * Off the tab bar on purpose - a screen you navigate to and come back from, not a destination.
 * The base currency chosen here is pushed straight into the session, so the expense form records
 * in it immediately rather than after the next sign-in.
 */
export default function SettingsRoute() {
  const session = useCurrentSession();
  const { signIn, signOut } = useSession();

  // The tabs are only reachable while signed in, but a restored URL can still arrive after a
  // sign-out. Redirecting in an effect keeps render free of side effects.
  useEffect(() => {
    if (!session) router.replace('/auth/sign-in');
  }, [session]);

  if (!session) return null;

  return (
    <SettingsScreen
      session={session}
      onBaseCurrencyChange={(baseCurrency) => signIn({ ...session, baseCurrency })}
      onSignOut={() => {
        signOut();
        router.replace('/auth/sign-in');
      }}
      onAuthError={(message) => {
        Alert.alert('Session ended', message);
        signOut();
        router.replace('/auth/sign-in');
      }}
    />
  );
}
