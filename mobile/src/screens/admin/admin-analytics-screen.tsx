import { useMemo } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Button, Chip } from '@/components/ui/primitives';
import { BarChart, CHART_COLORS, ChartLegend } from '@/features/admin/components/bar-chart';
import { ConsoleHeader, initialsFrom } from '@/features/admin/components/console-header';
import {
  EmptyState,
  ListRow,
  MetricTile,
  Pill,
  SectionCard,
  StatBlock,
} from '@/features/admin/components/console-ui';
import { useAdminConsole } from '@/features/admin/hooks/use-admin-console';
import type { AdminSession, DefaultCategory } from '@/features/admin/lib/admin-api';
import { ADMIN_RANGES, type AdminRange, type AdminRangeKey } from '@/features/admin/lib/admin-range';
import {
  countIntoBuckets,
  formatBytes,
  formatUptime,
  rangeBuckets,
  rangeLabels,
  toChartBars,
  toPercent,
} from '@/features/admin/lib/admin-stats';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/**
 * The console's Analytics screen - `ADMIN-02` in figmaInspiration.
 *
 * Every figure is computed from the admin API. Three of the mock-up's blocks cannot be filled
 * from the current endpoints and say so rather than inventing numbers:
 *
 * - "Total Logins" and "Daily Active Users" would need a login/session log; the backend records
 *   only unexpected server errors, so this screen reports account growth instead.
 * - "Expense Logging / Envelope Budgeting" adoption is a user-side metric and there is no
 *   aggregate endpoint for it, so the module list shows what the console can actually count.
 * - "Push Delivery" and "Total Queries" are not in `GET /admin/health`.
 */
