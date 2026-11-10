import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { AppHeader } from '@/components/ui/app-header';
import { FadeIn } from '@/components/ui/fade-in';
import { SegmentedNav, type SegmentItem } from '@/components/ui/segmented-nav';
import { TopToast, type ToastMessage } from '@/components/ui/top-toast';
import { Spacing } from '@/constants/theme';
import { CategoriesManager } from '@/features/finances/components/categories-manager';
import { useTheme } from '@/hooks/use-theme';
import { useCurrentSession, useSession } from '@/providers';

const SEGMENTS: SegmentItem[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'transactions', label: 'Transactions' },
  { key: 'categories', label: 'Categories' },
];

/**
 * The Expenses tab, split into three sections.
 *
 * Each section answers one question - what did I spend and on what (Overview), what exactly did I
 * record (Transactions), and how is my spending organised (Categories) - instead of stacking all
 * of it into one long scroll, which buried the controls above the content.
 *
 * Only Categories is built so far, so it is the segment the tab opens on; the other two say
 * plainly that they are next rather than showing an empty screen.
 */
export default function ExpensesRoute() {
  const session = useCurrentSession();
  const { signOut } = useSession();
  const theme = useTheme();
  const [segment, setSegment] = useState('categories');
  // The toast belongs to the screen, not to a segment: it has to overlay the header and the tabs,
  // which are drawn above whatever a segment renders.
  const [toast, setToast] = useState<ToastMessage | null>(null);

  useEffect(() => {
    if (!session) router.replace('/auth/sign-in');
  }, [session]);

  if (!session) return null;

  return (
    <View style={[styles.screen, { backgroundColor: theme.background }]}>
      <AppHeader title="Expenses" />
      <SegmentedNav items={SEGMENTS} value={segment} onChange={setSegment} />

      {/* Keyed on the segment so the content fades in on every switch, and sized to fill the
          space left under the fixed header and tabs. */}
      <FadeIn key={segment} style={styles.body}>
        {segment === 'categories' ? (
          <CategoriesManager
            session={session}
            contentStyle={undefined}
            onToast={(kind, message) => setToast({ id: Date.now(), message, kind })}
            onSignOut={(message) => {
              Alert.alert('Session ended', message ?? 'Please sign in again.');
              signOut();
              router.replace('/auth/sign-in');
            }}
          />
        ) : (
          <View style={styles.pending}>
            <ThemedText type="small" themeColor="textMuted" style={styles.pendingText}>
              {segment === 'overview'
                ? 'The overview - this window’s total, its categories and how it compares - is the next piece to build.'
                : 'The list of recorded expenses is the next piece to build.'}
            </ThemedText>
          </View>
        )}
      </FadeIn>

      {/* Last child, so it paints over the header and the segment bar rather than under them. */}
      <TopToast toast={toast} onDismiss={() => setToast(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  body: {
    flex: 1,
  },
  pending: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.four,
  },
  pendingText: {
    textAlign: 'center',
    maxWidth: 320,
  },
});
