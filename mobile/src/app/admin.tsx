import { useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { BottomTabInset, MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import {
  adminLogin,
  AdminSession,
  ApiRequestError,
  ErrorLogEntry,
  fetchErrorLogs,
  fetchPlatformHealth,
  HealthStatus,
  PlatformHealth,
} from '@/services/admin-monitoring';

const PAGE_SIZE = 20;

const STATUS_COLORS: Record<HealthStatus, string> = { UP: '#1F9D55', DOWN: '#D93025' };

type RangeKey = 'all' | '24h' | '7d';

const RANGES: { key: RangeKey; label: string; hours?: number }[] = [
  { key: 'all', label: 'All' },
  { key: '24h', label: 'Last 24h', hours: 24 },
  { key: '7d', label: 'Last 7 days', hours: 24 * 7 },
];

/**
 * US10: lets an administrator monitor platform health and recent errors.
 */
export default function AdminScreen() {
  const [session, setSession] = useState<AdminSession | null>(null);
  // Why the admin was sent back to sign in (e.g. account no longer allowed).
  const [signInMessage, setSignInMessage] = useState<string | null>(null);
  const signOut = useCallback((message?: string) => {
    setSession(null);
    setSignInMessage(message ?? null);
  }, []);

  const safeAreaInsets = useSafeAreaInsets();
  const theme = useTheme();
  const contentPlatformStyle = Platform.select({
    android: {
      paddingTop: safeAreaInsets.top,
      paddingBottom: safeAreaInsets.bottom + BottomTabInset + Spacing.three,
    },
    ios: { paddingBottom: Spacing.three },
    web: { paddingTop: Spacing.six + Spacing.four, paddingBottom: Spacing.four },
  });

  return (
    <ThemedView style={styles.screen}>
      {session ? (
        <MonitoringDashboard
          session={session}
          contentStyle={contentPlatformStyle}
          onSignOut={signOut}
        />
      ) : (
        <ScrollView
          style={{ backgroundColor: theme.background }}
          contentInsetAdjustmentBehavior="automatic"
          contentContainerStyle={[styles.contentContainer, contentPlatformStyle]}
          keyboardShouldPersistTaps="handled">
          <AdminSignIn initialError={signInMessage} onSignedIn={setSession} />
        </ScrollView>
      )}
    </ThemedView>
  );
}

function AdminSignIn({
  initialError,
  onSignedIn,
}: {
  initialError: string | null;
  onSignedIn: (session: AdminSession) => void;
}) {
  const theme = useTheme();
  const [usernameOrEmail, setUsernameOrEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(initialError);
  const [loading, setLoading] = useState(false);

  const signIn = async () => {
    if (!usernameOrEmail || !password) {
      setError('Enter your username (or email) and password.');
      return;
    }
    setLoading(true);
    setError(null);
    try {
      onSignedIn(await adminLogin(usernameOrEmail, password));
    } catch (e) {
      setError(
        e instanceof ApiRequestError && e.status === 401
          ? 'Wrong username or password.'
          : describeError(e),
      );
    } finally {
      setLoading(false);
    }
  };

  const inputStyle = [
    styles.input,
    { color: theme.text, backgroundColor: theme.backgroundElement, borderColor: theme.backgroundSelected },
  ];

  return (
    <View style={styles.signIn}>
      <ThemedText type="subtitle">Admin</ThemedText>
      <ThemedText themeColor="textSecondary">Sign in to monitor platform health and errors.</ThemedText>

      <TextInput
        style={inputStyle}
        placeholder="Username or email"
        placeholderTextColor={theme.textSecondary}
        autoCapitalize="none"
        autoCorrect={false}
        value={usernameOrEmail}
        onChangeText={setUsernameOrEmail}
      />
      <TextInput
        style={inputStyle}
        placeholder="Password"
        placeholderTextColor={theme.textSecondary}
        secureTextEntry
        value={password}
        onChangeText={setPassword}
        onSubmitEditing={signIn}
      />

      {error && <ThemedText style={styles.errorText}>{error}</ThemedText>}

      <Button label={loading ? 'Signing in…' : 'Sign in'} onPress={signIn} disabled={loading} />
    </View>
  );
}

function MonitoringDashboard({
  session,
  contentStyle,
  onSignOut,
}: {
  session: AdminSession;
  contentStyle: object | undefined;
  onSignOut: (message?: string) => void;
}) {
  const theme = useTheme();
  const [health, setHealth] = useState<PlatformHealth | null>(null);
  const [errors, setErrors] = useState<ErrorLogEntry[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalErrors, setTotalErrors] = useState(0);
  const [range, setRange] = useState<RangeKey>('all');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [errorsUnavailable, setErrorsUnavailable] = useState(false);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && (e.status === 401 || e.status === 403)) {
        // Tokens last 60 minutes and there is no refresh token: sign in again.
        onSignOut(
          e.status === 401 ? 'Your session has expired. Please sign in again.' : describeError(e),
        );
        return;
      }
      setMessage(describeError(e));
    },
    [onSignOut],
  );

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    const hours = RANGES.find((r) => r.key === range)?.hours;
    const from = hours ? new Date(Date.now() - hours * 3600 * 1000) : undefined;
    // Settled separately: when the database is down the error list fails, but health still
    // answers (reporting DOWN), and that is exactly what the admin needs to see.
    const [healthResult, errorsResult] = await Promise.allSettled([
      fetchPlatformHealth(session),
      fetchErrorLogs(session, { from, page: 0, size: PAGE_SIZE }),
    ]);
    if (healthResult.status === 'fulfilled') {
      setHealth(healthResult.value);
    }
    setErrorsUnavailable(errorsResult.status === 'rejected');
    if (errorsResult.status === 'fulfilled') {
      setErrors(errorsResult.value.content);
      setPage(0);
      setTotalPages(errorsResult.value.page.totalPages);
      setTotalErrors(errorsResult.value.page.totalElements);
    } else {
      setErrors([]);
      setTotalPages(0);
    }
    const failure =
      healthResult.status === 'rejected'
        ? healthResult.reason
        : errorsResult.status === 'rejected'
          ? errorsResult.reason
          : null;
    if (failure) {
      handleError(failure);
    }
    setLoading(false);
  }, [session, range, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  const loadMore = async () => {
    setLoadingMore(true);
    const hours = RANGES.find((r) => r.key === range)?.hours;
    const from = hours ? new Date(Date.now() - hours * 3600 * 1000) : undefined;
    try {
      const next = await fetchErrorLogs(session, { from, page: page + 1, size: PAGE_SIZE });
      setErrors((current) => [...current, ...next.content]);
      setPage(next.page.number);
      setTotalPages(next.page.totalPages);
    } catch (e) {
      handleError(e);
    } finally {
      setLoadingMore(false);
    }
  };

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.contentContainer, contentStyle]}
      refreshControl={<RefreshControl refreshing={loading && health !== null} onRefresh={load} />}>
      <View style={styles.dashboard}>
        <View style={styles.headerRow}>
          <View>
            <ThemedText type="subtitle">Platform health</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Signed in as {session.userName}
            </ThemedText>
          </View>
          <View style={styles.headerActions}>
            <Button label="Refresh" onPress={load} disabled={loading} secondary />
            <Button label="Sign out" onPress={() => onSignOut()} secondary />
          </View>
        </View>

        {message && <ThemedText style={styles.errorText}>{message}</ThemedText>}

        {loading && !health ? (
          <ActivityIndicator style={styles.loader} />
        ) : (
          health && <HealthSummary health={health} />
        )}

        <View style={styles.headerRow}>
          <ThemedText type="smallBold">
            Recent errors {health && !errorsUnavailable ? `(${totalErrors})` : ''}
          </ThemedText>
          <View style={styles.headerActions}>
            {RANGES.map((r) => (
              <Chip key={r.key} label={r.label} selected={range === r.key} onPress={() => setRange(r.key)} />
            ))}
          </View>
        </View>

        {!loading && errors.length === 0 && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText themeColor="textSecondary">
              {errorsUnavailable
                ? 'The error list is unavailable right now.'
                : 'No errors recorded in this period.'}
            </ThemedText>
          </ThemedView>
        )}

        {errors.map((entry) => (
          <ErrorLogItem key={entry.id} entry={entry} />
        ))}

        {page + 1 < totalPages && (
          <Button
            label={loadingMore ? 'Loading…' : 'Load more'}
            onPress={loadMore}
            disabled={loadingMore}
            secondary
          />
        )}
      </View>
    </ScrollView>
  );
}

