import { Pressable, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Icon, type IconName } from '@/components/ui/icon';
import { Brand, Radius, Shadows, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

// The admin console's presentation pieces, matching the admin mock-ups:
// `figmaInspiration/ADMIN-0*.png`. Kept together because they are only meaningful as a set -
// every screen in the console is a stack of these.

export type Tint = 'success' | 'danger' | 'neutral' | 'brand';

/** A small rounded label: "Live", "Operational", "Default", "Admin". */
export function Pill({ label, tint = 'neutral' }: { label: string; tint?: Tint }) {
  const theme = useTheme();
  const background = {
    success: theme.tintSuccess,
    danger: theme.tintDanger,
    neutral: theme.tintNeutral,
    brand: theme.tintSuccess,
  }[tint];
  const color = {
    success: Status.success,
    danger: Status.danger,
    neutral: theme.textSecondary,
    brand: Brand.green,
  }[tint];

  return (
    <View style={[styles.pill, { backgroundColor: background }]}>
      <ThemedText type="caption" style={[styles.pillText, { color }]}>
        {label}
      </ThemedText>
    </View>
  );
}

/** A pale disc holding an icon - the shape repeated all over the mock-ups. */
export function IconTile({
  name,
  size = 40,
  iconSize = 20,
  tint,
  color,
}: {
  name: IconName;
  size?: number;
  iconSize?: number;
  /** Renders a filled brand-navy tile with a white glyph, as on the lead metric card. */
  tint?: string;
  color?: string;
}) {
  const theme = useTheme();
  const background = tint ?? theme.iconTile;
  const glyph = color ?? (tint ? '#FFFFFF' : Brand.green);

  return (
    <View style={[styles.iconTile, { width: size, height: size, backgroundColor: background }]}>
      <Icon name={name} size={iconSize} color={glyph} />
    </View>
  );
}

/**
 * One figure card.
 *
 * `lead` renders the navy card the mock-ups lead with. `delta` is the small green "+14%" corner
 * note; it is only shown when the caller has a real comparison to make.
 */
export function MetricTile({
  label,
  value,
  icon,
  detail,
  delta,
  percent,
  percentLabel,
  lead,
  style,
}: {
  label: string;
  value: string;
  icon?: IconName;
  detail?: string;
  delta?: string;
  /** 0-100, drawn as the progress bar under the figure. */
  percent?: number;
  percentLabel?: string;
  lead?: boolean;
  style?: StyleProp<ViewStyle>;
}) {
  const theme = useTheme();
  const onLead = lead === true;

  return (
    <ThemedView
      style={[
        styles.metric,
        Shadows.card,
        onLead
          ? { backgroundColor: Brand.navy, borderColor: Brand.navy }
          : { borderColor: theme.border },
        style,
      ]}>
      <View style={styles.metricTop}>
        <ThemedText type="small" style={onLead ? styles.onLeadMuted : undefined} themeColor={onLead ? undefined : 'textSecondary'}>
          {label}
        </ThemedText>
        {icon ? (
          <IconTile
            name={icon}
            size={34}
            iconSize={17}
            tint={onLead ? 'rgba(255,255,255,0.14)' : undefined}
            color={onLead ? '#FFFFFF' : undefined}
          />
        ) : null}
      </View>

      <View style={styles.metricValueRow}>
        <ThemedText type="title" style={[styles.metricValue, onLead && styles.onLead]}>
          {value}
        </ThemedText>
        {delta ? (
          <ThemedText type="caption" style={styles.delta}>
            {delta}
          </ThemedText>
        ) : null}
      </View>

      {detail ? (
        <ThemedText type="caption" style={onLead ? styles.onLeadMuted : undefined} themeColor={onLead ? undefined : 'textMuted'}>
          {detail}
        </ThemedText>
      ) : null}

      {percent !== undefined ? (
        <View style={styles.progress}>
          <View style={styles.progressLabels}>
            <ThemedText type="caption" style={onLead ? styles.onLeadMuted : undefined} themeColor={onLead ? undefined : 'textMuted'}>
              {percentLabel ? 'Ratio' : '0%'}
            </ThemedText>
            <ThemedText type="caption" style={onLead ? styles.onLead : styles.progressStrong}>
              {percentLabel ?? `${percent}%`}
            </ThemedText>
          </View>
          <View style={[styles.track, { backgroundColor: onLead ? 'rgba(255,255,255,0.22)' : theme.backgroundSelected }]}>
            {percent > 0 ? (
              <View
                style={[
                  styles.fill,
                  { width: `${Math.min(100, percent)}%`, backgroundColor: onLead ? '#FFFFFF' : Brand.green },
                ]}
              />
            ) : null}
          </View>
        </View>
      ) : null}
    </ThemedView>
  );
}

/** A titled white card: the container every list and chart sits in. */
export function SectionCard({
  title,
  subtitle,
  action,
  children,
  style,
  flush,
}: {
  title?: string;
  subtitle?: string;
  /** Right-hand control, e.g. a range selector or a "Manage" button. */
  action?: React.ReactNode;
  children?: React.ReactNode;
  style?: StyleProp<ViewStyle>;
  /** Removes inner padding, for a card whose content draws to its edges. */
  flush?: boolean;
}) {
  const theme = useTheme();

  return (
    <ThemedView
      style={[
        styles.card,
        Shadows.card,
        { borderColor: theme.border, padding: flush ? 0 : Spacing.three },
        style,
      ]}>
      {title ? (
        <View style={styles.cardHeader}>
          <View style={styles.cardTitleGroup}>
            <ThemedText type="subtitle">{title}</ThemedText>
            {subtitle ? (
              <ThemedText type="caption" themeColor="textMuted">
                {subtitle}
              </ThemedText>
            ) : null}
          </View>
          {action}
        </View>
      ) : null}
      {children}
    </ThemedView>
  );
}

/** A row of a list: icon tile, title, supporting line, trailing content. */
export function ListRow({
  icon,
  title,
  subtitle,
  trailing,
  onPress,
  highlight,
}: {
  icon?: IconName;
  title: string;
  subtitle?: string;
  trailing?: React.ReactNode;
  onPress?: () => void;
  /** Tints the icon tile, matching the per-category colours in the mock-ups. */
  highlight?: string;
}) {
  const theme = useTheme();

  const body = (
    <View style={[styles.listRow, { borderColor: theme.border }]}>
      {icon ? <IconTile name={icon} tint={highlight} color={highlight ? '#FFFFFF' : undefined} /> : null}
      <View style={styles.listCopy}>
        <ThemedText type="smallBold" numberOfLines={1}>
          {title}
        </ThemedText>
        {subtitle ? (
          <ThemedText type="caption" themeColor="textMuted" numberOfLines={2}>
            {subtitle}
          </ThemedText>
        ) : null}
      </View>
      {trailing}
    </View>
  );

  if (!onPress) return body;

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={({ pressed }) => pressed && styles.pressed}>
      {body}
    </Pressable>
  );
}

