import { useCallback, useEffect, useMemo, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { TextField } from '@/components/ui/text-field';
import { Button } from '@/components/ui/primitives';
import { ConsoleHeader, initialsFrom } from '@/features/admin/components/console-header';
import { EmptyState, FilterPill, IconTile, Pill, SectionCard } from '@/features/admin/components/console-ui';
import {
  createDefaultCategory,
  fetchDefaultCategories,
  setDefaultCategoryActive,
  updateDefaultCategory,
  type AdminSession,
  type DefaultCategory,
} from '@/features/admin/lib/admin-api';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { Brand, MaxContentWidth, Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { describeError } from '@/utils/errors';

/**
 * The console's Categories screen - `ADMIN-05` in figmaInspiration.
 *
 * The mock-up splits categories into "Expense" and "Income" with an envelope amount. The backend
 * has no type field and no envelope column - a default category is a name, description, icon,
 * colour and an active flag - so this screen filters on what actually exists (All / Active /
 * Inactive) and labels each row from its real fields. Inventing an expense/income split would mean
 * showing numbers that no endpoint can produce.
 */
export function AdminCategoriesScreen({
  session,
  onCategoriesLoaded,
  onAuthError,
}: {
  session: AdminSession;
  /** Lets the Analytics screen rank the same list without a second fetch. */
  onCategoriesLoaded?: (categories: DefaultCategory[]) => void;
  onAuthError: (message: string) => void;
}) {
  const theme = useTheme();

  const [categories, setCategories] = useState<DefaultCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<'all' | 'active' | 'inactive'>('all');
  const [busyId, setBusyId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

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
        // One unfiltered read: the list is short, and filtering locally means the counts in the
        // pills and the rows on screen can never disagree.
        const result = await fetchDefaultCategories(session, { page: 0, size: 100 });
        setCategories(result.content);
        onCategoriesLoaded?.(result.content);
      } catch (error) {
        handleFailure(error);
      } finally {
        setLoading(false);
        setRefreshing(false);
      }
    },
    [session, handleFailure, onCategoriesLoaded],
  );

  useEffect(() => {
    void load('initial');
  }, [load]);

  const counts = useMemo(() => {
    const active = categories.filter((category) => category.active).length;
    return { total: categories.length, active, inactive: categories.length - active };
  }, [categories]);

  const visible = useMemo(() => {
    const term = query.trim().toLowerCase();
    return categories
      .filter((category) =>
        filter === 'all' ? true : filter === 'active' ? category.active : !category.active,
      )
      .filter((category) =>
        term
          ? category.name.toLowerCase().includes(term) ||
            (category.description ?? '').toLowerCase().includes(term)
          : true,
      );
  }, [categories, filter, query]);

  const toggle = async (category: DefaultCategory) => {
    setBusyId(category.id);
    setMessage(null);
    try {
      const updated = await setDefaultCategoryActive(session, category.id, !category.active);
      setCategories((current) =>
        current.map((row) => (row.id === updated.id ? updated : row)),
      );
      setMessage(`${updated.name} is now ${updated.active ? 'seeded to new accounts' : 'inactive'}.`);
    } catch (error) {
      handleFailure(error);
    } finally {
      setBusyId(null);
    }
  };

  return (
    <ScrollView
      style={{ backgroundColor: theme.adminBackground }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={styles.content}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void load('refresh')} />}>
      <ConsoleHeader title="Categories" initials={initialsFrom(session.userName)} />

      <View style={styles.page}>
        <View style={styles.titleRow}>
          <View style={styles.titleCopy}>
            <ThemedText type="title">Default Categories</ThemedText>
            <ThemedText type="caption" themeColor="textMuted">
              System-wide defaults seeded to new accounts. Custom user categories are isolated.
            </ThemedText>
          </View>
          <Pill
            label={counts.active === counts.total ? 'Live Seeding' : `${counts.active} seeding`}
            tint={counts.active > 0 ? 'success' : 'danger'}
          />
        </View>

        <View style={[styles.summary, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
          <IconTile name="categories" size={34} iconSize={17} />
          <View style={styles.summaryCopy}>
            <ThemedText type="smallBold">{counts.total} System Defaults</ThemedText>
            <ThemedText type="caption" themeColor="textMuted">
              {counts.active} active · {counts.inactive} inactive
            </ThemedText>
          </View>
        </View>

        <Button
          label={creating ? 'Working…' : 'Create Default Category'}
          primary
          disabled={creating}
          onPress={() => setCreating(true)}
        />

        <TextField
          placeholder="Search default categories"
          autoCapitalize="none"
          autoCorrect={false}
          value={query}
          onChangeText={setQuery}
          hint={counts.total > 0 ? `Filtering ${counts.total} defaults` : undefined}
        />

        <View style={styles.filters}>
          <FilterPill
            label="All"
            count={counts.total}
            selected={filter === 'all'}
            tone="neutral"
            onPress={() => setFilter('all')}
          />
          <FilterPill
            label="Active"
            count={counts.active}
            selected={filter === 'active'}
            tone="success"
            onPress={() => setFilter('active')}
          />
          <FilterPill
            label="Inactive"
            count={counts.inactive}
            selected={filter === 'inactive'}
            tone="danger"
            onPress={() => setFilter('inactive')}
          />
        </View>

        {message ? (
          <View style={[styles.banner, { borderColor: theme.border }]}>
            <ThemedText type="caption" themeColor="textSecondary">
              {message}
            </ThemedText>
          </View>
        ) : null}

        {creating ? (
          <CategoryForm
            session={session}
            onCancel={() => setCreating(false)}
            onSaved={(saved) => {
              setCategories((current) => [...current, saved]);
              setCreating(false);
              setMessage(`${saved.name} created and active.`);
              void load('refresh');
            }}
            onAuthError={handleFailure}
          />
        ) : null}

        {loading && categories.length === 0 ? (
          <ActivityIndicator style={styles.loader} />
        ) : visible.length === 0 ? (
          <EmptyState
            title={categories.length === 0 ? 'No default categories yet' : 'Nothing matches that filter'}
            hint={
              categories.length === 0
                ? 'Create one and it is copied to every new account.'
                : 'Clear the search or pick another filter.'
            }
          />
        ) : (
          visible.map((category) => (
            <CategoryRow
              key={category.id}
              category={category}
              session={session}
              busy={busyId === category.id}
              onToggle={() => void toggle(category)}
              onSaved={(saved) => {
                setCategories((current) => current.map((row) => (row.id === saved.id ? saved : row)));
                setMessage(`${saved.name} updated.`);
              }}
              onFailure={handleFailure}
            />
          ))
        )}
      </View>
    </ScrollView>
  );
}

/**
 * One category, in the mock-up's card style: icon tile, name, a status pill, and the details the
 * backend actually stores.
 */
function CategoryRow({
  category,
  session,
  busy,
  onToggle,
  onSaved,
  onFailure,
}: {
  category: DefaultCategory;
  session: AdminSession;
  busy: boolean;
  onToggle: () => void;
  onSaved: (saved: DefaultCategory) => void;
  onFailure: (error: unknown) => void;
}) {
  const theme = useTheme();
  const [expanded, setExpanded] = useState(false);

  return (
    <View style={[styles.card, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <Pressable
        onPress={() => setExpanded((value) => !value)}
        accessibilityRole="button"
        accessibilityState={{ expanded }}
        style={({ pressed }) => pressed && styles.pressed}>
        <View style={styles.cardTop}>
          <IconTile
            name="document"
            tint={category.color ?? theme.iconTile}
            color={category.color ? '#FFFFFF' : undefined}
          />
          <View style={styles.cardCopy}>
            <View style={styles.cardNameRow}>
              <ThemedText type="smallBold" numberOfLines={1}>
                {category.name}
              </ThemedText>
              <Pill label={category.active ? 'Default' : 'Inactive'} tint={category.active ? 'success' : 'danger'} />
            </View>
            <ThemedText type="caption" themeColor="textMuted" numberOfLines={2}>
              {category.description ?? 'No description'}
            </ThemedText>
            <ThemedText type="caption" themeColor="textMuted">
              {category.active ? 'Seed Active' : 'Not seeded'} · id {category.id}
            </ThemedText>
          </View>
          <ThemedText type="small" themeColor="textMuted">
            ⋮
          </ThemedText>
        </View>
      </Pressable>

      {expanded ? (
        <View style={styles.cardBody}>
          <CategoryForm
            session={session}
            category={category}
            onCancel={() => setExpanded(false)}
            onSaved={(saved) => {
              onSaved(saved);
              setExpanded(false);
            }}
            onAuthError={onFailure}
            compact
          />
          <Button
            label={busy ? 'Working…' : category.active ? 'Deactivate' : 'Activate'}
            disabled={busy}
            onCard
            onPress={onToggle}
          />
          <ThemedText type="caption" themeColor="textMuted">
            Categories are never deleted — deactivating stops new accounts receiving them while
            leaving every account already using it untouched.
          </ThemedText>
        </View>
      ) : null}
    </View>
  );
}

/**
 * Create and edit share one form.
 *
 * Editing re-reads the row with `GET /admin/categories/{id}` before it can be saved, because `PUT`
 * is a full replace: saving from a stale list row would silently clear a description another
 * admin had just added.
 */
function CategoryForm({
  session,
  category,
  onSaved,
  onCancel,
  onAuthError,
  compact,
}: {
  session: AdminSession;
  category?: DefaultCategory;
  onSaved: (saved: DefaultCategory) => void;
  onCancel: () => void;
  onAuthError: (error: unknown) => void;
  compact?: boolean;
}) {
  const [name, setName] = useState(category?.name ?? '');
  const [description, setDescription] = useState(category?.description ?? '');
  const [color, setColor] = useState(category?.color ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  const HEX = /^#[0-9A-Fa-f]{6}$/;

  const save = async () => {
    const next: Record<string, string> = {};
    if (!name.trim()) next.name = 'Name is required';
    if (color.trim() && !HEX.test(color.trim())) {
      next.color = 'Use a hex value like #01916D';
    }
    setErrors(next);
    if (Object.keys(next).length > 0) return;

    setSaving(true);
    try {
      const body = {
        name: name.trim(),
        description: description.trim() || undefined,
        color: color.trim() || undefined,
      };
      const saved = category
        ? await updateDefaultCategory(session, category.id, body)
        : await createDefaultCategory(session, body);
      onSaved(saved);
    } catch (error) {
      if (error instanceof ApiRequestError && (error.status === 401 || error.status === 403)) {
        onAuthError(error);
      } else if (error instanceof ApiRequestError && error.status === 409) {
        setErrors({ name: error.message });
      } else if (error instanceof ApiRequestError) {
        setErrors({ ...error.fieldErrors, form: error.message });
      } else {
        setErrors({ form: describeError(error) });
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={[styles.form, compact ? undefined : styles.formStandalone]}>
      <ThemedText type="caption" themeColor="textSecondary">
        {category ? `Editing “${category.name}”` : 'New default category'}
      </ThemedText>

      <TextField
        label="Name"
        placeholder="e.g. Transport"
        maxLength={50}
        value={name}
        onChangeText={setName}
        error={errors.name}
      />
      <TextField
        label="Description (optional)"
        placeholder="What belongs in this category"
        maxLength={255}
        value={description}
        onChangeText={setDescription}
      />
      <TextField
        label="Colour (optional)"
        placeholder="#01916D"
        autoCapitalize="none"
        autoCorrect={false}
        maxLength={7}
        value={color}
        onChangeText={setColor}
        error={errors.color}
      />

      {errors.form ? (
        <ThemedText type="caption" style={{ color: Status.danger }}>
          {errors.form}
        </ThemedText>
      ) : null}

      <View style={styles.formActions}>
        <Button
          label={saving ? 'Saving…' : category ? 'Save changes' : 'Create'}
          primary
          disabled={saving}
          onPress={() => void save()}
        />
        <Button label="Cancel" onPress={onCancel} disabled={saving} onCard />
      </View>
    </View>
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
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: Spacing.two,
  },
  titleCopy: {
    gap: Spacing.half,
    flexShrink: 1,
  },
  summary: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two + Spacing.one,
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.three,
  },
  summaryCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  filters: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: Spacing.two,
  },
  card: {
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  cardTop: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: Spacing.two + Spacing.one,
  },
  cardCopy: {
    flex: 1,
    gap: Spacing.half,
  },
  cardNameRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    flexWrap: 'wrap',
  },
  cardBody: {
    gap: Spacing.two,
    paddingTop: Spacing.two,
    borderTopWidth: StyleSheet.hairlineWidth,
    borderTopColor: 'rgba(148, 163, 184, 0.4)',
  },
  form: {
    gap: Spacing.two + Spacing.half,
  },
  formStandalone: {
    borderRadius: Radius.card,
    borderWidth: 1,
    borderColor: Brand.green,
    padding: Spacing.three,
  },
  formActions: {
    flexDirection: 'row',
    flexWrap: 'wrap',
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
  pressed: {
    opacity: 0.6,
  },
});
