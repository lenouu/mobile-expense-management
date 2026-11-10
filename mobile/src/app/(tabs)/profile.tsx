import { router } from 'expo-router';
import { useEffect } from 'react';
import { Alert } from 'react-native';

import { useCurrentSession } from '@/providers';
import { ProfileScreen } from '@/screens/profile/profile-screen';

/**
 * The signed-in user's own profile, opened from the Dashboard.
 *
 * The same screen the admin console shows, drawn without console chrome because an ordinary user
 * has no console header. It sits off the tab bar - a screen you navigate to, not a destination -
 * and the Back button returns to wherever you came from.
 */
export default function ProfileRoute() {
  const session = useCurrentSession();

  // The tabs are only reachable while signed in, but a restored URL or deep link can still arrive
  // here after a sign-out. Redirecting in an effect keeps render free of side effects.
  useEffect(() => {
    if (!session) router.replace('/auth/sign-in');
  }, [session]);

  if (!session) return null;

  return (
    <ProfileScreen
      session={session}
      consoleChrome={false}
      onAuthError={(message) => {
        Alert.alert('Session ended', message);
        router.replace('/auth/sign-in');
      }}
    />
  );
}