/** A quick action in the mock-ups' "Administrative Controls" grid. */
export function QuickAction({
  icon,
  label,
  onPress,
  badge,
}: {
  icon: IconName;
  label: string;
  onPress: () => void;
  /** Optional count shown under the label, as the mock-ups do on the Users tile. */
  badge?: string;
}) {
  const theme = useTheme();

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={({ pressed }) => [styles.quickAction, pressed && styles.pressed]}>
      <ThemedView style={[styles.quickActionInner, { borderColor: theme.border }]}>
        <IconTile name={icon} />
        <ThemedText type="caption" themeColor="textSecondary" numberOfLines={1}>
          {label}
        </ThemedText>
        {badge ? (
          <ThemedText type="caption" themeColor="textMuted" numberOfLines={1}>
            {badge}
          </ThemedText>
        ) : null}
      </ThemedView>
    </Pressable>
  );
}

/** A circle with the account's initials, as used throughout the mock-ups' directory. */
export function AvatarCircle({ initials, size = 40 }: { initials: string; size?: number }) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.avatar,
        { width: size, height: size, backgroundColor: theme.tintNeutral },
      ]}>
      <ThemedText type="smallBold" themeColor="textSecondary">
        {initials}
      </ThemedText>
    </View>
  );
}

/** A filter row of pills, each with a count, as in the mock-ups' Users and Categories screens. */
export function FilterPill({
  label,
  count,
  selected,
  tone = 'success',
  onPress,
}: {
  label: string;
  count?: number;
  selected: boolean;
  tone?: 'success' | 'danger' | 'neutral';
  onPress: () => void;
}) {
  const theme = useTheme();
  const dot = {
    success: Status.success,
    danger: Status.danger,
    neutral: theme.textMuted,
  }[tone];

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={({ pressed }) => pressed && styles.pressed}>
      <View
        style={[
          styles.filterPill,
          {
            backgroundColor: selected ? theme.backgroundElement : theme.tintNeutral,
            borderColor: selected ? Brand.green : 'transparent',
          },
        ]}>
        <View style={[styles.filterDot, { backgroundColor: dot }]} />
        <ThemedText type="caption" themeColor={selected ? 'text' : 'textSecondary'}>
          {label}
          {count !== undefined ? `  ${count}` : ''}
        </ThemedText>
      </View>
    </Pressable>
  );
}

