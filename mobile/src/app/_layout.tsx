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
        {/* Explicit rather than "auto": once the theme can be forced in Settings, the status bar
            has to follow the theme the app is drawing, not the device's setting. */}
        <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
        <SplashOverlay />
        <Stack
          screenOptions={{
            headerShown: false,
            contentStyle: { backgroundColor: theme.background },
            // Crossing between the auth screens and the signed-in app is a replace on this stack:
            // a crossfade turns the swap into a dissolve instead of a cut.
            animation: 'fade',
            animationDuration: 280,
          }}>
          <Stack.Screen name="(tabs)" />
          <Stack.Screen name="auth" />
          {/* Settings is pushed over the tabs rather than living inside them: it is a screen you
              return from, and a hidden tab would be switched to instead of opened. */}
          <Stack.Screen name="settings" options={{ animation: 'slide_from_right' }} />
          {/* One budget, opened from the Budget tab. */}
          <Stack.Screen name="budgets/[id]" options={{ animation: 'slide_from_right' }} />
        </Stack>
      </SessionProvider>
    </ThemeProvider>
  );
}