function HealthSummary({ health }: { health: PlatformHealth }) {
  const { database, memory, disk } = health;
  return (
    <View style={styles.healthGrid}>
      <ThemedView type="backgroundElement" style={[styles.card, styles.overallCard]}>
        <StatusBadge status={health.status} large />
        <View>
          <ThemedText type="smallBold">
            {health.status === 'UP' ? 'All systems operational' : 'Problem detected'}
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            Checked {formatDateTime(health.timestamp)} · v{health.version}
          </ThemedText>
        </View>
      </ThemedView>

      <MetricCard
        title="Database"
        status={database.status}
        value={database.status === 'UP' ? `${database.responseTimeMs} ms` : 'Unreachable'}
        detail={database.error}
      />
      <MetricCard
        title="Memory"
        status={memory.status}
        value={`${memory.usagePercent}%`}
        detail={`${formatBytes(memory.usedBytes)} of ${formatBytes(memory.maxBytes)}`}
      />
      <MetricCard
        title="Disk"
        status={disk.status}
        value={formatBytes(disk.freeBytes)}
        detail={`free of ${formatBytes(disk.totalBytes)}`}
      />
      <MetricCard title="Uptime" value={formatDuration(health.uptimeSeconds)} />
      <MetricCard
        title="Errors (24h)"
        value={health.errorsLast24Hours !== null ? String(health.errorsLast24Hours) : '—'}
        detail={health.errorsLast24Hours !== null ? undefined : 'Unavailable while the database is down'}
      />
    </View>
  );
}