/** A labelled statistic inside a card, as in the mock-ups' "Core Infrastructure Health". */
export function StatBlock({
  label,
  value,
  unit,
  percent,
}: {
  label: string;
  value: string;
  unit?: string;
  percent?: number;
}) {
  const theme = useTheme();

  return (
    <View style={styles.statBlock}>
      <ThemedText type="caption" themeColor="textSecondary">
        {label}
      </ThemedText>
      <View style={styles.statValueRow}>
        <ThemedText type="subtitle">{value}</ThemedText>
        {unit ? (
          <ThemedText type="caption" themeColor="textMuted">
            {unit}
          </ThemedText>
        ) : null}
      </View>
      {percent !== undefined ? (
        <View style={[styles.track, { backgroundColor: theme.backgroundSelected }]}>
          {percent > 0 ? (
            <View style={[styles.fill, { width: `${Math.min(100, percent)}%`, backgroundColor: Brand.navy }]} />
          ) : null}
        </View>
      ) : null}
    </View>
  );
}

/** The muted card shown when a list has nothing in it. */
export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  const theme = useTheme();
  return (
    <View style={[styles.empty, { backgroundColor: theme.surfaceSubtle, borderColor: theme.border }]}>
      <Icon name="shield" size={20} color={theme.textMuted} />
      <ThemedText type="small" themeColor="textSecondary" style={styles.emptyText}>
        {title}
      </ThemedText>
      {hint ? (
        <ThemedText type="caption" themeColor="textMuted" style={styles.emptyText}>
          {hint}
        </ThemedText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  pill: {
    borderRadius: Radius.pill,
    paddingHorizontal: Spacing.two + Spacing.half,
    paddingVertical: Spacing.half + 1,
    alignSelf: 'flex-start',
  },
  pillText: {
    fontWeight: '700',
  },
  iconTile: {
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  metric: {
    flexGrow: 1,
    flexBasis: 150,
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.one,
    minHeight: 132,
  },
  metricTop: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  metricValueRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  metricValue: {
    fontSize: 30,
    lineHeight: 36,
  },
  delta: {
    color: Status.success,
    fontWeight: '700',
  },
  onLead: {
    color: '#FFFFFF',
  },
  onLeadMuted: {
    color: 'rgba(255,255,255,0.72)',
  },
  progress: {
    marginTop: 'auto',
    gap: Spacing.one,
  },
  progressLabels: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  progressStrong: {
    color: Brand.green,
    fontWeight: '700',
  },
  track: {
    height: 6,
    borderRadius: Radius.pill,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    borderRadius: Radius.pill,
  },
  card: {
    borderRadius: Radius.card,
    borderWidth: 1,
    gap: Spacing.three,
    width: '100%',
  },
  cardHeader: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  cardTitleGroup: {
    gap: Spacing.half,
    flexShrink: 1,
  },
  listRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + Spacing.one,
    borderRadius: Radius.field,
    paddingVertical: Spacing.two,
  },
  listCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  quickAction: {
    flexGrow: 1,
    flexBasis: 72,
  },
  quickActionInner: {
    borderRadius: Radius.card,
    borderWidth: 1,
    paddingVertical: Spacing.two + Spacing.half,
    paddingHorizontal: Spacing.two,
    alignItems: 'center',
    gap: Spacing.one + Spacing.half,
    minHeight: 86,
    justifyContent: 'center',
  },
  statBlock: {
    flexGrow: 1,
    flexBasis: 120,
    gap: Spacing.one,
  },
  statValueRow: {
    flexDirection: 'row',
    alignItems: 'baseline',
    gap: Spacing.one,
  },
  empty: {
    borderRadius: Radius.field,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.one,
    alignItems: 'flex-start',
  },
  emptyText: {
    textAlign: 'left',
  },
  avatar: {
    borderRadius: Radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  filterPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.one + Spacing.half,
    borderRadius: Radius.pill,
    borderWidth: 1,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one + Spacing.half,
  },
  filterDot: {
    width: 7,
    height: 7,
    borderRadius: Radius.pill,
  },
  pressed: {
    opacity: 0.6,
  },
});
