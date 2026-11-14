import { ReactNode, useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Button } from '@/components/ui/primitives';
import type { ToastKind } from '@/components/ui/top-toast';
import { BottomTabInset, Spacing } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import {
  Field,
  financeStyles as styles,
  TextField,
} from '@/features/finances/components/finance-ui';
import { MessagePreviewPopup } from '@/features/finances/components/message-preview-popup';
import { notifyDataChanged } from '@/features/finances/lib/data-events';
import { fetchMyCategories, type MyCategory } from '@/features/finances/lib/finances-api';
import { fetchIncomeSources, type IncomeSource } from '@/features/finances/lib/income-api';
import { scanMessage, type ScannedTransaction } from '@/features/finances/lib/message-scan';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

/**
 * The SMS segment: paste a message, let the AI read it, review what it found.
 *
 * The reader decides in or out per movement, and the review is where the user overrules it:
 * each row has an In / Out toggle, an expense row can be put under the running budget, and an
 * income row picks where the money came from. Nothing is written until the review is confirmed,
 * and confirming refreshes every screen that shows money.
 */
export function SmsCaptureManager({
  session,
  onSignOut,
  onToast,
  header,
}: {
  session: Session;
  onSignOut: (message?: string) => void;
  onToast: (kind: ToastKind, message: string) => void;
  header?: ReactNode;
}) {
  const theme = useTheme();

  const [text, setText] = useState('');
  const [reading, setReading] = useState(false);
  const [readError, setReadError] = useState<string | null>(null);

  const [scanned, setScanned] = useState<ScannedTransaction[]>([]);
  const [previewOpen, setPreviewOpen] = useState(false);

  // The review's pickers: the user's categories, and the sources an income can come from.
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [sources, setSources] = useState<IncomeSource[]>([]);
  const [loadingLists, setLoadingLists] = useState(true);

  const handleError = useCallback(
    (e: unknown, show: (message: string) => void = setReadError) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        onSignOut('Your session has expired. Please sign in again.');
        return;
      }
      show(describeError(e));
    },
    [onSignOut],
  );

  const loadLists = useCallback(async () => {
    setLoadingLists(true);
    try {
      const [myCategories, mySources] = await Promise.all([
        fetchMyCategories(session, { size: 200 }),
        // An account may have none yet; the review explains that rather than failing.
        fetchIncomeSources(session).catch(() => [] as IncomeSource[]),
      ]);
      setCategories(myCategories.data);
      setSources(mySources);
    } catch (e) {
      handleError(e);
    } finally {
      setLoadingLists(false);
    }
  }, [session, handleError]);

  useEffect(() => {
    loadLists();
  }, [loadLists]);

  const read = async () => {
    if (!text.trim()) {
      setReadError('Paste the message first.');
      return;
    }

    setReading(true);
    setReadError(null);
    try {
      const found = await scanMessage(session, text.trim());

      if (found.length === 0) {
        setReadError('No money movement was found in that text.');
        return;
      }

      setScanned(found);
      setPreviewOpen(true);
    } catch (e) {
      handleError(e);
    } finally {
      setReading(false);
    }
  };

  const onSaved = (count: number) => {
    setPreviewOpen(false);
    setScanned([]);
    setText('');

    // Every screen showing money re-reads itself: expenses, incomes, overview, budget, dashboard.
    notifyDataChanged();

    onToast(
      'created',
      count === 1
        ? 'Movement added from the message.'
        : `${count} movements added from the message.`,
    );
  };

  return (
    <>
      <ScrollView
        style={{ flex: 1, backgroundColor: theme.background }}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={[styles.contentContainer, local.content]}
        keyboardShouldPersistTaps="handled">
        <View style={styles.page}>
          {header}

          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold">Read a message</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Paste a mobile money SMS, a bank alert, or any text that describes a payment. The
              reader decides whether each movement is money in or money out, and you check every
              detail before anything is saved.
            </ThemedText>

            <Field label="Message" error={readError ?? undefined}>
              <TextField
                placeholder="Long-press here and choose Paste"
                multiline
                textAlignVertical="top"
                maxLength={8000}
                value={text}
                onChangeText={(value) => {
                  setText(value);
                  setReadError(null);
                }}
                style={local.messageInput}
                accessibilityLabel="The message to read"
              />
            </Field>

            <View style={styles.row}>
              <Button
                primary
                label={reading ? 'Reading…' : 'Read message'}
                onPress={read}
                disabled={reading || loadingLists}
              />
              {text ? (
                <Button
                  label="Clear"
                  onPress={() => setText('')}
                  disabled={reading}
                  secondary
                  onCard
                />
              ) : null}
            </View>
          </ThemedView>

          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText type="smallBold">How it is read</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Money leaving your account becomes an expense and can count against your running
              budget. Money arriving becomes an income. You can flip any row between the two in the
              review.
            </ThemedText>
          </ThemedView>
        </View>
      </ScrollView>

      <MessagePreviewPopup
        visible={previewOpen}
        scanned={scanned}
        categories={categories}
        sources={sources}
        session={session}
        onClose={() => setPreviewOpen(false)}
        onSaved={onSaved}
        onAuthError={() => onSignOut('Your session has expired. Please sign in again.')}
      />
    </>
  );
}

const local = StyleSheet.create({
  content: {
    paddingBottom: BottomTabInset + Spacing.four,
  },
  messageInput: {
    minHeight: 120,
  },
});
