import { useCallback, useState } from 'react';
import { Alert } from 'react-native';
import { router } from 'expo-router';

import type { DefaultCategory } from '@/features/admin/lib/admin-api';
import { rangeByKey, type AdminRangeKey } from '@/features/admin/lib/admin-range';
import { AdminAnalyticsScreen } from '@/screens/admin/admin-analytics-screen';
import { AdminCategoriesScreen } from '@/screens/admin/admin-categories-screen';
import { AdminGuard } from '@/screens/admin/admin-guard';
import { AdminHomeScreen } from '@/screens/admin/admin-home-screen';
import { AdminLogsScreen } from '@/screens/admin/admin-logs-screen';
import { AdminUsersScreen } from '@/screens/admin/admin-users-screen';

export type ConsoleTab = 'home' | 'analytics' | 'users' | 'categories' | 'logs';

/**
 * The admin console.
 *
 * All four tabs and the audit-log screen share one session, one time range and the category list,
 * so switching tabs keeps the range you chose and the Analytics screen can rank categories the
 * Categories screen already loaded. Each screen keeps its own data fetch, which is why changing
 * the range there refetches only what that screen needs.
 *
 * Authorisation lives in `AdminGuard`: it is the check that holds even when the route is reached
 * without going through the tab bar.
 */
export function AdminConsole({ tab }: { tab: ConsoleTab }) {
  const [rangeKey, setRangeKey] = useState<AdminRangeKey>('7d');
  const [categories, setCategories] = useState<DefaultCategory[]>([]);
  const range = rangeByKey(rangeKey);

  const onAuthError = useCallback((message: string) => {
    // A 401/403 from any console screen ends the session in one place: rather than each screen
    // deciding what to do, they hand the message up and the user is sent back to sign in.
    Alert.alert('Session ended', message);
    router.replace('/auth/sign-in');
  }, []);

  return (
    <AdminGuard>
      {(session) => {
        switch (tab) {
          case 'analytics':
            return (
              <AdminAnalyticsScreen
                session={session}
                range={range}
                onRangeChange={setRangeKey}
                categories={categories}
              />
            );
          case 'users':
            return (
              <AdminUsersScreen
                session={session}
                range={range}
                onRangeChange={setRangeKey}
                onAuthError={onAuthError}
              />
            );
          case 'categories':
            return (
              <AdminCategoriesScreen
                session={session}
                onCategoriesLoaded={setCategories}
                onAuthError={onAuthError}
              />
            );
          case 'logs':
            return (
              <AdminLogsScreen
                session={session}
                range={range}
                onRangeChange={setRangeKey}
                onAuthError={onAuthError}
                onBack={() => router.replace('/admin')}
              />
            );
          case 'home':
          default:
            return <AdminHomeScreen session={session} range={range} onRangeChange={setRangeKey} />;
        }
      }}
    </AdminGuard>
  );
}
