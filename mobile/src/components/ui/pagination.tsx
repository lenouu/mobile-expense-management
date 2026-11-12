import { Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Brand, Radius, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

/** At most this many numbered buttons at once; the rest become ellipses. */
const MAX_BUTTONS = 5;

/** The page numbers to show around the current one, keeping it as close to the middle as possible. */
function pagesToShow(page: number, totalPages: number): number[] {
  if (totalPages <= MAX_BUTTONS) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }

  const half = Math.floor(MAX_BUTTONS / 2);
  const start = Math.min(Math.max(page - half, 0), totalPages - MAX_BUTTONS);
  return Array.from({ length: MAX_BUTTONS }, (_, index) => start + index);
}

/**
 * Page numbers with previous/next arrows, for a paged list.
 *
 * Numbers rather than a "load more" button: on a short list (one or two pages) that is the whole
 * control, and on a longer one it says where you are instead of only whether more exists. The
 * arrows are symbols from the icon set, not words, so the row reads at a glance.
 *
 * Pages are zero-based on the wire and one-based on screen - a list that says "page 0" is a list
 * written for the backend's convenience rather than the reader's.
 */
export function Pagination({
  page,
  totalPages,
  onChange,
  disabled = false,
}: {
  /** Zero-based, as the API counts them. */
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  /** True while a page is loading, so the row cannot be tapped twice. */
  disabled?: boolean;
}) {
  const theme = useTheme();

  // A single page still shows its number, so the row does not vanish and reappear as the list
  // grows: it simply has nothing to move to.
  const total = Math.max(totalPages, 1);
  const visible = pagesToShow(page, total);

  const atStart = page <= 0;
  const atEnd = page >= total - 1;

  return (
    <View style={styles.row}>
      <Pressable
        onPress={() => onChange(page - 1)}
        disabled={disabled || atStart}
        accessibilityRole="button"
        accessibilityLabel="Previous page"
        accessibilityState={{ disabled: disabled || atStart }}
        hitSlop={8}
        style={({ pressed }) => [
          styles.arrow,
          { borderColor: theme.border },
          (pressed || disabled || atStart) && styles.muted,
        ]}>
        <Icon name="chevronLeft" size={18} color={theme.textSecondary} />
      </Pressable>

      {visible[0] > 0 ? (
        <ThemedText type="small" themeColor="textMuted" style={styles.ellipsis}>
          …
        </ThemedText>
      ) : null}

      {visible.map((index) => {
        const active = index === page;

        return (
          <Pressable
            key={index}
            onPress={() => onChange(index)}
            disabled={disabled || active}
            accessibilityRole="button"
            accessibilityLabel={`Page ${index + 1}`}
            accessibilityState={{ selected: active, disabled: disabled }}
            style={({ pressed }) => [
              styles.page,
              {
                borderColor: active ? Brand.green : theme.border,
                backgroundColor: active ? Brand.green : 'transparent',
              },
              pressed && styles.muted,
            ]}>
            <ThemedText
              type="smallBold"
              style={{ color: active ? '#FFFFFF' : theme.textSecondary }}>
              {index + 1}
            </ThemedText>
          </Pressable>
        );
      })}

      {visible[visible.length - 1] < total - 1 ? (
        <ThemedText type="small" themeColor="textMuted" style={styles.ellipsis}>
          …
        </ThemedText>
      ) : null}

      <Pressable
        onPress={() => onChange(page + 1)}
        disabled={disabled || atEnd}
        accessibilityRole="button"
        accessibilityLabel="Next page"
        accessibilityState={{ disabled: disabled || atEnd }}
        hitSlop={8}
        style={({ pressed }) => [
          styles.arrow,
          { borderColor: theme.border },
          (pressed || disabled || atEnd) && styles.muted,
        ]}>
        <Icon name="chevronRight" size={18} color={theme.textSecondary} />
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    flexWrap: 'wrap',
    gap: Spacing.two,
    paddingVertical: Spacing.two,
  },
  arrow: {
    width: 36,
    height: 36,
    borderRadius: Radius.field,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  page: {
    minWidth: 36,
    height: 36,
    borderRadius: Radius.field,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: Spacing.two,
  },
  ellipsis: {
    width: 18,
    textAlign: 'center',
  },
  muted: {
    opacity: 0.45,
  },
});
