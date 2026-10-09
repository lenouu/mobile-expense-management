import { Stack } from 'expo-router';

import { useTheme } from '@/hooks/use-theme';

/** The unauthenticated journey: account creation and, later, sign-in. */
export default function AuthLayout() {
  const theme = useTheme();

  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: theme.background },
      }}
    />
  );
}
