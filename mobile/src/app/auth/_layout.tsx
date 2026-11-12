import { Stack } from 'expo-router';

import { useTheme } from '@/hooks/use-theme';

/**
 * The unauthenticated journey: signing in and creating an account.
 *
 * The stack animates a crossfade between the two screens on purpose. Without an explicit
 * animation the platform cut from one to the other, and combined with each screen's own entrance
 * fade that read as: old screen vanishes, blank, content appears. A crossfade keeps both screens
 * on screen while they swap, so the swap is a dissolve rather than a jump.
 */
export default function AuthLayout() {
  const theme = useTheme();

  return (
    <Stack
      screenOptions={{
        headerShown: false,
        contentStyle: { backgroundColor: theme.background },
        animation: 'fade',
        // Slightly shorter than the screens' own 320 ms entrance, so the two do not end together
        // in a way that looks like the content stopped moving before the screen did.
        animationDuration: 280,
        gestureEnabled: true,
      }}
    />
  );
}
