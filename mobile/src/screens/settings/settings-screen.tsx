import Constants from 'expo-constants';
import { router } from 'expo-router';
import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Icon } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { TopToast, type ToastMessage } from '@/components/ui/top-toast';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { updateMyProfile } from '@/features/auth/lib/account-api';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { fetchExpenses } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import {
  setThemePreference,
  themeLabel,
  type ThemePreference,
  useThemePreference,
} from '@/hooks/use-theme-preference';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

/**
 * The currencies the app offers. The backend accepts any ISO 4217 code, but a short list keeps
 * the choice honest: these are the three the product's examples use.
 */
const CURRENCIES = [
  { code: 'XAF', name: 'CFA franc', note: 'Central Africa' },
  { code: 'EUR', name: 'Euro', note: 'European Union' },
  { code: 'USD', name: 'US dollar', note: 'United States' },
];

const THEMES: ThemePreference[] = ['system', 'light', 'dark'];

/** The notifications the vision asks for. Only the 80% budget alert is on by default. */
const NOTIFICATIONS = [
  { key: 'budgetAlerts', title: 'Budget alerts', detail: 'When spending reaches 80% of a budget' },
  { key: 'periodEnding', title: 'Period ending', detail: 'The day before your period closes' },
  { key: 'weeklySummary', title: 'Weekly summary', detail: 'What you spent, every Sunday evening' },
] as const;

type NotificationKey = (typeof NOTIFICATIONS)[number]['key'];

/**
 * Settings: the preferences that change what the rest of the app means.
 *
 * The base currency is deliberately the most consequential one: it is the unit every amount is
 * recorded in, so it can only be changed while nothing has been recorded. There is no rate to
 * convert history with, and relabelling stored amounts would make every total wrong rather than
 * merely inconvenient.
 */
