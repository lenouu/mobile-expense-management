import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Icon } from '@/components/ui/icon';
import { ProgressBar } from '@/components/ui/progress-bar';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { formatLongDate, formatMoney } from '@/features/finances/components/finance-ui';
import { IncomeFormPopup } from '@/features/finances/components/income-form-popup';
import { WindowControl, type WindowValue } from '@/features/finances/components/window-control';
import {
  type IncomeEntry,
  type IncomeSource,
  type IncomeSummary,
  deleteIncome,
  fetchIncomeSources,
  fetchIncomes,
  fetchIncomeSummary,
} from '@/features/finances/lib/income-api';
import { fetchCurrentPeriod, type FinancialPeriod } from '@/features/finances/lib/finances-api';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const PAGE_SIZE = 20;
const DEFAULT_SOURCE_COLOR = '#1F9D55';

/**
 * The Income half of the ledger.
 *
 * <p>The same window, the same row language as expenses, and the same shape of answer at the top:
 * what came in, from where, and how much of it each source is worth. Income is never netted
 * against spending - the Dashboard will do that comparison - so this total is always gross.
 *
 * <p>Uses the shared {@link WindowControl}, so the two halves of the ledger describe the same days
 * when you flip between them.
 */
export function IncomeSegment({
  session,
  onSignOut,
  onToast,
  onSwitch,
}: {
  session: Session;
  onSignOut: (message?: string) => void;
  onToast?: (kind: 'created' | 'edited' | 'deleted', message: string) => void;
  /** Renders the Expenses | Income switch above everything else. */
  onSwitch: (ledger: 'expenses') => void;
}) {
  const theme = useTheme();

  const [period, setPeriod] = useState<FinancialPeriod | null>(null);
  const [window, setWindow] = useState<WindowValue>({ key: 'period' });
  const [summary, setSummary] = useState<IncomeSummary | null>(null);
  const [entries, setEntries] = useState<IncomeEntry[]>([]);
  const [sources, setSources] = useState<IncomeSource[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<IncomeEntry | null>(null);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        onSignOut('Your session has expired. Please sign in again.');
        return;
      }
      setError(describeError(e));
    },
    [onSignOut],
  );

  useEffect(() => {
    let cancelled = false;
    Promise.all([
      fetchCurrentPeriod(session).catch(() => null),
      fetchIncomeSources(session).catch(() => [] as IncomeSource[]),
    ]).then(([currentPeriod, sourceList]) => {
      if (cancelled) return;
      setPeriod(currentPeriod);
      setSources(sourceList);
    });
    return () => {
      cancelled = true;
    };
  }, [session]);

  /** The dates this screen is looking at. */
  const range = period
    ? window.key === 'period'
      ? { from: period.startDate, to: period.endDate }
      : { from: window.from ?? period.startDate, to: window.to ?? period.endDate }
    : null;

  const load = useCallback(
    async (nextPage: number, append: boolean) => {
      if (!range) return;

      setLoading(true);
      setError(null);
      try {
        const [pageResult, summaryResult] = await Promise.all([
          fetchIncomes(session, { ...range, page: nextPage, size: PAGE_SIZE }),
          fetchIncomeSummary(session, range),
        ]);

        setEntries((current) => (append ? [...current, ...pageResult.data] : pageResult.data));
        setTotalPages(pageResult.totalPages);
        setPage(pageResult.page);
        setSummary(summaryResult);
      } catch (e) {
        handleError(e);
      } finally {
        setLoading(false);
      }
    },
    // `range` is rebuilt each render; its two dates are what matter.
    [session, range?.from, range?.to, handleError],
  );

  useEffect(() => {
    if (range) load(0, false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [range?.from, range?.to]);

  const remove = async (entry: IncomeEntry) => {
    try {
      await deleteIncome(session, entry.id);
      onToast?.('deleted', 'The income entry has been deleted.');
      setFormOpen(false);
      setEditing(null);
      load(0, false);
    } catch (e) {
      handleError(e);
    }
  };

  return (
    <View style={[styles.screen, { backgroundColor: theme.background }]}>
      <ScrollView
        style={{ flex: 1 }}
        contentContainerStyle={styles.content}
        contentInsetAdjustmentBehavior="automatic">
        <View style={styles.page}>
          {/* The switch the user asked for: the same ledger, the other direction. */}
          <View style={[styles.switchRow, { borderColor: theme.border }]}>
            <LedgerTab
              label="Expenses"
              active={false}
              onPress={() => onSwitch('expenses')}
              theme={theme}
            />
            <LedgerTab label="Income" active onPress={() => undefined} theme={theme} />
          </View>

          <WindowControl
            period={period}
            value={window}
            onChange={(next) => setWindow({ key: next.key, from: next.from, to: next.to })}
          />

          {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
          {loading && !summary ? <ActivityIndicator style={styles.loader} /> : null}

          {summary ? (
            <>
              <ThemedView type="backgroundElement" style={styles.hero}>
                <ThemedText type="small" themeColor="textMuted">
                  Money in
                </ThemedText>
                <ThemedText type="display">{formatMoney(summary.total)}</ThemedText>

                <ThemedText type="small" themeColor="textSecondary">
                  {formatLongDate(summary.from)} – {formatLongDate(summary.to)} ·{' '}
                  {summary.count} {summary.count === 1 ? 'entry' : 'entries'}
                </ThemedText>
              </ThemedView>

              {summary.bySource.length > 0 ? (
                <ThemedView type="backgroundElement" style={styles.card}>
                  <ThemedText type="overline" themeColor="textMuted">
                    By source
                  </ThemedText>

                  {summary.bySource.map((slice) => (
                    <View key={slice.source.id} style={styles.slice}>
                      <View style={styles.sliceHead}>
                        <CategoryBadge
                          icon={slice.source.icon}
                          color={slice.source.color ?? DEFAULT_SOURCE_COLOR}
                          size={26}
                        />
                        <ThemedText type="small" style={styles.sliceName} numberOfLines={1}>
                          {slice.source.name}
                        </ThemedText>
                        <ThemedText type="caption" themeColor="textMuted">
                          {slice.share}%
                        </ThemedText>
                        <ThemedText type="smallBold">{formatMoney(slice.total)}</ThemedText>
                      </View>
                      <ProgressBar
                        value={slice.total.amount}
                        max={Math.max(1, summary.total.amount)}
                        height={6}
                      />
                    </View>
                  ))}
                </ThemedView>
              ) : null}

              <View style={styles.section}>
                <ThemedText type="overline" themeColor="textMuted">
                  Entries
                </ThemedText>

                {entries.length === 0 ? (
                  <ThemedView type="backgroundElement" style={styles.card}>
                    <ThemedText themeColor="textSecondary">
                      Nothing received in this window yet.
                    </ThemedText>
                  </ThemedView>
                ) : (
                  entries.map((entry) => (
                    <Pressable
                      key={entry.id}
                      onPress={() => {
                        setEditing(entry);
                        setFormOpen(true);
                      }}
                      accessibilityRole="button"
                      accessibilityLabel={`Edit ${entry.name ?? entry.source?.name ?? 'income'}`}
                      style={({ pressed }) => [pressed && styles.pressed]}>
                      <ThemedView type="backgroundElement" style={styles.entryRow}>
                        <CategoryBadge
                          icon={entry.source?.icon ?? null}
                          color={entry.source?.color ?? DEFAULT_SOURCE_COLOR}
                          size={30}
                        />

                        <View style={styles.entryCopy}>
                          <ThemedText type="small" numberOfLines={1}>
                            {entry.name ?? entry.source?.name ?? 'Income'}
                          </ThemedText>
                          <ThemedText type="caption" themeColor="textMuted" numberOfLines={1}>
                            {formatLongDate(entry.date)} · {entry.source?.name ?? 'No source'}
                          </ThemedText>
                        </View>

                        <ThemedText type="smallBold" style={{ color: Brand.green }}>
                          +{formatMoney(entry.amount)}
                        </ThemedText>
                      </ThemedView>
                    </Pressable>
                  ))
                )}

                {page + 1 < totalPages ? (
                  <Pressable
                    onPress={() => load(page + 1, true)}
                    accessibilityRole="button"
                    accessibilityLabel="Load more income entries"
                    style={({ pressed }) => [styles.loadMore, { borderColor: theme.border }, pressed && styles.pressed]}>
                    <ThemedText type="small" themeColor="textSecondary">
                      {loading ? 'Loading…' : 'Load more'}
                    </ThemedText>
                  </Pressable>
                ) : null}
              </View>
            </>
          ) : null}
        </View>
      </ScrollView>

      <Pressable
        onPress={() => {
          setEditing(null);
          setFormOpen(true);
        }}
        accessibilityRole="button"
        accessibilityLabel="Add income"
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}>
        <Icon name="add" size={28} color="#FFFFFF" />
      </Pressable>

      <IncomeFormPopup
        visible={formOpen}
        onClose={() => setFormOpen(false)}
        session={session}
        sources={sources}
        entry={editing}
        onSaved={(message, kind) => {
          setFormOpen(false);
          setEditing(null);
          onToast?.(kind, message);
          load(0, false);
        }}
        onDelete={editing ? () => remove(editing) : undefined}
        onAuthError={() => onSignOut('Your session has expired. Please sign in again.')}
      />
    </View>
  );
}

/** One side of the Expenses | Income switch. */
function LedgerTab({
  label,
  active,
  onPress,
  theme,
}: {
  label: string;
  active: boolean;
  onPress: () => void;
  theme: { background: string; backgroundSelected: string; text: string };
}) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="tab"
      accessibilityState={{ selected: active }}
      accessibilityLabel={label}
      style={[styles.ledgerTab, active && { backgroundColor: theme.backgroundSelected }]}>
      <ThemedText type={active ? 'smallBold' : 'small'} themeColor={active ? 'text' : 'textMuted'}>
        {label}
      </ThemedText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  content: { flexGrow: 1, paddingBottom: Spacing.six + 56 },
  page: {
    width: '100%',
    maxWidth: MaxContentWidth,
    alignSelf: 'center',
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  switchRow: {
    flexDirection: 'row',
    borderWidth: 1,
    borderRadius: Radius.pill,
    padding: 3,
    alignSelf: 'flex-start',
    gap: 3,
  },
  ledgerTab: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one + Spacing.half,
    borderRadius: Radius.pill,
  },
  hero: { borderRadius: Radius.card, padding: Spacing.four, gap: Spacing.two },
  card: { borderRadius: Radius.card, padding: Spacing.three, gap: Spacing.two },
  slice: { gap: Spacing.one },
  sliceHead: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  sliceName: { flex: 1, flexShrink: 1, minWidth: 0 },
  section: { gap: Spacing.two },
  entryRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  entryCopy: { flex: 1, gap: Spacing.half, minWidth: 0 },
  loadMore: {
    alignItems: 'center',
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingVertical: Spacing.two,
  },
  fab: {
    position: 'absolute',
    right: Spacing.four,
    bottom: Spacing.four,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Brand.green,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fabPressed: { opacity: 0.85, transform: [{ scale: 0.94 }] },
  loader: { marginTop: Spacing.five },
  error: { color: Status.danger },
  pressed: { opacity: 0.7 },
});
