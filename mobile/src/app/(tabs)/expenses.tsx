import { router } from 'expo-router';
import { useEffect, useState } from 'react';
import { Alert, StyleSheet, View } from 'react-native';

import { AppHeader } from '@/components/ui/app-header';
import { FadeIn } from '@/components/ui/fade-in';
import { SegmentedNav, type SegmentItem } from '@/components/ui/segmented-nav';
import { TopToast, type ToastKind, type ToastMessage } from '@/components/ui/top-toast';
import { CategoriesManager } from '@/features/finances/components/categories-manager';
import { OverviewSegment } from '@/features/finances/components/overview-segment';
import { SmsCaptureManager } from '@/features/finances/components/sms-capture-manager';
import { TransactionsSegment } from '@/features/finances/components/transactions-segment';
import { useTheme } from '@/hooks/use-theme';
import { useCurrentSession, useSession } from '@/providers';

const SEGMENTS: SegmentItem[] = [
  { key: 'overview', label: 'Overview' },
  { key: 'transactions', label: 'Transactions' },
  { key: 'categories', label: 'Categories' },
  // S6-TECH-2: add an expense from a pasted mobile money SMS.
  { key: 'sms', label: 'SMS' },
];

/**
 * The Expenses tab, split into sections.
 *
 * Each section answers one question - what did I spend and on what (Overview), what exactly did I
 * record (Transactions), how is my spending organised (Categories), and the shortcut of adding one
 * from a mobile money SMS (SMS, S6-TECH-2) - instead of stacking all of it into one long scroll,
 * which buried the controls above the content.
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

  const showToast = (kind: ToastKind, message: string) =>
    setToast({ id: Date.now(), message, kind });

  const handleSessionEnd = (message?: string) => {
    Alert.alert('Session ended', message ?? 'Please sign in again.');
    signOut();
    router.replace('/auth/sign-in');
  };

  return (
    <View style={[styles.screen, { backgroundColor: theme.background }]}>
      <AppHeader title="Expenses & Income" />
      <SegmentedNav items={SEGMENTS} value={segment} onChange={setSegment} />

      {/* Keyed on the segment so the content fades in on every switch, and sized to fill the
          space left under the fixed header and tabs. */}
      <FadeIn key={segment} style={styles.body}>
        {segment === 'categories' ? (
          <CategoriesManager
            session={session}
            contentStyle={undefined}
            onToast={showToast}
            onSignOut={handleSessionEnd}
          />
        ) : segment === 'transactions' ? (
          <TransactionsSegment
            session={session}
            onToast={showToast}
            onSignOut={handleSessionEnd}
          />
        ) : segment === 'sms' ? (
          <SmsCaptureManager session={session} onToast={showToast} onSignOut={handleSessionEnd} />
        ) : (
          <OverviewSegment session={session} onSignOut={handleSessionEnd} />
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
});
