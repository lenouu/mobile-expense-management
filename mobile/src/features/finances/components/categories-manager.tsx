import { ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/ui/primitives';
import { ColorPicker } from '@/components/ui/color-picker';
import { EmojiPicker } from '@/components/ui/emoji-picker';
import { FadeIn } from '@/components/ui/fade-in';
import { Icon } from '@/components/ui/icon';
import { Pagination } from '@/components/ui/pagination';
import { Popup } from '@/components/ui/popup';
import { TopToast, type ToastKind, type ToastMessage } from '@/components/ui/top-toast';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { IncomeSourcesManager } from '@/features/finances/components/income-sources-manager';
import { DeleteButton, Field, financeStyles, TextField } from '@/features/finances/components/finance-ui';
import { Brand, Radius, Shadows, Spacing } from '@/constants/theme';
import { describeError } from '@/utils/errors';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { useTheme } from '@/hooks/use-theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import type { Session } from '@/types/api';
import {
  createMyCategory,
  deleteMyCategory,
  fetchMyCategories,
  type MyCategory,
  type NewCategory,
  updateMyCategory,
} from '@/features/finances/lib/finances-api';

/** How many categories a page holds. */
const PAGE_SIZE = 10;

/** The colour a brand-new category starts on, so the picker always has a valid value. */
const DEFAULT_COLOR = '#01916D';

/**
 * US17: the signed-in user's own expense categories - the starters copied at sign-up (S2-TECH-1)
 * and the ones they create here. A new category is offered in "Add expense" straight away.
 */
export function CategoriesManager({
  session,
  contentStyle,
  onSignOut,
  header,
  onToast,
}: {
  session: Session;
  contentStyle: object | undefined;
  onSignOut: (message?: string) => void;
  header?: ReactNode;
  /**
   * Shows a success toast. Passed in by a screen that also draws chrome above this component (the
   * Expenses tab), so the toast can overlay that chrome; without it the manager shows its own,
   * which is right when it fills the screen by itself.
   */
  onToast?: (kind: ToastKind, message: string) => void;
}) {
  const theme = useTheme();
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [message, setMessage] = useState<string | null>(null);
  /** The success toast; failures stay inline as red text. */
  const [toast, setToast] = useState<ToastMessage | null>(null);
  /** The magnifier is closed by default so the screen opens on the list, not on a search box. */
  const [searchOpen, setSearchOpen] = useState(false);
  const [searchInput, setSearchInput] = useState('');
  const [q, setQ] = useState('');
  /** What the pop-up is for: 'new', the category being edited, or null when it is closed. */
  /** Which list is on screen: the expense categories, or the income sources. */
  const [kind, setKind] = useState<'expenses' | 'income'>('expenses');

  const [formTarget, setFormTarget] = useState<'new' | MyCategory | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const handleError = useCallback(
    (e: unknown) => {
      if (e instanceof ApiRequestError && e.status === 401) {
        onSignOut('Your session has expired. Please sign in again.');
        return;
      }
      setMessage(describeError(e));
    },
    [onSignOut],
  );

  const loadPage = useCallback(
    async (target: number, query: string) => {
      setLoading(true);
      setMessage(null);
      try {
        const result = await fetchMyCategories(session, {
          page: Math.max(0, target),
          size: PAGE_SIZE,
          q: query,
        });

        // Deleting the only row on the last page can leave that page empty; step back one.
        if (result.data.length === 0 && result.totalPages > 0 && target > 0) {
          const last = await fetchMyCategories(session, {
            page: result.totalPages - 1,
            size: PAGE_SIZE,
            q: query,
          });
          setCategories(last.data);
          setPage(last.page);
          setTotalPages(last.totalPages);
          setTotalElements(last.totalElements);
          return;
        }

        setCategories(result.data);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      } catch (e) {
        handleError(e);
      } finally {
        setLoading(false);
      }
    },
    [session, handleError],
  );

  // First load only. The guard matters: `loadPage` changes identity when the screen re-renders
  // (its dependencies include the caller's callback), and without it every parent render would
  // send the user back to page 1.
  const loadedOnce = useRef(false);
  useEffect(() => {
    if (loadedOnce.current) return;
    loadedOnce.current = true;
    loadPage(0, '');
  }, [loadPage]);

  // Typing settles for a moment before a request goes out, so a five-letter name is one request
  // rather than five.
  useEffect(() => {
    const handle = setTimeout(() => setQ(searchInput.trim()), 350);
    return () => clearTimeout(handle);
  }, [searchInput]);

  // A new query always starts at the first page: page 3 of the previous search means nothing here.
  const loadedQuery = useRef('');
  useEffect(() => {
    if (!loadedOnce.current || loadedQuery.current === q) return;
    loadedQuery.current = q;
    loadPage(0, q);
  }, [q, loadPage]);

  const reload = useCallback(() => loadPage(page, q), [loadPage, page, q]);

  const showToast = (kind: ToastKind, message: string) => {
    // A screen that draws chrome above this component hosts the toast itself, so it can overlay
    // that chrome; otherwise the manager owns it.
    if (onToast) {
      onToast(kind, message);
      return;
    }
    setToast({ id: Date.now(), message, kind });
  };

  const onSaved = (saved: MyCategory, created: boolean) => {
    // The pop-up closes with its own exit animation; `formTarget` is cleared in onClosed so the
    // form does not vanish mid-flight.
    setFormOpen(false);
    showToast(
      created ? 'created' : 'edited',
      `${saved.name} has been ${created ? 'created' : 'edited'} successfully.`,
    );
    reload();
  };

  /** Opens the pop-up for a new category, or for the one being edited. */
  const openForm = (target: 'new' | MyCategory) => {
    setMessage(null);
    setFormTarget(target);
    setFormOpen(true);
  };

  /**
   * Deletes one of the caller's own categories.
   *
   * The backend refuses while expenses still reference it (422) and says how many, so that answer
   * is shown as-is rather than being collapsed into a generic failure.
   */
  const remove = async (category: MyCategory) => {
    setDeletingId(category.id);
    setMessage(null);
    try {
      await deleteMyCategory(session, category.id);
      showToast('deleted', `${category.name} has been deleted successfully.`);
      // If that category is the one the pop-up has open, close it: it no longer exists.
      if (formTarget !== 'new' && formTarget?.id === category.id) setFormOpen(false);
      reload();
    } catch (e) {
      if (e instanceof ApiRequestError && (e.status === 422 || e.status === 409 || e.status === 404)) {
        setMessage(e.message);
      } else {
        handleError(e);
      }
    } finally {
      setDeletingId(null);
    }
  };

  return (
    <View style={styles.screen}>
      <ScrollView
        style={{ flex: 1, backgroundColor: theme.background }}
        contentInsetAdjustmentBehavior="automatic"
        contentContainerStyle={[financeStyles.contentContainer, styles.listContent, contentStyle]}
        keyboardShouldPersistTaps="handled"
        refreshControl={
          <RefreshControl refreshing={loading && categories.length > 0} onRefresh={reload} />
        }>
        <View style={financeStyles.page}>
          {/* Two vocabularies, one screen: what you spend on, and where you earn from. */}
          <View style={[styles.kindSwitch, { borderColor: theme.border }]}>
            {(['expenses', 'income'] as const).map((side) => {
              const active = side === kind;
              return (
                <Pressable
                  key={side}
                  onPress={() => setKind(side)}
                  accessibilityRole="tab"
                  accessibilityState={{ selected: active }}
                  accessibilityLabel={side === 'expenses' ? 'Expense categories' : 'Income sources'}
                  style={[
                    styles.kindTab,
                    active && { backgroundColor: theme.backgroundSelected },
                  ]}>
                  <ThemedText
                    type={active ? 'smallBold' : 'small'}
                    themeColor={active ? 'text' : 'textMuted'}>
                    {side === 'expenses' ? 'Expense categories' : 'Income sources'}
                  </ThemedText>
                </Pressable>
              );
            })}
          </View>

          {kind === 'income' ? (
            <IncomeSourcesManager session={session} onToast={onToast ?? (() => undefined)} onSignOut={onSignOut} />
          ) : (
            <>
              {header}

          {message && <ThemedText style={financeStyles.errorText}>{message}</ThemedText>}

          <View style={styles.toolbar}>
            <View style={styles.toolbarGrow}>
              <Button primary label="New category" onPress={() => openForm('new')} />
            </View>

            <Pressable
              onPress={() => {
                // Closing the field clears the search, so the list is never left silently filtered.
                if (searchOpen) {
                  setSearchInput('');
                  setQ('');
                }
                setSearchOpen((current) => !current);
              }}
              accessibilityRole="button"
              accessibilityLabel={searchOpen ? 'Close search' : 'Search categories'}
              accessibilityState={{ expanded: searchOpen }}
              hitSlop={8}
              style={({ pressed }) => [
                styles.searchButton,
                { borderColor: theme.border, backgroundColor: theme.backgroundElement },
                (pressed || searchOpen) && { borderColor: Brand.green },
              ]}>
              <Icon name="search" size={20} color={searchOpen ? Brand.green : theme.textSecondary} />
            </Pressable>
          </View>

          {searchOpen ? (
            <FadeIn>
              <TextField
                placeholder="Search a category by name"
                autoCapitalize="none"
                autoCorrect={false}
                value={searchInput}
                onChangeText={setSearchInput}
                accessibilityLabel="Search categories by name"
              />
            </FadeIn>
          ) : null}

          <ThemedText type="smallBold">
            {q ? `Matches for “${q}”` : 'My categories'} {!loading ? `(${totalElements})` : ''}
          </ThemedText>

          {loading && categories.length === 0 && <ActivityIndicator style={financeStyles.loader} />}

          {!loading && categories.length === 0 ? (
            <ThemedView type="backgroundElement" style={financeStyles.card}>
              <ThemedText themeColor="textSecondary">
                {q
                  ? `No category is named “${q}”.`
                  : 'No categories yet. Tap “New category” to add one.'}
              </ThemedText>
            </ThemedView>
          ) : null}

          {categories.map((category) => {
            const color = category.color ?? DEFAULT_COLOR;

            return (
              <ThemedView key={category.id} type="backgroundElement" style={financeStyles.card}>
                <View style={financeStyles.headerRow}>
                  <View style={financeStyles.row}>
                    {/* The icon the user chose, tinted by the colour they chose: the two
                        settings from the form are the identity of the row. */}
                    <CategoryBadge icon={category.icon} color={color} />
                    <ThemedText type="smallBold">{category.name}</ThemedText>
                  </View>
                </View>
                {category.description && (
                  <ThemedText type="small" themeColor="textSecondary">
                    {category.description}
                  </ThemedText>
                )}

                <View style={financeStyles.row}>
                  <Button label="Edit" secondary onCard onPress={() => openForm(category)} />
                  <DeleteButton onConfirm={() => remove(category)} busy={deletingId === category.id} />
                </View>
              </ThemedView>
            );
          })}

          {totalPages > 0 && (
            <Pagination
              page={page}
              totalPages={totalPages}
              onChange={(target) => loadPage(target, q)}
              disabled={loading}
            />
          )}
          </>
          )}
        </View>
      </ScrollView>

      {/* Always within thumb reach, so adding a category never means scrolling back up. */}
      <Pressable
        onPress={() => openForm('new')}
        accessibilityRole="button"
        accessibilityLabel="New category"
        style={({ pressed }) => [styles.fab, pressed && styles.fabPressed]}>
        <Icon name="add" size={28} color="#FFFFFF" />
      </Pressable>

      {/* Outside the page view but inside the screen: a Modal draws in its own window, so where
          it sits in the tree does not affect the layout behind it. */}
      <Popup
        visible={formOpen}
        onClose={() => setFormOpen(false)}
        onClosed={() => setFormTarget(null)}>
        {formTarget ? (
          <CategoryForm
            session={session}
            category={formTarget === 'new' ? null : formTarget}
            onSaved={onSaved}
            onCancel={() => setFormOpen(false)}
            onAuthError={handleError}
          />
        ) : null}
      </Popup>

      {/* Only when this component is the whole screen; otherwise the screen renders it, above
          its own header and tabs. */}
      {onToast ? null : <TopToast toast={toast} onDismiss={() => setToast(null)} />}
    </View>
  );
}

function CategoryForm({
  session,
  category,
  onSaved,
  onCancel,
  onAuthError,
}: {
  session: Session;
  /** Present when editing an existing category; absent when creating a new one. */
  category?: MyCategory | null;
  onSaved: (saved: MyCategory, created: boolean) => void;
  onCancel: () => void;
  onAuthError: (e: unknown) => void;
}) {
  const theme = useTheme();
  const [name, setName] = useState(category?.name ?? '');
  const [description, setDescription] = useState(category?.description ?? '');
  const [icon, setIcon] = useState(category?.icon ?? '');
  // The picker always hands back a valid colour, so a new category starts on the brand green
  // rather than on an empty string the picker would have to invent a value for.
  const [color, setColor] = useState(category?.color ?? DEFAULT_COLOR);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    // Same rules as the backend, checked first so the user gets instant feedback.
    const errors: Record<string, string> = {};
    if (!name.trim()) errors.name = 'Name is required';
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) return;

    const body: NewCategory = {
      name: name.trim(),
      description: description.trim() || undefined,
      icon: icon.trim() || undefined,
      color: color.trim() || undefined,
    };

    setSaving(true);
    setError(null);
    try {
      // PUT is a full replace, which is why every field is prefilled above: an omitted description
      // would otherwise clear it.
      const saved = category
        ? await updateMyCategory(session, category.id, body)
        : await createMyCategory(session, body);
      onSaved(saved, category === undefined || category === null);
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        onAuthError(e);
      } else if (e instanceof ApiRequestError && e.status === 409) {
        // "You already have a category named ..." belongs next to the name.
        setFieldErrors({ name: e.message });
      } else {
        setFieldErrors(e instanceof ApiRequestError ? e.fields : {});
        setError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <View style={styles.formBody}>
      <ThemedText type="subtitle">{category ? 'Edit category' : 'New category'}</ThemedText>

      <Field label="Name" error={fieldErrors.name}>
        <TextField placeholder="e.g. Pets" maxLength={50} value={name} onChangeText={setName} />
      </Field>

      <Field label="Description (optional)" error={fieldErrors.description}>
        <TextField
          placeholder="What belongs in this category"
          maxLength={255}
          value={description}
          onChangeText={setDescription}
        />
      </Field>

      <Field label="Icon (optional)" error={fieldErrors.icon}>
        <EmojiPicker value={icon} onChange={setIcon} accent={color} />
      </Field>

      <Field label="Colour" error={fieldErrors.color}>
        <ColorPicker value={color} onChange={setColor} />
      </Field>

      {error && <ThemedText style={financeStyles.errorText}>{error}</ThemedText>}

      <View style={financeStyles.row}>
        <Button label={saving ? 'Saving…' : 'Save'} onPress={save} disabled={saving} />
        <Button label="Cancel" onPress={onCancel} disabled={saving} secondary onCard />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  kindSwitch: {
    flexDirection: 'row',
    alignSelf: 'flex-start',
    gap: 3,
    padding: 3,
    borderWidth: 1,
    borderRadius: 999,
    marginBottom: Spacing.three,
  },
  kindTab: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.one,
    borderRadius: 999,
  },
  screen: {
    flex: 1,
  },
  /** Room at the bottom so the last row can scroll clear of the floating button. */
  listContent: {
    paddingBottom: Spacing.six + 56,
  },
  /** The pop-up supplies the surface, so the form itself is just spaced content. */
  formBody: {
    gap: 12,
  },
  /** New-category button and the search affordance, side by side. */
  toolbar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  toolbarGrow: {
    flex: 1,
  },
  searchButton: {
    width: 44,
    height: 44,
    borderRadius: Radius.field,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fab: {
    position: 'absolute',
    right: Spacing.four,
    // The scene ends where the tab bar begins, so this is measured from above the bar: 16 pt
    // clears it, and the list's bottom padding keeps the last row scrollable clear of the button.
    bottom: Spacing.four,
    width: 56,
    height: 56,
    borderRadius: 28,
    backgroundColor: Brand.green,
    alignItems: 'center',
    justifyContent: 'center',
    ...Shadows.button,
  },
  fabPressed: {
    opacity: 0.85,
    transform: [{ scale: 0.94 }],
  },
});
