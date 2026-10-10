import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Button, Chip } from '@/components/ui/primitives';
import { BarChart, CHART_COLORS, ChartLegend } from '@/features/admin/components/bar-chart';
import { ConsoleHeader, initialsFrom } from '@/features/admin/components/console-header';
import {
  AvatarCircle,
  EmptyState,
  FilterPill,
  Pill,
  SectionCard,
} from '@/features/admin/components/console-ui';
import { AccountActions } from '@/features/admin/components/account-actions';
import {
  fetchUsers,
  searchUsers,
  type AdminSession,
  type UserSummary,
} from '@/features/admin/lib/admin-api';
import { ADMIN_RANGES, type AdminRange, type AdminRangeKey } from '@/features/admin/lib/admin-range';
import {
  countIntoBuckets,
  formatRelative,
  rangeBuckets,
  rangeLabels,
  toChartBars,
} from '@/features/admin/lib/admin-stats';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { TextField } from '@/components/ui/text-field';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { initialsOf } from '@/features/admin/lib/admin-stats';
import { describeError } from '@/utils/errors';
import type { AccountState } from '@/types/api';

const PAGE_SIZE = 8;

/** The filters the mock-up offers: All, Active, Disabled. */
const FILTERS: { key: AccountState | 'ALL'; label: string; tone: 'success' | 'danger' | 'neutral' }[] = [
  { key: 'ALL', label: 'All', tone: 'neutral' },
  { key: 'ACTIVE', label: 'Active', tone: 'success' },
  { key: 'SUSPENDED', label: 'Disabled', tone: 'danger' },
];

/**
 * The console's Users screen - `ADMIN-03` in figmaInspiration.
 *
 * One list, two jobs: the intake chart and the directory read from the same `GET /admin/users`
 * payload, so the figures above the list always agree with the rows inside it. Account writes
 * (status and role) are delegated to `AccountActions`, which the Account detail sheet shares.
 */
