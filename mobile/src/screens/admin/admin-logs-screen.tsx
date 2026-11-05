import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { TextField } from '@/components/ui/text-field';
import { Button, Chip } from '@/components/ui/primitives';
import { ConsoleHeader, initialsFrom } from '@/features/admin/components/console-header';
import { EmptyState, MetricTile, Pill, SectionCard } from '@/features/admin/components/console-ui';
import { fetchErrorLogWindow, type AdminSession, type ErrorLogEntry } from '@/features/admin/lib/admin-api';
import { ADMIN_RANGES, type AdminRange, type AdminRangeKey } from '@/features/admin/lib/admin-range';
import { formatDateTime, formatRelative, shortExceptionName } from '@/features/admin/lib/admin-stats';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { describeError } from '@/utils/errors';

/** How many entries to pull. The endpoint pages 100 at a time. */
const MAX_ENTRIES = 400;
const PAGE_SIZE = 10;

/**
 * The console's Audit Logs screen, reached from Home's Administrative Controls rather than a tab.
 *
 * It shows `GET /admin/logs/errors` - the recorded unexpected server errors. That is the only
 * audit trail the backend keeps: there is no per-action security log, so this screen does not
 * claim to show who changed what.
 */
export function AdminLogsScreen({
  session,
  range,
  onRangeChange,
  onAuthError,
  onBack,
}: {
  session: AdminSession;
  range: AdminRange;
  onRangeChange: (key: AdminRangeKey) => void;
  onAuthError: (message: string) => void;
  onBack: () => void;
}) {
  const theme = useTheme();

  const [entries, setEntries] = useState<ErrorLogEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [truncated, setTruncated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);

  const load = useCallback(
    async (mode: 'initial' | 'refresh') => {
      if (mode === 'refresh') setRefreshing(true);
      else setLoading(true);
      setMessage(null);
      try {
        const from = new Date(Date.now() - range.hours * 3600 * 1000);
        const result = await fetchErrorLogWindow(session, { from, maxEntries: MAX_ENTRIES });
        setEntries(result.entries);
        setTotal(result.total);
        setTruncated(result.truncated);
        setPage(0);
      } catch (error) {
        if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
          onAuthError(
            error.status === 401
              ? 'Your session has expired. Please sign in again.'
              : 'This account no longer has administrator access.',
          );
          return;
        }
        setMessage(describeError(error, { unauthorized: 'Please sign in again.' }));
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [session, range.hours, onAuthError],
  );

  useEffect(() => {
    void load('initial');
  }, [load]);

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase();
    if (!term) return entries;
    return entries.filter((entry) =>
      [entry.path, entry.exceptionType, entry.message, entry.httpMethod]
        .filter(Boolean)
        .some((field) => String(field).toLowerCase().includes(term)),
    );
  }, [entries, query]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, pageCount - 1);
  const visible = filtered.slice(safePage * PAGE_SIZE, safePage * PAGE_SIZE + PAGE_SIZE);
  const serverErrors = entries.filter((entry) => entry.status >= 500).length;

  return (
    <ScrollView
      style={{ backgroundColor: theme.adminBackground }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load('refresh')} />}>
      <ConsoleHeader title="Audit Logs" initials={initialsFrom(session.userName)} />

      <View style={styles.page}>
        <View style={styles.titleRow}>
          <View style={styles.titleCopy}>
            <ThemedText type="title">Audit Logs</ThemedText>
            <ThemedText type="caption" themeColor="textMuted">
              Recorded unexpected server errors. The backend keeps no per-action security log.
            </ThemedText>
          </View>
          <Button label="Back to Home" onPress={onBack} onCard />
        </View>

        <View style={styles.grid}>
          <MetricTile
            label={`Incidents · ${range.label}`}
            value={String(total)}
            icon="shield"
            detail={truncated ? `Newest ${entries.length} loaded` : 'All loaded'}
          />
          <MetricTile
            label="Server errors (5xx)"
            value={String(serverErrors)}
            icon="sliders"
            detail="Everything the log records is a server error"
          />
        </View>

        <View style={styles.chips}>
          {ADMIN_RANGES.map((option) => (
            <Chip
              key={option.key}
              label={option.label}
              selected={option.key === range.key}
              onPress={() => onRangeChange(option.key)}
            />
          ))}
        </View>

        <TextField
          placeholder="Filter by path, exception or message"
          autoCapitalize="none"
          autoCorrect={false}
          value={query}
          onChangeText={setQuery}
          hint={entries.length > 0 ? `Filtering ${entries.length} loaded entries on this device` : undefined}
        />

        {message ? (
          <View style={[styles.banner, { borderColor: Status.danger }]}>
            <ThemedText type="caption" style={{ color: Status.danger }}>
              {message}
            </ThemedText>
          </View>
        ) : null}

        {loading && entries.length === 0 ? (
          <ActivityIndicator style={styles.loader} />
        ) : filtered.length === 0 ? (
          <EmptyState
            title={
              entries.length === 0
                ? `No server errors in the last ${range.label}`
                : 'No entry matches that filter'
            }
            hint={entries.length === 0 ? 'That is the healthy case.' : 'Clear the filter to see everything.'}
          />
        ) : (
          visible.map((entry) => <LogCard key={entry.id} entry={entry} />)
        )}

        {pageCount > 1 ? (
          <View style={styles.pager}>
            <Button
              label="Previous"
              onPress={() => setPage((value) => Math.max(0, value - 1))}
              disabled={safePage === 0}
              onCard
            />
            <ThemedText type="caption" themeColor="textSecondary">
              Page {safePage + 1} of {pageCount}
            </ThemedText>
            <Button
              label="Next"
              onPress={() => setPage((value) => Math.min(pageCount - 1, value + 1))}
              disabled={safePage + 1 >= pageCount}
              onCard
            />
          </View>
        ) : null}
      </View>
    </ScrollView>
  );
}

/** One incident, collapsed to a summary and expandable to the stack trace. */
function LogCard({ entry }: { entry: ErrorLogEntry }) {
  const [open, setOpen] = useState(false);

  return (
    <SectionCard
      title={shortExceptionName(entry.exceptionType)}
      subtitle={`${entry.httpMethod ?? ''} ${entry.path ?? ''}`.trim() || 'No route recorded'}
      action={<Pill label={String(entry.status)} tint="danger" />}>
      {entry.message ? <ThemedText type="small">{entry.message}</ThemedText> : null}

      <ThemedText type="caption" themeColor="textMuted">
        {formatRelative(entry.occurredAt)} · {formatDateTime(entry.occurredAt)} · id {entry.id}
      </ThemedText>

      {entry.stackTrace ? (
        <>
          {open ? (
            <ThemedText type="code" themeColor="textSecondary">
              {entry.stackTrace}
            </ThemedText>
          ) : null}
          <Button
            label={open ? 'Hide stack trace' : 'Show stack trace'}
            onPress={() => setOpen((value) => !value)}
            onCard
          />
        </>
      ) : null}
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingBottom: Spacing.six,
  },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  titleRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  titleCopy: {
    gap: Spacing.half,
    flexShrink: 1,
  },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.one + Spacing.half,
  },
  pager: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  banner: {
    borderRadius: Radius.field,
    borderWidth: 1,
    padding: Spacing.two + Spacing.half,
  },
  loader: {
    paddingVertical: Spacing.five,
  },
});
