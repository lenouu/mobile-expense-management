import { ReactNode, useCallback, useEffect, useState } from 'react';
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  TextInput,
  View,
} from 'react-native';

import { Button, Chip, DANGER_COLOR, describeError, SUCCESS_COLOR } from '@/components/admin/admin-ui';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { MaxContentWidth, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import {
  createCategory,
  DefaultCategory,
  fetchCategories,
  SaveCategory,
  setCategoryActive,
  updateCategory,
} from '@/services/admin-categories';
import { AdminSession, ApiRequestError } from '@/services/admin-monitoring';

const PAGE_SIZE = 50;

type FilterKey = 'all' | 'active' | 'inactive';

const FILTERS: { key: FilterKey; label: string; active?: boolean }[] = [
  { key: 'all', label: 'All' },
  { key: 'active', label: 'Active', active: true },
  { key: 'inactive', label: 'Inactive', active: false },
];

/** Quick picks for the color field; any #RRGGBB value can still be typed. */
const COLOR_PRESETS = ['#1F9D55', '#2D7FF9', '#E67E22', '#8E44AD', '#D93025', '#F1C40F', '#16A085', '#7F8C8D'];

const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;

type FormState = { name: string; description: string; icon: string; color: string };

const EMPTY_FORM: FormState = { name: '', description: '', icon: '', color: '' };

/** null: form closed. 'new': creating. A number: editing that category. */
type Editing = null | 'new' | number;

/**
 * US09: lets an administrator manage the default expense categories new users start with.
 * Categories are never deleted, only deactivated, so the backend has no DELETE.
 */
export function DefaultCategoriesManager({
  session,
  contentStyle,
  onSignOut,
  header,
}: {
  session: AdminSession;
  contentStyle: object | undefined;
  onSignOut: (message?: string) => void;
  header: ReactNode;
}) {
  const theme = useTheme();
  const [categories, setCategories] = useState<DefaultCategory[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [filter, setFilter] = useState<FilterKey>('all');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [editing, setEditing] = useState<Editing>(null);
  // Id of the category whose Activate/Deactivate call is in flight.
  const [togglingId, setTogglingId] = useState<number | null>(null);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && (e.status === 401 || e.status === 403)) {
        onSignOut(
          e.status === 401 ? 'Your session has expired. Please sign in again.' : describeError(e),
        );
        return;
      }
      setMessage(describeError(e));
    },
    [onSignOut],
  );

  const activeFilter = FILTERS.find((f) => f.key === filter)?.active;

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      const result = await fetchCategories(session, { active: activeFilter, page: 0, size: PAGE_SIZE });
      setCategories(result.content);
      setPage(0);
      setTotalPages(result.page.totalPages);
      setTotal(result.page.totalElements);
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, activeFilter, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  const loadMore = async () => {
    setLoadingMore(true);
    try {
      const next = await fetchCategories(session, { active: activeFilter, page: page + 1, size: PAGE_SIZE });
      setCategories((current) => [...current, ...next.content]);
      setPage(next.page.number);
      setTotalPages(next.page.totalPages);
    } catch (e) {
      handleError(e);
    } finally {
      setLoadingMore(false);
    }
  };

  const onSaved = (saved: DefaultCategory, created: boolean) => {
    setEditing(null);
    setNotice(created ? `“${saved.name}” created.` : `“${saved.name}” updated.`);
    load();
  };

  const toggleActive = async (category: DefaultCategory) => {
    setTogglingId(category.id);
    setMessage(null);
    setNotice(null);
    try {
      const saved = await setCategoryActive(session, category.id, !category.active);
      setNotice(`“${saved.name}” ${saved.active ? 'activated' : 'deactivated'}.`);
      load();
    } catch (e) {
      handleError(e);
    } finally {
      setTogglingId(null);
    }
  };

  const editedCategory = typeof editing === 'number' ? categories.find((c) => c.id === editing) : undefined;

  return (
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[styles.contentContainer, contentStyle]}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={loading && categories.length > 0} onRefresh={load} />}>
      <View style={styles.page}>
        {header}
        <View style={styles.headerRow}>
          <View>
            <ThemedText type="subtitle">Default categories</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              What new users start with · signed in as {session.userName}
            </ThemedText>
          </View>
          <View style={styles.row}>
            <Button label="Refresh" onPress={load} disabled={loading} secondary />
            <Button label="Sign out" onPress={() => onSignOut()} secondary />
          </View>
        </View>

        {message && <ThemedText style={styles.errorText}>{message}</ThemedText>}
        {notice && <ThemedText style={styles.noticeText}>{notice}</ThemedText>}

        {editing === 'new' ? (
          <CategoryForm
            session={session}
            onSaved={(saved) => onSaved(saved, true)}
            onCancel={() => setEditing(null)}
            onAuthError={handleError}
          />
        ) : (
          <Button
            label="New category"
            onPress={() => {
              setNotice(null);
              setEditing('new');
            }}
          />
        )}

        <View style={styles.headerRow}>
          <ThemedText type="smallBold">Categories {!loading ? `(${total})` : ''}</ThemedText>
          <View style={styles.row}>
            {FILTERS.map((f) => (
              <Chip key={f.key} label={f.label} selected={filter === f.key} onPress={() => setFilter(f.key)} />
            ))}
          </View>
        </View>

        {loading && categories.length === 0 && <ActivityIndicator style={styles.loader} />}

        {!loading && categories.length === 0 && (
          <ThemedView type="backgroundElement" style={styles.card}>
            <ThemedText themeColor="textSecondary">No categories match this filter.</ThemedText>
          </ThemedView>
        )}

        {categories.map((category) =>
          editedCategory?.id === category.id ? (
            <CategoryForm
              key={category.id}
              session={session}
              category={category}
              onSaved={(saved) => onSaved(saved, false)}
              onCancel={() => setEditing(null)}
              onAuthError={handleError}
            />
          ) : (
            <CategoryItem
              key={category.id}
              category={category}
              toggling={togglingId === category.id}
              onEdit={() => {
                setNotice(null);
                setEditing(category.id);
              }}
              onToggleActive={() => toggleActive(category)}
            />
          ),
        )}

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