export function AdminUsersScreen({
  session,
  range,
  onRangeChange,
  onAuthError,
}: {
  session: AdminSession;
  range: AdminRange;
  onRangeChange: (key: AdminRangeKey) => void;
  onAuthError: (message: string) => void;
}) {
  const theme = useTheme();

  const [filter, setFilter] = useState<AccountState | 'ALL'>('ALL');
  const [search, setSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [users, setUsers] = useState<UserSummary[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [openId, setOpenId] = useState<number | null>(null);

  /**
   * The intake chart needs the whole window, not just the current page: one page of eight would
   * draw a chart of the last eight signups rather than of the period. This is a second, larger
   * read used only for the figures above the list.
   */
  const [windowUsers, setWindowUsers] = useState<UserSummary[]>([]);

  const handleFailure = useCallback(
    (error: unknown) => {
      if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
        onAuthError(
          error.status === 401
            ? 'Your session has expired. Please sign in again.'
            : 'This account no longer has administrator access.',
        );
        return;
      }
      setMessage(
        error instanceof ApiRequestError && error.status === 409
          ? error.message
          : describeError(error, { unauthorized: 'Please sign in again.' }),
      );
    },
    [onAuthError],
  );

  const load = useCallback(
    async (mode: 'initial' | 'refresh' = 'initial') => {
      if (mode === 'refresh') setRefreshing(true);
      else setLoading(true);
      setMessage(null);

      try {
        const [list, window] = await Promise.all([
          appliedSearch
            ? searchUsers(session, appliedSearch, { page, size: PAGE_SIZE })
            : fetchUsers(session, {
                page,
                size: PAGE_SIZE,
                ...(filter === 'ALL' ? {} : { status: filter }),
              }),
          // Newest first, capped by the backend's page size.
          fetchUsers(session, { page: 0, size: 100, sort: 'createdAt,desc' }),
        ]);

        setUsers(list.content);
        setPage(list.page.number);
        setTotalPages(list.page.totalPages);
        setTotalElements(list.page.totalElements);
        setWindowUsers(window.content);
      } catch (error) {
        handleFailure(error);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [session, appliedSearch, filter, page, handleFailure],
  );

  useEffect(() => {
    void load('initial');
  }, [load]);

  const intake = useMemo(() => {
    const starts = rangeBuckets(range);
    const labels = rangeLabels(starts, range);
    const registrations = countIntoBuckets(windowUsers, (user) => user.createdAt ?? '', range);
    const active = countIntoBuckets(
      windowUsers.filter((user) => user.status === 'ACTIVE'),
      (user) => user.createdAt ?? '',
      range,
    );
    return {
      bars: toChartBars(labels, registrations, active),
      registrations: registrations.reduce((sum, value) => sum + value, 0),
      active: active.reduce((sum, value) => sum + value, 0),
    };
  }, [range, windowUsers]);

  const counts = useMemo(() => {
    const active = windowUsers.filter((user) => user.status === 'ACTIVE').length;
    const disabled = windowUsers.filter(
      (user) => user.status === 'SUSPENDED' || user.status === 'DELETED',
    ).length;
    const known = windowUsers.length || 1;
    return { active, disabled, activePct: Math.round((active / known) * 100), disabledPct: Math.round((disabled / known) * 100) };
  }, [windowUsers]);

  return (
    <ScrollView
      style={{ backgroundColor: theme.adminBackground }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load('refresh')} />}>
      <ConsoleHeader title="Users" initials={initialsFrom(session.userName)} />

      <View style={styles.page}>
        <View style={styles.titleRow}>
          <View style={styles.titleCopy}>
            <ThemedText type="title">User Accounts</ThemedText>
            <ThemedText type="caption" themeColor="textMuted">
              {appliedSearch
                ? `${totalElements} match “${appliedSearch}”`
                : `${totalElements} account${totalElements === 1 ? '' : 's'} registered`}
            </ThemedText>
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
        </View>

        <TextField
          placeholder="Search users by first or last name"
          autoCapitalize="none"
          autoCorrect={false}
          value={search}
          onChangeText={setSearch}
          onSubmitEditing={() => {
            setPage(0);
            setAppliedSearch(search.trim());
          }}
          returnKeyType="search"
        />

        <SectionCard title="Intake & Health" subtitle={`Last ${range.label}`}>
          <View style={styles.healthRow}>
            <Pill label={`Active ${counts.active} (${counts.activePct}%)`} tint="success" />
            <Pill label={`Disabled ${counts.disabled} (${counts.disabledPct}%)`} tint="danger" />
            <Pill label={`${intake.registrations} new`} tint="brand" />
          </View>
          <BarChart
            bars={intake.bars}
            series={[
              { label: 'Registered', color: CHART_COLORS.primary },
              { label: 'Active', color: CHART_COLORS.compare },
            ]}
            height={120}
            emptyLabel="No accounts were created in this period"
          />
          <ChartLegend
            entries={[
              { label: 'Registered', color: CHART_COLORS.primary },
              { label: 'Active', color: CHART_COLORS.compare },
            ]}
          />
        </SectionCard>

        <View style={styles.filterRow}>
          {FILTERS.map((option) => (
            <FilterPill
              key={option.key}
              label={option.label}
              count={
                option.key === 'ALL'
                  ? totalElements
                  : option.key === 'ACTIVE'
                    ? counts.active
                    : counts.disabled
              }
              selected={filter === option.key}
              tone={option.tone}
              onPress={() => {
                // Choosing a filter leaves the name search: the backend's search endpoint has no
                // status parameter, so the two cannot be combined.
                setFilter(option.key);
                setSearch('');
                setAppliedSearch('');
                setPage(0);
                setOpenId(null);
              }}
            />
          ))}
        </View>

        <View style={[styles.notice, { backgroundColor: theme.tintSuccess, borderColor: theme.border }]}>
          <ThemedText type="caption" style={{ color: Status.success }}>
            Data Shield Active
          </ThemedText>
          <ThemedText type="caption" themeColor="textSecondary">
            Admin endpoints expose account and role data only. A user's balances, expenses, incomes
            and ledger entries are not readable from the console.
          </ThemedText>
        </View>

        {message ? (
          <View style={[styles.banner, { borderColor: theme.border }]}>
            <ThemedText type="caption" themeColor="textSecondary">
              {message}
            </ThemedText>
          </View>
        ) : null}

        <View style={styles.directoryHeader}>
          <ThemedText type="subtitle">User Directory</ThemedText>
          <ThemedText type="caption" themeColor="textMuted">
            Sorted by most recent
          </ThemedText>
        </View>

        {loading && users.length === 0 ? (
          <ActivityIndicator style={styles.loader} />
        ) : users.length === 0 ? (
          <EmptyState title="No accounts match this view" hint="Try another filter or clear the search." />
        ) : (
          users.map((user) => (
            <View key={user.id} style={[styles.userCard, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
              <View style={styles.userTop}>
                <AvatarCircle initials={initialsOf(user.firstName, user.lastName)} />
                <View style={styles.userCopy}>
                  <View style={styles.userNameRow}>
                    <ThemedText type="smallBold" numberOfLines={1}>
                      {user.firstName} {user.lastName}
                    </ThemedText>
                    <ThemedText type="caption" style={{ color: Status.success }}>
                      {user.status === 'ACTIVE' ? '✓' : ''}
                    </ThemedText>
                    {user.status === 'SUSPENDED' || user.status === 'DELETED' ? (
                      <ThemedText type="caption" style={{ color: Status.danger }}>
                        ⃠
                      </ThemedText>
                    ) : null}
                  </View>
                  <ThemedText type="caption" themeColor="textMuted" numberOfLines={1}>
                    {user.userName}
                  </ThemedText>
                  <ThemedText type="caption" themeColor="textMuted">
                    {user.type === 'ADMIN' ? 'Administrator' : 'Registered user'}
                  </ThemedText>
                </View>
                <View style={styles.userTrailing}>
                  <Pill
                    label={user.status === 'ACTIVE' ? 'Active' : user.status === 'SUSPENDED' ? 'Disabled' : user.status}
                    tint={user.status === 'ACTIVE' ? 'success' : 'danger'}
                  />
                  <Button
                    label={openId === user.id ? 'Close' : 'Manage'}
                    onPress={() => setOpenId((current) => (current === user.id ? null : user.id))}
                    onCard
                  />
                </View>
              </View>

              {openId === user.id ? (
                <AccountActions
                  session={session}
                  userId={user.id}
                  onChanged={(updated) => {
                    setUsers((current) =>
                      current.map((row) =>
                        row.id === updated.id
                          ? { ...row, status: updated.status, type: updated.type }
                          : row,
                      ),
                    );
                    setMessage(`${updated.userName} is now ${updated.status} (${updated.type}).`);
                    void load('refresh');
                  }}
                  onFailure={handleFailure}
                />
              ) : null}
            </View>
          ))
        )}

        <View style={styles.footer}>
          <ThemedText type="caption" themeColor="textMuted">
            {totalElements === 0
              ? 'No accounts'
              : `Showing ${page * PAGE_SIZE + 1}–${page * PAGE_SIZE + users.length} of ${totalElements} accounts`}
          </ThemedText>
          {totalPages > 1 ? (
            <View style={styles.pager}>
              <Button label="Previous" onPress={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0 || loading} onCard />
              <ThemedText type="caption" themeColor="textSecondary">
                {page + 1} / {totalPages}
              </ThemedText>
              <Button
                label="Next"
                onPress={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page + 1 >= totalPages || loading}
                onCard
              />
            </View>
          ) : null}
        </View>

        <ThemedText type="caption" themeColor="textMuted">
          Exporting the audit trail is not available yet: the backend has no audit-log endpoint, so
          the Error log tab is the closest record.
        </ThemedText>
      </View>
    </ScrollView>
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
  chips: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.one + Spacing.half,
  },
  healthRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  filterRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  notice: {
    borderRadius: Radius.field,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.half,
  },
  directoryHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  userCard: {
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  userTop: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two + Spacing.one,
  },
  userCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  userNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one,
  },
  userTrailing: {
    alignItems: 'flex-end',
    gap: Spacing.one + Spacing.half,
  },
  footer: {
    gap: Spacing.two,
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