function MetricCard({
  title,
  value,
  detail,
  status,
}: {
  title: string;
  value: string;
  detail?: string;
  status?: HealthStatus;
}) {
  return (
    <ThemedView type="backgroundElement" style={[styles.card, styles.metricCard]}>
      <View style={styles.metricTitleRow}>
        <ThemedText type="small" themeColor="textSecondary">
          {title}
        </ThemedText>
        {status && <StatusBadge status={status} />}
      </View>
      <ThemedText type="smallBold" style={styles.metricValue}>
        {value}
      </ThemedText>
      {detail && (
        <ThemedText type="small" themeColor="textSecondary" numberOfLines={2}>
          {detail}
        </ThemedText>
      )}
    </ThemedView>
  );
}

function ErrorLogItem({ entry }: { entry: ErrorLogEntry }) {
  const [expanded, setExpanded] = useState(false);
  const shortType = entry.exceptionType.split('.').pop();

  return (
    <Pressable onPress={() => setExpanded((e) => !e)} style={({ pressed }) => pressed && styles.pressed}>
      <ThemedView type="backgroundElement" style={styles.card}>
        <View style={styles.metricTitleRow}>
          <ThemedText type="smallBold">
            {entry.status} · {shortType}
          </ThemedText>
          <ThemedText type="small" themeColor="textSecondary">
            {formatDateTime(entry.occurredAt)}
          </ThemedText>
        </View>
        {entry.path && (
          <ThemedText type="code">
            {entry.httpMethod} {entry.path}
          </ThemedText>
        )}
        {entry.message && <ThemedText type="small">{entry.message}</ThemedText>}
        {expanded && entry.stackTrace && (
          <ThemedText type="code" themeColor="textSecondary" style={styles.stackTrace}>
            {entry.stackTrace}
          </ThemedText>
        )}
        {entry.stackTrace && (
          <ThemedText type="linkPrimary">{expanded ? 'Hide details' : 'Show details'}</ThemedText>
        )}
      </ThemedView>
    </Pressable>
  );
}

function StatusBadge({ status, large }: { status: HealthStatus; large?: boolean }) {
  return (
    <View style={[styles.badge, large && styles.badgeLarge, { backgroundColor: STATUS_COLORS[status] }]}>
      <ThemedText type="smallBold" style={styles.badgeText}>
        {status}
      </ThemedText>
    </View>
  );
}

function Button({
  label,
  onPress,
  disabled,
  secondary,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  secondary?: boolean;
}) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [(pressed || disabled) && styles.pressed]}>
      <ThemedView
        type={secondary ? 'backgroundElement' : 'text'}
        style={[styles.button, secondary && styles.buttonSecondary]}>
        <ThemedText type="smallBold" themeColor={secondary ? 'text' : 'background'}>
          {label}
        </ThemedText>
      </ThemedView>
    </Pressable>
  );
}

function Chip({ label, selected, onPress }: { label: string; selected: boolean; onPress: () => void }) {
  return (
    <Pressable onPress={onPress} style={({ pressed }) => pressed && styles.pressed}>
      <ThemedView type={selected ? 'backgroundSelected' : 'backgroundElement'} style={styles.chip}>
        <ThemedText type="small" themeColor={selected ? 'text' : 'textSecondary'}>
          {label}
        </ThemedText>
      </ThemedView>
    </Pressable>
  );
}

function describeError(e: unknown): string {
  if (e instanceof ApiRequestError) {
    if (e.status === 401) return 'Please sign in again.';
    if (e.status === 403) return 'This account is not an administrator.';
    return e.message;
  }
  return 'Something went wrong.';
}

function formatBytes(bytes: number): string {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(value >= 10 || unit === 0 ? 0 : 1)} ${units[unit]}`;
}

function formatDuration(totalSeconds: number): string {
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m ${totalSeconds % 60}s`;
}

function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString();
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
  },
  contentContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    flexGrow: 1,
  },
  signIn: {
    flexGrow: 1,
    maxWidth: 420,
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.five,
  },
  dashboard: {
    flexGrow: 1,
    maxWidth: MaxContentWidth,
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.four,
  },
  input: {
    borderWidth: 1,
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.three,
    fontSize: 16,
  },
  errorText: {
    color: STATUS_COLORS.DOWN,
  },
  headerRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  headerActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  loader: {
    paddingVertical: Spacing.five,
  },
  healthGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.one,
  },
  overallCard: {
    width: '100%',
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.three,
  },
  metricCard: {
    flexGrow: 1,
    flexBasis: 150,
  },
  metricTitleRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  metricValue: {
    fontSize: 20,
    lineHeight: 28,
  },
  stackTrace: {
    marginTop: Spacing.two,
  },
  badge: {
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.half,
  },
  badgeLarge: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  badgeText: {
    color: '#ffffff',
  },
  button: {
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two + Spacing.one,
    alignItems: 'center',
  },
  buttonSecondary: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  chip: {
    borderRadius: Spacing.four,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
  },
  pressed: {
    opacity: 0.6,
  },
});
