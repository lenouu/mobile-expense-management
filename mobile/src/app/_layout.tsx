import { DarkTheme, DefaultTheme, Stack, ThemeProvider } from 'expo-router';
import { StatusBar } from 'expo-status-bar';

import { SplashOverlay } from '@/components/splash-overlay';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useTheme } from '@/hooks/use-theme';
import { SessionProvider } from '@/providers';

/**
 * The app's bootstrap: providers, the splash hand-off, then the single root navigator.
 *
 * Two stacks sit side by side - the tabbed shell in `(tabs)/` for the signed-in app, and the
 * `auth/` group for signing up and signing in. The auth screens are not tabs because they are
 * pushed from anywhere in the app.
 */
export default function RootLayout() {
  const scheme = useColorScheme();
  const theme = useTheme();

  return (
    <ThemeProvider value={scheme === 'dark' ? DarkTheme : DefaultTheme}>
      <SessionProvider>
        <StatusBar style="auto" />
        <SplashOverlay />
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: theme.background },
          }}>
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="auth" />
        </Stack>
      </SessionProvider>
    </ThemeProvider>
  );
}