export function AdminAnalyticsScreen({
  session,
  range,
  onRangeChange,
  categories,
}: {
  session: AdminSession;
  range: AdminRange;
  onRangeChange: (key: AdminRangeKey) => void;
  /** Loaded by the Categories screen so this one can rank them without a second fetch. */
  categories: DefaultCategory[];
}) {
  const theme = useTheme();
  const data = useAdminConsole(session, range);
  const { health, accounts, users, usersTruncated, error, loading, refreshing, refresh } = data;

  const totals = useMemo(() => {
    const starts = rangeBuckets(range);
    const labels = rangeLabels(starts, range);
    const perBucket = countIntoBuckets(users, (user) => user.createdAt ?? '', range);
    const admins = users.filter((user) => user.type === 'ADMIN').length;
    const registrations = perBucket.reduce((sum, value) => sum + value, 0);
    // "Verified" here means an account the backend reports as ACTIVE, which is the closest real
    // signal to the mock-up's verified series.
    const verifiedPerBucket = countIntoBuckets(
      users.filter((user) => user.status === 'ACTIVE'),
      (user) => user.createdAt ?? '',
      range,
    );

    return {
      bars: toChartBars(labels, perBucket, verifiedPerBucket),
      registrations,
      verified: verifiedPerBucket.reduce((sum, value) => sum + value, 0),
      admins,
      activeRatio: toPercent(accounts?.ACTIVE ?? 0, accounts?.total ?? 0),
    };
  }, [range, users, accounts]);

  const errorRate = toPercent(health?.errorsLast24Hours ?? 0, Math.max(accounts?.total ?? 1, 1));

  return (
    <ScrollView
      style={{ backgroundColor: theme.adminBackground }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <ConsoleHeader title="Analytics" initials={initialsFrom(session.userName)} />

      <View style={styles.page}>
        <View style={styles.titleRow}>
          <View style={styles.titleCopy}>
            <ThemedText type="title">Platform Analytics</ThemedText>
            <ThemedText type="caption" themeColor="textMuted">
              Aggregated account activity from the admin API
            </ThemedText>
          </View>
          <Button label="Refresh" onPress={refresh} onCard />
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

        {error ? (
          <View style={[styles.banner, { borderColor: Status.danger }]}>
            <ThemedText type="caption" style={{ color: Status.danger }}>
              {error}
            </ThemedText>
            <Button label="Retry" onPress={refresh} onCard />
          </View>
        ) : null}

        {loading && !health ? (
          <ActivityIndicator style={styles.loader} />
        ) : (
          <>
            <View style={styles.grid}>
              <MetricTile
                label="Accounts on record"
                value={String(accounts?.total ?? 0)}
                icon="trendUp"
                delta={`${totals.admins} admin${totals.admins === 1 ? '' : 's'}`}
                detail={`Backend tracks up to 100 rows per request`}
              />
              <MetricTile
                label="Active accounts"
                value={String(accounts?.ACTIVE ?? 0)}
                icon="analytics"
                delta={`${totals.activeRatio}%`}
                percent={totals.activeRatio}
                percentLabel={`${totals.activeRatio}%`}
              />
            </View>

            <SectionCard
              title="Account Registrations"
              subtitle={`New accounts per column · last ${range.label}`}
              action={
                <Pill label={`${range.label} window`} tint="brand" />
              }>
              <BarChart
                bars={totals.bars}
                series={[
                  { label: 'Registered', color: CHART_COLORS.primary },
                  { label: 'Active', color: CHART_COLORS.compare },
                ]}
                emptyLabel={`No accounts were created in the last ${range.label}`}
              />
              <ChartLegend
                entries={[
                  { label: 'Registered', color: CHART_COLORS.primary },
                  { label: 'Active', color: CHART_COLORS.compare },
                ]}
                totals={[totals.registrations, totals.verified]}
              />
              <ThemedText type="caption" themeColor="textMuted">
                {usersTruncated
                  ? `Computed from the ${users.length} most recent accounts; the backend caps a page at 100 rows.`
                  : `Computed from all ${users.length} accounts.`}
              </ThemedText>
            </SectionCard>

            <SectionCard
              title="Most Utilized Modules"
              subtitle="System-wide defaults ranked by what is live">
              {categories.length === 0 ? (
                <EmptyState
                  title="No default categories to rank"
                  hint="Create one from the Categories tab and it appears here."
                />
              ) : (
                categories
                  .slice()
                  .sort((a, b) => Number(b.active) - Number(a.active) || a.name.localeCompare(b.name))
                  .slice(0, 5)
                  .map((category) => {
                    const live = categories.filter((item) => item.active).length || 1;
                    const share = category.active ? toPercent(1, live) : 0;
                    return (
                      <ListRow
                        key={category.id}
                        // The API stores a free-text icon name; the tile shows the category colour
                        // when one is set, which is the only styling signal available.
                        icon="document"
                        highlight={category.color ?? undefined}
                        title={category.name}
                        subtitle={category.description ?? 'No description'}
                        trailing={
                          <View style={styles.moduleTrailing}>
                            <ThemedText type="smallBold">{category.active ? '100%' : '0%'}</ThemedText>
                            <ThemedText type="caption" style={{ color: Brand.green }}>
                              {category.active ? 'Live' : 'Inactive'}
                            </ThemedText>
                          </View>
                        }
                      />
                    );
                  })
              )}
              {categories.length > 5 ? (
                <ThemedText type="caption" themeColor="textMuted">
                  Showing 5 of {categories.length} defaults.
                </ThemedText>
              ) : null}
            </SectionCard>

            <SectionCard
              title="Core Infrastructure Health"
              action={
                <Pill
                  label={health ? `${formatUptime(health.uptimeSeconds)} uptime` : '—'}
                  tint={health?.status === 'UP' ? 'success' : 'danger'}
                />
              }>
              <View style={styles.grid}>
                <StatBlock
                  label="Database Latency"
                  value={String(health?.database.responseTimeMs ?? 0)}
                  unit="ms"
                  percent={Math.max(0, 100 - Math.min(100, health?.database.responseTimeMs ?? 0))}
                />
                <StatBlock
                  label="Memory in use"
                  value={String(health?.memory.usagePercent ?? 0)}
                  unit="%"
                  percent={health?.memory.usagePercent ?? 0}
                />
              </View>
              <View style={styles.grid}>
                <StatBlock
                  label="Heap used"
                  value={formatBytes(health?.memory.usedBytes ?? 0)}
                  unit={`of ${formatBytes(health?.memory.maxBytes ?? 0)}`}
                  percent={toPercent(health?.memory.usedBytes ?? 0, health?.memory.maxBytes ?? 1)}
                />
                <StatBlock
                  label="Disk free"
                  value={formatBytes(health?.disk.freeBytes ?? 0)}
                  unit={`of ${formatBytes(health?.disk.totalBytes ?? 0)}`}
                  percent={toPercent(
                    (health?.disk.totalBytes ?? 0) - (health?.disk.freeBytes ?? 0),
                    health?.disk.totalBytes ?? 1,
                  )}
                />
              </View>
              <View style={styles.grid}>
                <StatBlock
                  label="Errors · 24h"
                  value={String(health?.errorsLast24Hours ?? 0)}
                  unit={errorRate > 0 ? `${errorRate}% of accounts` : 'no incidents'}
                />
                <StatBlock label="Version" value={health?.version ?? '—'} unit={health?.applicationName} />
              </View>
            </SectionCard>

            <SectionCard title="Not measured yet" subtitle="Endpoints this screen would need">
              <ThemedText type="small" themeColor="textSecondary">
                Login volume, daily active users and per-module adoption are not exposed by the
                current backend. These cards stay empty rather than showing estimated numbers — add
                an activity-log or analytics endpoint and they can be filled in.
              </ThemedText>
            </SectionCard>
          </>
        )}
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
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  moduleTrailing: {
    alignItems: 'flex-end',
    gap: Spacing.half,
  },
  banner: {
    borderRadius: Radius.field,
    borderWidth: 1,
    padding: Spacing.two + Spacing.half,
    gap: Spacing.two,
    alignItems: 'flex-start',
  },
  loader: {
    paddingVertical: Spacing.six,
  },
});
