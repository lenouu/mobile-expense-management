import { useMemo, useState } from 'react';
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';
import { useRouter } from 'expo-router';

import { ThemedText } from '@/components/themed-text';
import { Button, Chip } from '@/components/ui/primitives';
import { BarChart, CHART_COLORS, ChartLegend } from '@/features/admin/components/bar-chart';
import { ConsoleHeader, initialsFrom } from '@/features/admin/components/console-header';
import {
  IconTile,
  MetricTile,
  Pill,
  QuickAction,
  SectionCard,
  StatBlock,
} from '@/features/admin/components/console-ui';
import { useAdminConsole } from '@/features/admin/hooks/use-admin-console';
import type { AdminSession, UserSummary } from '@/features/admin/lib/admin-api';
import { ADMIN_RANGES, type AdminRange, type AdminRangeKey } from '@/features/admin/lib/admin-range';
import {
  countIntoBuckets,
  formatRelative,
  formatUptime,
  rangeBuckets,
  rangeLabels,
  toChartBars,
  toPercent,
} from '@/features/admin/lib/admin-stats';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

type ChartSeriesKey = 'registrations' | 'errors';

/**
 * The console's Home screen - `ADMIN-01` in figmaInspiration.
 *
 * Everything here is derived from the five endpoints the console reads: the figures from the
 * account counts, the chart from the accounts' `createdAt` plus the error log, and the health
 * block from `GET /admin/health`.
 */
export function AdminHomeScreen({ session, range, onRangeChange }: {
  session: AdminSession;
  range: AdminRange;
  onRangeChange: (key: AdminRangeKey) => void;
}) {
  const router = useRouter();
  const theme = useTheme();
  const data = useAdminConsole(session, range);
  const [series, setSeries] = useState<ChartSeriesKey>('registrations');

  const { health, accounts, users, categories, errors, errorTotal, errorsTruncated, loading, refreshing, error, refresh } = data;

  const chart = useMemo(() => {
    const starts = rangeBuckets(range);
    const labels = rangeLabels(starts, range);
    const registrations = countIntoBuckets(users, (user) => createdAtOf(user), range);
    const errorCounts = countIntoBuckets(errors, (entry) => entry.occurredAt, range);
    const bars =
      series === 'registrations'
        ? toChartBars(labels, registrations)
        : toChartBars(labels, errorCounts);
    return {
      bars,
      registrationsTotal: registrations.reduce((sum, value) => sum + value, 0),
      errorsTotal: errorCounts.reduce((sum, value) => sum + value, 0),
    };
  }, [range, users, errors, series]);

  const total = accounts?.total ?? 0;
  const active = accounts?.ACTIVE ?? 0;
  const suspended = accounts?.SUSPENDED ?? 0;
  const activeRatio = toPercent(active, total);
  const systemRate = total > 0 ? Math.round((suspended / total) * 1000) / 10 : 0;

  const healthOk = health?.status === 'UP';

  return (
    <ScrollView
      style={{ backgroundColor: theme.adminBackground }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={refresh} />}>
      <ConsoleHeader
        title="Home"
        initials={initialsFrom(session.userName)}
        onBellPress={() => router.push('/admin-logs')}
      />

      <View style={styles.page}>
        {/* Platform overview banner */}
        <View style={[styles.overview, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <View style={styles.overviewTop}>
            <View style={styles.overviewTitle}>
              <View style={[styles.liveDot, { backgroundColor: healthOk ? Status.success : Status.danger }]} />
              <ThemedText type="subtitle">Platform Overview</ThemedText>
            </View>
            <Pill label={healthOk ? 'LIVE NODE' : 'DEGRADED'} tint={healthOk ? 'success' : 'danger'} />
          </View>
          <ThemedText type="caption" themeColor="textMuted">
            API v{health?.version ?? '—'} · {healthOk ? 'All Services Healthy' : 'A component is down'}
            {health ? ` · checked ${formatRelative(health.timestamp)}` : ''}
          </ThemedText>
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
                lead
                label="Total Users"
                value={String(total)}
                icon="users"
                percent={activeRatio}
                percentLabel={`${activeRatio}%`}
              />
              <MetricTile
                label="Active Accounts"
                value={String(active)}
                icon="shield"
                detail={activeRatio >= 90 ? 'Healthy adoption' : 'Some accounts are not active'}
                percent={activeRatio}
                percentLabel={`${activeRatio}%`}
              />
            </View>

            <View style={styles.grid}>
              <MetricTile
                label="Suspended / Idle"
                value={String(suspended)}
                icon="sliders"
                detail={`${systemRate}% of all accounts`}
                percent={systemRate}
                percentLabel={`${systemRate}%`}
              />
              <MetricTile
                label="Default Categories"
                value={String(categories?.total ?? '—')}
                icon="categories"
                detail={
                  categories
                    ? `${categories.active} active of ${categories.total}`
                    : 'Managed in the Categories tab'
                }
                percent={categories ? toPercent(categories.active, categories.total) : undefined}
                percentLabel={
                  categories ? `${toPercent(categories.active, categories.total)}%` : undefined
                }
              />
            </View>

            <SectionCard
              title="Activity Dynamics"
              subtitle={`Account registrations per column · last ${range.label}`}
              action={
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
              }>
              <BarChart
                bars={chart.bars}
                series={[{ label: series, color: CHART_COLORS.primary }]}
                emptyLabel={`No registrations or errors in the last ${range.label}`}
              />
              <ChartLegend
                entries={[
                  {
                    label: 'New Registrations',
                    color: CHART_COLORS.primary,
                    onToggle: () => setSeries('registrations'),
                    dimmed: series !== 'registrations',
                  },
                  {
                    label: 'Recorded Errors',
                    color: CHART_COLORS.danger,
                    onToggle: () => setSeries('errors'),
                    dimmed: series !== 'errors',
                  },
                ]}
                totals={[chart.registrationsTotal, chart.errorsTotal]}
              />
              <ThemedText type="caption" themeColor="textMuted">
                Tap a legend entry to switch the chart between new accounts and recorded errors.
              </ThemedText>
            </SectionCard>

            <View style={styles.sectionHeading}>
              <ThemedText type="subtitle">Administrative Controls</ThemedText>
            </View>
            <View style={styles.quickGrid}>
              <QuickAction icon="users" label="Users" onPress={() => router.push('/admin-users')} />
              <QuickAction
                icon="categories"
                label="Categories"
                onPress={() => router.push('/admin-categories')}
              />
              <QuickAction icon="shield" label="Audit Logs" onPress={() => router.push('/admin-logs')} />
              {/* The mock-up's fourth control is Settings, which the backend has no endpoints for;
                  Analytics is the closest screen that actually exists. */}
              <QuickAction icon="analytics" label="Analytics" onPress={() => router.push('/admin-analytics')} />
            </View>

            <SectionCard
              title="Service Health"
              action={
                <View style={styles.uptime}>
                  <IconTile name="verified" size={22} iconSize={13} />
                  <ThemedText type="caption" themeColor="textSecondary">
                    {health ? `${formatUptime(health.uptimeSeconds)} uptime` : '—'}
                  </ThemedText>
                </View>
              }>
              <View style={styles.services}>
                <ServiceRow
                  icon="cloud"
                  title="Spring Boot Core API"
                  detail={`Latency: ${health?.database.responseTimeMs ?? '—'}ms · Heap: ${health?.memory.usagePercent ?? '—'}%`}
                  status={health?.status ?? 'DOWN'}
                  statusLabel={healthOk ? 'Operational' : 'Down'}
                />
                <ServiceRow
                  icon="database"
                  title="PostgreSQL Cluster"
                  detail={`Disk free: ${health ? `${Math.round(health.disk.freeBytes / 1024 / 1024 / 1024)} GB` : '—'} · Response ${health?.database.responseTimeMs ?? '—'}ms`}
                  status={health?.database.status ?? 'DOWN'}
                  statusLabel={health?.database.status === 'UP' ? 'Optimal' : 'Unreachable'}
                />
                <ServiceRow
                  icon="lock"
                  title="Authentication Gateway"
                  detail="JWT bearer · tokens expire after 60 minutes"
                  status="UP"
                  statusLabel="Protected"
                />
              </View>

              <View style={styles.statRow}>
                <StatBlock
                  label="Memory in use"
                  value={String(health?.memory.usagePercent ?? 0)}
                  unit="%"
                  percent={health?.memory.usagePercent ?? 0}
                />
                <StatBlock
                  label="Errors · 24h"
                  value={String(health?.errorsLast24Hours ?? 0)}
                  unit={health?.errorsLast24Hours === 1 ? 'incident' : 'incidents'}
                  percent={toPercent(health?.errorsLast24Hours ?? 0, Math.max(errorTotal, 1))}
                />
              </View>
            </SectionCard>

            {errorsTruncated ? (
              <ThemedText type="caption" themeColor="textMuted">
                Charting the newest {errors.length} of {errorTotal} incidents in this window.
              </ThemedText>
            ) : null}
          </>
        )}
      </View>
    </ScrollView>
  );
}