function CategoryItem({
  category,
  toggling,
  onEdit,
  onToggleActive,
}: {
  category: DefaultCategory;
  toggling: boolean;
  onEdit: () => void;
  onToggleActive: () => void;
}) {
  return (
    <ThemedView type="backgroundElement" style={[styles.card, !category.active && styles.inactiveCard]}>
      <View style={styles.titleRow}>
        <View style={styles.row}>
          <ColorDot color={category.color} />
          <ThemedText type="smallBold">{category.name}</ThemedText>
        </View>
        <View
          style={[styles.badge, { backgroundColor: category.active ? SUCCESS_COLOR : DANGER_COLOR }]}>
          <ThemedText type="smallBold" style={styles.badgeText}>
            {category.active ? 'ACTIVE' : 'INACTIVE'}
          </ThemedText>
        </View>
      </View>
      {category.description && <ThemedText type="small">{category.description}</ThemedText>}
      {category.icon && (
        <ThemedText type="small" themeColor="textSecondary">
          Icon: {category.icon}
        </ThemedText>
      )}
      <View style={[styles.row, styles.itemActions]}>
        <Button label="Edit" onPress={onEdit} secondary onCard />
        <Button
          label={toggling ? 'Saving…' : category.active ? 'Deactivate' : 'Activate'}
          onPress={onToggleActive}
          disabled={toggling}
          secondary
          onCard
        />
      </View>
    </ThemedView>
  );
}

