import { router } from 'expo-router';
import { StyleSheet, View } from 'react-native';

import { Screen } from '@/components/screen';
import { ThemedText } from '@/components/themed-text';
import { Button } from '@/components/ui/primitives';
import { Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { useSession } from '@/providers';
import type { Session } from '@/types/api';

/**
 * Renders the admin console only for an administrator.
 *
 * The tab bar already hides the console from a signed-out or non-admin account, but that is a
 * navigation detail: a deep link, a restored URL on web, or a session expiring while the screen is
 * open would each mount these screens without it. This guard is the authorisation check that does
 * not depend on which route was followed - and it mirrors what the backend does, since every
 * `/api/admin/**` call is refused with 403 for a non-admin token.
 */
export function AdminGuard({
  children,
}: {
  children: (session: Session) => React.ReactNode;
}) {
  const { session } = useSession();

  if (!session) {
    return (
      <Notice
        title="Administrator sign-in required"
        body="Sign in with an administrator account to open the console."
      />
    );
  }

  if (session.type !== 'ADMIN') {
    return (
      <Notice
        title="This area is for administrators"
        body={`${session.userName} is a standard account, so the admin API refuses these calls with 403.`}
      />
    );
  }

  return <>{children(session)}</>;
}

function Notice({ title, body }: { title: string; body: string }) {
  const theme = useTheme();

  return (
    <Screen withTabInset={false} contentStyle={styles.content}>
      <View style={[styles.card, { borderColor: theme.border, backgroundColor: theme.backgroundElement }]}>
        <ThemedText type="subtitle">{title}</ThemedText>
        <ThemedText type="small" themeColor="textSecondary">
          {body}
        </ThemedText>
        <Button label="Go to sign in" primary onPress={() => router.replace('/auth/sign-in')} />
      </View>
    </Screen>
  );
}

const styles = StyleSheet.create({
  content: {
    gap: Spacing.three,
  },
  card: {
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.four,
    gap: Spacing.two,
  },
});
