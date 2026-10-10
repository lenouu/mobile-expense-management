import { Modal, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Brand, Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type WelcomeTone = 'admin' | 'user';

/**
 * The small confirmation shown after a successful sign-in, before the app moves to the screen
 * that account type belongs on.
 *
 * It exists to make the role-based redirect visible while the rest of the app is being built:
 * seeing "Welcome admin" versus "Welcome <username>" is the difference between trusting the
 * redirect and guessing at it. It dismisses itself after `autoDismissMs`, so it never becomes a
 * step the user has to get through; tapping it skips the wait.
 */
export function WelcomeModal({
  visible,
  title,
  message,
  tone,
  destination,
  autoDismissMs = 1800,
  onDismiss,
}: {
  visible: boolean;
  title: string;
  message?: string;
  tone: WelcomeTone;
  /** Where the app is about to go, named so the test subject can see the decision. */
  destination: string;
  autoDismissMs?: number;
  onDismiss: () => void;
}) {
  const theme = useTheme();
  const accent = tone === 'admin' ? Brand.navy : Brand.green;

  return (
    <Modal
      visible={visible}
      transparent
      animationType="fade"
      // Not `onRequestClose`-driven: the screen behind is already navigating, and a back press
      // there would leave the app on a login form with a valid session.
      statusBarTranslucent
      onShow={() => {
        if (autoDismissMs <= 0) return;
        setTimeout(onDismiss, autoDismissMs);
      }}>
      <Pressable style={styles.backdrop} onPress={onDismiss} accessibilityRole="button">
        <View
          style={[
            styles.card,
            Shadows.card,
            { backgroundColor: theme.backgroundElement, borderColor: theme.border },
          ]}>
          <View style={[styles.badge, { backgroundColor: accent }]}>
            <ThemedText type="overline" style={styles.badgeText}>
              {tone === 'admin' ? 'ADMIN' : 'USER'}
            </ThemedText>
          </View>

          <ThemedText type="title" style={styles.centered}>
            {title}
          </ThemedText>

          {message ? (
            <ThemedText type="caption" themeColor="textSecondary" style={styles.centered}>
              {message}
            </ThemedText>
          ) : null}

          <View style={[styles.destination, { borderTopColor: theme.border }]}>
            <ThemedText type="caption" themeColor="textMuted">
              Taking you to
            </ThemedText>
            <ThemedText type="smallBold" style={{ color: accent }}>
              {destination}
            </ThemedText>
          </View>
        </View>
      </Pressable>
    </Modal>
  );
}

const styles = StyleSheet.create({
  backdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
    backgroundColor: 'rgba(2, 40, 76, 0.45)',
  },
  card: {
    width: '100%',
    maxWidth: 320,
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.four,
    alignItems: 'center',
    gap: Spacing.two,
  },
  badge: {
    borderRadius: Radius.pill,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.half + 1,
    marginBottom: Spacing.one,
  },
  badgeText: {
    color: '#FFFFFF',
  },
  centered: {
    textAlign: 'center',
  },
  destination: {
    alignItems: 'center',
    gap: Spacing.half,
    marginTop: Spacing.two,
    paddingTop: Spacing.two,
    borderTopWidth: StyleSheet.hairlineWidth,
    alignSelf: 'stretch',
  },
});
