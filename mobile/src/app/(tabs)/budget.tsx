import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { AppHeader } from '@/components/ui/app-header';
import { TopToast, type ToastKind, type ToastMessage } from '@/components/ui/top-toast';
import { useCurrentSession, useSession } from '@/providers';
import { BudgetScreen } from '@/screens/budget/budget-screen';

/**
 * The Budget tab.
 *
 * A thin route: the screen owns the plan, the history and its animations, while the header, the
 * toast layer and the sign-out handoff live here with the rest of the shell.
 */
export default function BudgetRoute() {
  const session = useCurrentSession();
  const { signOut } = useSession();
  const [toast, setToast] = useState<ToastMessage | null>(null);

  // The tabs are only reachable while signed in, but a restored URL can still arrive after a
  // sign-out. Redirecting in an effect keeps render free of side effects.
  useEffect(() => {
    if (!session) router.replace('/auth/sign-in');
  }, [session]);

  if (!session) return null;

  const showToast = (kind: ToastKind, message: string) =>
    setToast({ id: Date.now(), message, kind });

  const handleSessionEnd = (message?: string) => {
    Alert.alert('Session ended', message ?? 'Please sign in again.');
    signOut();
    router.replace('/auth/sign-in');
  };

  return (
    <View style={styles.screen}>
      <AppHeader title="Budget" />

      <BudgetScreen session={session} onToast={showToast} onSignOut={handleSessionEnd} />

      {/* Last child, so it paints over the header rather than under it. */}
      <TopToast toast={toast} onDismiss={() => setToast(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
});
