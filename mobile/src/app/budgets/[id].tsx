import { router, useLocalSearchParams } from 'expo-router';
import { useEffect } from 'react';

import { useCurrentSession } from '@/providers';
import { BudgetDetailScreen } from '@/screens/budget/budget-detail-screen';

/**
 * One budget, opened from the Budget tab.
 *
 * Pushed over the tabs rather than living inside them: it is a screen you come back from, and the
 * back arrow has somewhere to return to.
 */
export default function BudgetDetailRoute() {
  const session = useCurrentSession();
  const { id } = useLocalSearchParams<{ id: string }>();

  useEffect(() => {
    if (!session) router.replace('/auth/sign-in');
  }, [session]);

  if (!session || !id) return null;

  return <BudgetDetailScreen session={session} budgetId={id} />;
}