function ServiceRow({
  icon,
  title,
  detail,
  status,
  statusLabel,
}: {
  icon: 'cloud' | 'database' | 'lock';
  title: string;
  detail: string;
  status: 'UP' | 'DOWN';
  statusLabel: string;
}) {
  const theme = useTheme();
  return (
    <View style={[styles.serviceRow, { backgroundColor: theme.surfaceSubtle, borderColor: theme.border }]}>
      <IconTile name={icon} />
      <View style={styles.serviceCopy}>
        <ThemedText type="smallBold" numberOfLines={1}>
          {title}
        </ThemedText>
        <ThemedText type="caption" themeColor="textMuted" numberOfLines={2}>
          {detail}
        </ThemedText>
      </View>
      <Pill label={statusLabel} tint={status === 'UP' ? 'success' : 'danger'} />
    </View>
  );
}

/**
 * Reads `createdAt` off a user summary.
 *
 * `UserSummary.createdAt` is optional because the backend's DTO does not declare it, so this
 * returns an empty string when it is missing - `countIntoBuckets` skips unparseable dates, which
 * means a payload without the field draws an empty chart rather than a wrong one.
 */
function createdAtOf(user: UserSummary): string {
  return user.createdAt ?? '';
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
  overview: {
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  overviewTop: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  overviewTitle: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    flexShrink: 1,
  },
  liveDot: {
    width: 10,
    height: 10,
    borderRadius: Radius.pill,
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
  sectionHeading: {
    paddingTop: Spacing.one,
  },
  quickGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  uptime: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  services: {
    gap: Spacing.two,
  },
  serviceRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + Spacing.one,
    borderRadius: Radius.field,
    borderWidth: 1,
    padding: Spacing.two + Spacing.half,
  },
  serviceCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  statRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.three,
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