export function SettingsScreen({
  session,
  onBaseCurrencyChange,
  onSignOut,
  onAuthError,
}: {
  session: Session;
  /** Lets the session carry the new unit, so later screens record in it immediately. */
  onBaseCurrencyChange: (currency: string) => void;
  onSignOut: () => void;
  onAuthError: (message: string) => void;
}) {
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const [baseCurrency, setBaseCurrency] = useState(session.baseCurrency);
  const [recorded, setRecorded] = useState<number | null>(null);
  const [pickerOpen, setPickerOpen] = useState(false);
  const [themeOpen, setThemeOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [toast, setToast] = useState<ToastMessage | null>(null);

  const themePreference = useThemePreference();

  const [notifications, setNotifications] = useState<Record<NotificationKey, boolean>>({
    budgetAlerts: true,
    periodEnding: false,
    weeklySummary: false,
  });

  // One row is enough: the question is "has anything been recorded", not "how much".
  useEffect(() => {
    let cancelled = false;
    fetchExpenses(session, { page: 0, size: 1 })
      .then((page) => {
        if (!cancelled) setRecorded(page.totalElements);
      })
      .catch((e) => {
        if (e instanceof ApiRequestError && e.status === 401) {
          onAuthError(e.message);
        }
        // Otherwise leave it unknown: the backend enforces the rule anyway.
      });
    return () => {
      cancelled = true;
    };
  }, [session, onAuthError]);

  const locked = (recorded ?? 0) > 0;

  const choose = useCallback(
    async (code: string) => {
      if (code === baseCurrency) {
        setPickerOpen(false);
        return;
      }

      setSaving(true);
      setError(null);
      try {
        const updated = await updateMyProfile(session, { baseCurrency: code });
        setBaseCurrency(updated.baseCurrency);
        onBaseCurrencyChange(updated.baseCurrency);
        setPickerOpen(false);
        setToast({
          id: Date.now(),
          kind: 'edited',
          message: `Base currency is now ${updated.baseCurrency}.`,
        });
      } catch (e) {
        if (e instanceof ApiRequestError && e.status === 401) {
          onAuthError(e.message);
        } else {
          setError(describeError(e));
        }
      } finally {
        setSaving(false);
      }
    },
    [baseCurrency, session, onBaseCurrencyChange, onAuthError],
  );

  const confirmSignOut = () => {
    Alert.alert('Sign out?', 'You will need your password to sign back in.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Sign out', style: 'destructive', onPress: onSignOut },
    ]);
  };

  const version = Constants.expoConfig?.version ?? '1.0.0';

  return (
    <View style={[styles.screen, { backgroundColor: theme.background }]}>
      {/* The back arrow is the whole header: Settings is a destination you return from. */}
      <View style={[styles.header, { paddingTop: insets.top + Spacing.two }]}>
        <Pressable
          onPress={() => router.back()}
          accessibilityRole="button"
          accessibilityLabel="Back"
          hitSlop={10}
          style={({ pressed }) => [styles.backButton, pressed && styles.pressed]}>
          <Icon name="chevronLeft" size={22} color={theme.textSecondary} />
        </Pressable>

        <ThemedText type="title">Settings</ThemedText>
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.content}
        contentInsetAdjustmentBehavior="automatic">
        <View style={styles.page}>
          <ThemedText type="overline" themeColor="textMuted">
            Preferences
          </ThemedText>

          <ThemedView type="backgroundElement" style={styles.card}>
            <Pressable
              onPress={() => !locked && setPickerOpen(true)}
              disabled={locked || saving}
              accessibilityRole="button"
              accessibilityLabel={
                locked
                  ? `Base currency ${baseCurrency}, locked because expenses are recorded`
                  : `Base currency ${baseCurrency}. Change it.`
              }
              style={({ pressed }) => [styles.row, pressed && !locked && styles.pressed]}>
              <View style={styles.rowCopy}>
                <ThemedText type="subtitle">Base currency</ThemedText>
                <ThemedText type="small" themeColor="textMuted">
                  {locked
                    ? `Locked: ${recorded} ${recorded === 1 ? 'expense is' : 'expenses are'} already recorded in ${baseCurrency}.`
                    : 'Every amount you record uses this currency.'}
                </ThemedText>
              </View>

              <ThemedText type="subtitle" themeColor={locked ? 'textMuted' : 'text'}>
                {baseCurrency}
              </ThemedText>

              {locked ? (
                <Icon name="lock" size={18} color={theme.textMuted} />
              ) : (
                <Icon name="chevronRight" size={18} color={theme.textSecondary} />
              )}
            </Pressable>
          </ThemedView>

          <ThemedText type="small" themeColor="textMuted">
            One currency keeps every total meaningful: amounts in different units cannot be added
            without an exchange rate, and the app does not guess one.
          </ThemedText>

          {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}

          <ThemedText type="overline" themeColor="textMuted" style={styles.sectionSpacing}>
            Appearance
          </ThemedText>

          <ThemedView type="backgroundElement" style={styles.card}>
            <Pressable
              onPress={() => setThemeOpen(true)}
              accessibilityRole="button"
              accessibilityLabel={`Theme ${themeLabel(themePreference)}. Change it.`}
              style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
              <View style={styles.rowCopy}>
                <ThemedText type="subtitle">Theme</ThemedText>
                <ThemedText type="small" themeColor="textMuted">
                  {themeLabel(themePreference)}
                </ThemedText>
              </View>

              <Icon name="chevronRight" size={18} color={theme.textSecondary} />
            </Pressable>
          </ThemedView>

          <ThemedText type="overline" themeColor="textMuted" style={styles.sectionSpacing}>
            Notifications
          </ThemedText>

          <ThemedView type="backgroundElement" style={styles.card}>
            {NOTIFICATIONS.map((notification, index) => (
              <View
                key={notification.key}
                style={[
                  styles.row,
                  index > 0 && {
                    borderTopWidth: StyleSheet.hairlineWidth,
                    borderTopColor: theme.border,
                  },
                ]}>
                <View style={styles.rowCopy}>
                  <ThemedText type="subtitle">{notification.title}</ThemedText>
                  <ThemedText type="small" themeColor="textMuted">
                    {notification.detail}
                  </ThemedText>
                </View>

                <Switch
                  value={notifications[notification.key]}
                  onValueChange={(next) =>
                    setNotifications((current) => ({ ...current, [notification.key]: next }))
                  }
                  trackColor={{ false: theme.border, true: Brand.green }}
                  thumbColor="#FFFFFF"
                  accessibilityLabel={notification.title}
                />
              </View>
            ))}
          </ThemedView>

          <ThemedText type="small" themeColor="textMuted">
            Kept on this device for now. These will be saved to your account when notifications are
            wired up.
          </ThemedText>

          <ThemedText type="overline" themeColor="textMuted" style={styles.sectionSpacing}>
            Account
          </ThemedText>

          <ThemedView type="backgroundElement" style={styles.card}>
            <Pressable
              onPress={() => router.back()}
              accessibilityRole="button"
              accessibilityLabel="Profile, password and account"
              style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
              <View style={styles.rowCopy}>
                <ThemedText type="subtitle">Profile, password and account</ThemedText>
                <ThemedText type="small" themeColor="textMuted">
                  Your name, your picture, your password
                </ThemedText>
              </View>
              <Icon name="chevronRight" size={18} color={theme.textSecondary} />
            </Pressable>
          </ThemedView>

          <ThemedText type="overline" themeColor="textMuted" style={styles.sectionSpacing}>
            Session
          </ThemedText>

          <ThemedView type="backgroundElement" style={styles.card}>
            <Pressable
              onPress={confirmSignOut}
              accessibilityRole="button"
              accessibilityLabel="Sign out"
              style={({ pressed }) => [styles.row, pressed && styles.pressed]}>
              <ThemedText type="subtitle" style={styles.danger}>
                Sign out
              </ThemedText>
              <Icon name="chevronRight" size={18} color={theme.textSecondary} />
            </Pressable>
          </ThemedView>

          <ThemedText type="caption" themeColor="textMuted" style={styles.version}>
            FinFlow {version}
          </ThemedText>
        </View>
      </ScrollView>

      {/* The currency choices, with what each one is - a code alone is not an answer. */}
      <Popup visible={pickerOpen} onClose={() => setPickerOpen(false)}>
        <View style={styles.popupBody}>
          <ThemedText type="hero">Base currency</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Choose the unit you record amounts in. It can only change while nothing is recorded.
          </ThemedText>

          {CURRENCIES.map((currency) => {
            const active = currency.code === baseCurrency;

            return (
              <Pressable
                key={currency.code}
                onPress={() => choose(currency.code)}
                disabled={saving}
                accessibilityRole="button"
                accessibilityState={{ selected: active, disabled: saving }}
                style={({ pressed }) => [
                  styles.option,
                  {
                    borderColor: active ? Brand.green : theme.border,
                    backgroundColor: theme.background,
                  },
                  (pressed || saving) && styles.pressed,
                ]}>
                <View style={styles.rowCopy}>
                  <ThemedText type="subtitle">
                    {currency.code} · {currency.name}
                  </ThemedText>
                  <ThemedText type="small" themeColor="textMuted">
                    {currency.note}
                  </ThemedText>
                </View>

                {saving && active ? (
                  <ActivityIndicator />
                ) : active ? (
                  <Icon name="verified" size={20} color={Brand.green} />
                ) : null}
              </Pressable>
            );
          })}
        </View>
      </Popup>

      {/* Appearance: applied the moment it is chosen, across every screen. */}
      <Popup visible={themeOpen} onClose={() => setThemeOpen(false)}>
        <View style={styles.popupBody}>
          <ThemedText type="hero">Theme</ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Follow the device, or pick one and keep it for this session.
          </ThemedText>

          {THEMES.map((option) => {
            const active = option === themePreference;

            return (
              <Pressable
                key={option}
                onPress={() => {
                  setThemePreference(option);
                  setThemeOpen(false);
                }}
                accessibilityRole="button"
                accessibilityState={{ selected: active }}
                style={({ pressed }) => [
                  styles.option,
                  {
                    borderColor: active ? Brand.green : theme.border,
                    backgroundColor: theme.background,
                  },
                  pressed && styles.pressed,
                ]}>
                <ThemedText type="subtitle" style={styles.rowCopy}>
                  {themeLabel(option)}
                </ThemedText>

                {active ? <Icon name="verified" size={20} color={Brand.green} /> : null}
              </Pressable>
            );
          })}
        </View>
      </Popup>

      <TopToast toast={toast} onDismiss={() => setToast(null)} />
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingBottom: Spacing.two,
  },
  backButton: {
    width: 36,
    height: 36,
    alignItems: 'center',
    justifyContent: 'center',
    marginLeft: -Spacing.two,
  },
  scroll: {
    flex: 1,
  },
  content: {
    flexGrow: 1,
    paddingBottom: Spacing.six,
  },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  sectionSpacing: {
    marginTop: Spacing.three,
  },
  card: {
    borderRadius: Radius.card,
    paddingHorizontal: Spacing.three,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingVertical: Spacing.three,
    minHeight: 56,
  },
  rowCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  popupBody: {
    gap: Spacing.three,
  },
  danger: {
    color: Status.danger,
    flex: 1,
  },
  error: {
    color: Status.danger,
    fontSize: 14,
    lineHeight: 20,
  },
  version: {
    textAlign: 'center',
    marginTop: Spacing.four,
  },
  pressed: {
    opacity: 0.7,
  },
});
