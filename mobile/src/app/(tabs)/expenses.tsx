import { router } from 'expo-router';
import { useEffect } from 'react';
import { Alert } from 'react-native';

import { AppHeader } from '@/components/ui/app-header';
import { Button } from '@/components/ui/primitives';
import { ExpensesManager } from '@/features/finances/components/expenses-manager';
import { useCurrentSession, useSession } from '@/providers';

/**
 * The Expenses tab: recording, browsing and correcting expenses.
 *
 * The window above the list filters both the list and its total, and it is a reporting range
 * (period, week, month, or any two dates) rather than the financial period - the period is only
 * one of the presets (section 6.2 of the vision).
 */
export default function ExpensesRoute() {
  const session = useCurrentSession();
  const { signOut } = useSession();

  useEffect(() => {
    if (!session) router.replace('/auth/sign-in');
  }, [session]);

  if (!session) return null;

  return (
    <ExpensesManager
      session={session}
      header={
        <AppHeader
          title="Expenses"
          action={
            <Button label="Categories" onPress={() => router.push('/categories')} secondary onCard />
          }
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
