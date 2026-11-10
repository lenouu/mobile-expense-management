import { Tabs } from 'expo-router';

import { Icon, type IconName } from '@/components/ui/icon';
import { Brand, Colors } from '@/constants/theme';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useSession } from '@/providers';

/**
 * The one bottom bar, which shows a different set of tabs depending on who is signed in.
 *
 * An administrator gets the console - Home, Analytics, Users, Categories - and does not get the
 * consumer screens, because an admin account has no expenses or incomes of its own to manage and
 * the mock-ups give the console its own four tabs. Everyone else gets Dashboard and Money.
 *
 * `href: null` removes a route from the bar while leaving it reachable by URL, which is how the
 * Audit Logs screen stays off the bar but openable from Home's Administrative Controls.
 */
export default function TabsLayout() {
  const scheme = useColorScheme();
  const colors = Colors[scheme ?? 'light'];
  const { session } = useSession();
  const isAdmin = session?.type === 'ADMIN';

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: Brand.green,
        tabBarInactiveTintColor: colors.textSecondary,
        // Signed out, the app is showing its single login screen, so the bar would only offer two
        // destinations the user cannot use yet. It appears as soon as there is a session.
        tabBarStyle: session
          ? { backgroundColor: colors.backgroundElement, borderTopColor: colors.border }
          : { display: 'none' },
      }}>
      {/* Consumer screens: hidden from an administrator. */}
      <Tabs.Screen
        name="index"
        options={{
          title: 'Dashboard',
          href: isAdmin ? null : '/',
          tabBarIcon: ({ color, size }) => <Icon name="home" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="expenses"
        options={{
          title: 'Expenses',
          href: isAdmin ? null : '/expenses',
          tabBarIcon: ({ color, size }) => <Icon name="cart" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="budget"
        options={{
          title: 'Budget',
          href: isAdmin ? null : '/budget',
          tabBarIcon: ({ color, size }) => <Icon name="wallet" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="profile"
        options={{
          title: 'Profile',
          href: isAdmin ? null : '/profile',
          tabBarIcon: ({ color, size }) => <Icon name="person" size={size} color={color} />,
        }}
      />

      {/* The old single "Money" screen. Off the bar now that its contents are being split into
          the tabs above; kept reachable by URL until the Expenses screen replaces it. */}
      <Tabs.Screen name="finances" options={{ href: null }} />

      {/* Admin console: hidden from everyone who is not an administrator. */}
      <Tabs.Screen
        name="admin"
        options={{
          title: 'Home',
          href: isAdmin ? '/admin' : null,
          tabBarIcon: ({ color, size }) => <Icon name="home" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="admin-analytics"
        options={{
          title: 'Analytics',
          href: isAdmin ? '/admin-analytics' : null,
          tabBarIcon: ({ color, size }) => <Icon name="analytics" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="admin-users"
        options={{
          title: 'Users',
          href: isAdmin ? '/admin-users' : null,
          tabBarIcon: ({ color, size }) => <Icon name="users" size={size} color={color} />,
        }}
      />
      <Tabs.Screen
        name="admin-categories"
        options={{
          title: 'Categories',
          href: isAdmin ? '/admin-categories' : null,
          tabBarIcon: ({ color, size }) => <Icon name="categories" size={size} color={color} />,
        }}
      />

      {/* Reachable from the Expenses header; never on the bar. */}
      <Tabs.Screen name="categories" options={{ href: null }} />

      {/* Reachable from Home's Administrative Controls; never on the bar. */}
      <Tabs.Screen name="admin-logs" options={{ href: null }} />
    </Tabs>
  );
}

/** Kept for the Metro plugin: an unused reference to the icon type avoids a lint warning. */
export type TabIconName = IconName;
