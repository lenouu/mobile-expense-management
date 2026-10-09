import { router, useLocalSearchParams } from 'expo-router';
import { useCallback, useEffect, useRef } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { BrandLockup } from '@/components/ui/brand-lockup';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/primitives';
import { Brand, Radius, Shadows, Spacing, Status } from '@/constants/theme';
import { useSession } from '@/providers';

/**
 * The landing tab, and the only screen a fresh install can reach.
 *
 * There is no analytics dashboard yet, so this screen's job is to confirm what the session
 * provider currently holds: a signed-out state with a way in, and a signed-in state with the
 * confirmation shown right after sign-up. The charts land here once the reporting endpoints
 * exist - nothing else about the navigation has to change.
 */
export function DashboardScreen() {
  const { session, signOut } = useSession();
  const params = useLocalSearchParams<{ welcome?: string }>();

  return (
    <Screen contentStyle={styles.content}>
      <BrandLockup
        title={session ? `Welcome, ${session.userName}` : 'FinFlow'}
        subtitle={
          session
            ? 'You are signed in.'
            : 'Track. Understand. Improve. Sign in to see your money, or create an account to get started.'
        }
      />

      {session ? (
        <SignedInCard
          userName={session.userName}
          showWelcome={!!params.welcome}
          onSignOut={signOut}
        />
      ) : (
        <SignedOutCard />
      )}
    </Screen>
  );
}

/** The success state after sign-up: the account exists and the session was stored. */
function SignedInCard({
  userName,
  showWelcome,
  onSignOut,
}: {
  userName: string;
  showWelcome: boolean;
  onSignOut: () => void;
}) {
  return (
    <Card style={styles.card}>
      {showWelcome ? <WelcomeBanner /> : null}

      <ThemedText type="subtitle">Log in successful</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        Signed in as <ThemedText type="smallBold">{userName}</ThemedText>. Your default categories
        have been created, so the Money tab is ready when you are.
      </ThemedText>

      <View style={styles.actions}>
        <Button label="Go to Money" primary onPress={() => router.replace('/finances')} />
        <Button label="Sign out" onPress={onSignOut} />
      </View>

      <ThemedText type="caption" themeColor="textMuted">
        This is a placeholder dashboard. Charts and summaries will be added here.
      </ThemedText>
    </Card>
  );
}

function SignedOutCard() {
  return (
    <Card style={styles.card}>
      <ThemedText type="subtitle">Get started</ThemedText>
      <ThemedText type="small" themeColor="textSecondary">
        New here? Create an account and your default expense categories are set up automatically.
      </ThemedText>

      <View style={styles.actions}>
        <Button label="Create account" primary onPress={() => router.push('/auth/sign-up')} />
        <Button label="Sign in" onPress={() => router.push('/auth/sign-in')} />
      </View>
    </Card>
  );
}

/** Fades and lifts in once, then stays put. */
function WelcomeBanner() {
  const progress = useRef(new Animated.Value(0)).current;

  useEffect(() => {
    Animated.timing(progress, {
      toValue: 1,
      duration: 420,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: true,
    }).start();
  }, [progress]);

  const translateY = progress.interpolate({ inputRange: [0, 1], outputRange: [-8, 0] });

  return (
    <Animated.View style={[styles.banner, { opacity: progress, transform: [{ translateY }] }]}>
      <ThemedText type="smallBold" style={styles.bannerText}>
        Account created — you are signed in.
      </ThemedText>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: Spacing.four,
  },
  card: {
    gap: Spacing.two,
  },
  actions: {
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  banner: {
    borderRadius: Radius.field,
    borderWidth: 1,
    borderColor: Status.success,
    backgroundColor: 'rgba(31, 157, 85, 0.08)',
    padding: Spacing.two + Spacing.half,
    ...Shadows.card,
    shadowColor: Brand.navy,
  },
  bannerText: {
    color: Status.success,
  },
});
