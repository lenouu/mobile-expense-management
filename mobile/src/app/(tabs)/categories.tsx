import { router } from 'expo-router';
import { useEffect } from 'react';
import { Alert } from 'react-native';

import { AppHeader } from '@/components/ui/app-header';
import { BackButton } from '@/components/ui/back-button';
import { CategoriesManager } from '@/features/finances/components/categories-manager';
import { useCurrentSession, useSession } from '@/providers';

/**
 * The user's own categories, reached from the Expenses header.
 *
 * Categories belong to the expense domain - you need one while recording - but managing them is
 * a separate job from recording, so they get their own screen rather than a third segment on the
 * Expenses tab.
 */
export default function CategoriesRoute() {
  const session = useCurrentSession();
  const { signOut } = useSession();

  useEffect(() => {
    if (!session) router.replace('/auth/sign-in');
  }, [session]);

  if (!session) return null;

  return (
    <CategoriesManager
      session={session}
      contentStyle={undefined}
      header={
        <AppHeader
          title="Categories"
          action={<BackButton onPress={() => router.back()} />}
        />
      }
      onSignOut={(message) => {
        Alert.alert('Session ended', message ?? 'Please sign in again.');
        signOut();
        router.replace('/auth/sign-in');
      }}
    />
  );
}