/** Create form when `category` is absent, edit form otherwise. */
function CategoryForm({
  session,
  category,
  onSaved,
  onCancel,
  onAuthError,
}: {
  session: AdminSession;
  category?: DefaultCategory;
  onSaved: (saved: DefaultCategory) => void;
  onCancel: () => void;
  onAuthError: (e: unknown) => void;
}) {
  const theme = useTheme();
  const [form, setForm] = useState<FormState>(
    category
      ? {
          name: category.name,
          description: category.description ?? '',
          icon: category.icon ?? '',
          color: category.color ?? '',
        }
      : EMPTY_FORM,
  );
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const set = (field: keyof FormState) => (value: string) => setForm((f) => ({ ...f, [field]: value }));

  const save = async () => {
    // Same rules as the backend, checked first so the admin gets instant feedback.
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = 'Name is required';
    if (form.color.trim() && !HEX_COLOR.test(form.color.trim())) {
      errors.color = 'Color must be a hex value like #1F9D55';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    // PUT is a full replace, so empty optional fields are sent as absent and get cleared.
    const body: SaveCategory = {
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      icon: form.icon.trim() || undefined,
      color: form.color.trim() || undefined,
    };

    setSaving(true);
    setError(null);
    try {
      onSaved(category ? await updateCategory(session, category.id, body) : await createCategory(session, body));
    } catch (e) {
      if (e instanceof ApiRequestError && (e.status === 401 || e.status === 403)) {
        onAuthError(e);
      } else if (e instanceof ApiRequestError && e.status === 409) {
        setFieldErrors({ name: e.message });
      } else {
        setFieldErrors(e instanceof ApiRequestError ? e.fieldErrors : {});
        setError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  const inputStyle = [
    styles.input,
    { color: theme.text, backgroundColor: theme.background, borderColor: theme.backgroundSelected },
  ];

  return (
    <ThemedView type="backgroundElement" style={styles.card}>
      <ThemedText type="smallBold">{category ? `Edit “${category.name}”` : 'New category'}</ThemedText>

      <Field label="Name" error={fieldErrors.name}>
        <TextInput
          style={inputStyle}
          placeholder="e.g. Transport"
          placeholderTextColor={theme.textSecondary}
          maxLength={50}
          value={form.name}
          onChangeText={set('name')}
        />
      </Field>

      <Field label="Description (optional)" error={fieldErrors.description}>
        <TextInput
          style={inputStyle}
          placeholder="What belongs in this category"
          placeholderTextColor={theme.textSecondary}
          maxLength={255}
          value={form.description}
          onChangeText={set('description')}
        />
      </Field>

      <Field label="Icon (optional)" error={fieldErrors.icon}>
        <TextInput
          style={inputStyle}
          placeholder="e.g. car"
          placeholderTextColor={theme.textSecondary}
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={50}
          value={form.icon}
          onChangeText={set('icon')}
        />
      </Field>

      <Field label="Color (optional)" error={fieldErrors.color}>
        <View style={styles.row}>
          {COLOR_PRESETS.map((color) => (
            <Pressable
              key={color}
              accessibilityLabel={`Use color ${color}`}
              onPress={() => set('color')(color)}
              style={({ pressed }) => pressed && styles.pressed}>
              <View
                style={[
                  styles.swatch,
                  { backgroundColor: color },
                  form.color.toUpperCase() === color && { borderColor: theme.text },
                ]}
              />
            </Pressable>
          ))}
        </View>
        <View style={styles.row}>
          <ColorDot color={HEX_COLOR.test(form.color) ? form.color : null} />
          <TextInput
            style={[inputStyle, styles.colorInput]}
            placeholder="#RRGGBB"
            placeholderTextColor={theme.textSecondary}
            autoCapitalize="characters"
            autoCorrect={false}
            maxLength={7}
            value={form.color}
            onChangeText={set('color')}
          />
        </View>
      </Field>

      {error && <ThemedText style={styles.errorText}>{error}</ThemedText>}

      <View style={styles.row}>
        <Button label={saving ? 'Saving…' : 'Save'} onPress={save} disabled={saving} />
        <Button label="Cancel" onPress={onCancel} disabled={saving} secondary onCard />
      </View>
    </ThemedView>
  );
}

function Field({ label, error, children }: { label: string; error?: string; children: ReactNode }) {
  return (
    <View style={styles.field}>
      <ThemedText type="small" themeColor="textSecondary">
        {label}
      </ThemedText>
      {children}
      {error && (
        <ThemedText type="small" style={styles.errorText}>
          {error}
        </ThemedText>
      )}
    </View>
  );
}

function ColorDot({ color }: { color: string | null }) {
  const theme = useTheme();
  return (
    <View
      style={[
        styles.dot,
        color ? { backgroundColor: color } : { borderWidth: 1, borderColor: theme.textSecondary },
      ]}
    />
  );
}

const styles = StyleSheet.create({
  contentContainer: {
    flexDirection: 'row',
    justifyContent: 'center',
    flexGrow: 1,
  },
  page: {
    flexGrow: 1,
    maxWidth: MaxContentWidth,
    gap: Spacing.three,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.four,
  },
  headerRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'center',
    gap: Spacing.two,
  },
  titleRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    gap: Spacing.two,
  },
  itemActions: {
    marginTop: Spacing.two,
  },
  loader: {
    paddingVertical: Spacing.five,
  },
  card: {
    borderRadius: Spacing.three,
    padding: Spacing.three,
    gap: Spacing.two,
  },
  inactiveCard: {
    opacity: 0.7,
  },
  field: {
    gap: Spacing.one,
  },
  input: {
    borderWidth: 1,
    borderRadius: Spacing.three,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
    fontSize: 16,
  },
  colorInput: {
    flexGrow: 1,
  },
  swatch: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  dot: {
    width: 14,
    height: 14,
    borderRadius: 7,
  },
  badge: {
    borderRadius: Spacing.two,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.half,
  },
  badgeText: {
    color: '#ffffff',
  },
  errorText: {
    color: DANGER_COLOR,
  },
  noticeText: {
    color: SUCCESS_COLOR,
  },
  pressed: {
    opacity: 0.6,
  },
});
