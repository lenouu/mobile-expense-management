import { ReactNode, useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/ui/primitives';
import { DeleteButton, Field, financeStyles, TextField } from '@/features/finances/components/finance-ui';
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

/** Quick picks for the color field; any #RRGGBB value can still be typed. */
const COLOR_PRESETS = ['#1F9D55', '#2D7FF9', '#E67E22', '#8E44AD', '#D93025', '#F1C40F', '#16A085', '#7F8C8D'];

const HEX_COLOR = /^#[0-9A-Fa-f]{6}$/;

/**
 * US17: the signed-in user's own expense categories - the starters copied at sign-up (S2-TECH-1)
 * and the ones they create here. A new category is offered in "Add expense" straight away.
 */
export function CategoriesManager({
  session,
  contentStyle,
  onSignOut,
  header,
}: {
  session: Session;
  contentStyle: object | undefined;
  onSignOut: (message?: string) => void;
  header: ReactNode;
}) {
  const theme = useTheme();
  const [categories, setCategories] = useState<MyCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [message, setMessage] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [creating, setCreating] = useState(false);
  /** The category being edited, or null. Never opened at the same time as the create form. */
  const [editing, setEditing] = useState<MyCategory | null>(null);
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

  const load = useCallback(async () => {
    setLoading(true);
    setMessage(null);
    try {
      setCategories(await fetchMyCategories(session));
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  const onSaved = (saved: MyCategory, created: boolean) => {
    setCreating(false);
    setEditing(null);
    setNotice(
      created
        ? `“${saved.name}” created. You can now use it when adding an expense.`
        : `“${saved.name}” updated.`,
    );
    load();
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
    setNotice(null);
    try {
      await deleteMyCategory(session, category.id);
      setNotice(`“${category.name}” deleted.`);
      if (editing?.id === category.id) setEditing(null);
      load();
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
    <ScrollView
      style={{ backgroundColor: theme.background }}
      contentInsetAdjustmentBehavior="automatic"
      contentContainerStyle={[financeStyles.contentContainer, contentStyle]}
      keyboardShouldPersistTaps="handled"
      refreshControl={<RefreshControl refreshing={loading && categories.length > 0} onRefresh={load} />}>
      <View style={financeStyles.page}>
        {header}

        {message && <ThemedText style={financeStyles.errorText}>{message}</ThemedText>}
        {notice && <ThemedText style={financeStyles.noticeText}>{notice}</ThemedText>}

        {creating ? (
          <CategoryForm
            session={session}
            onSaved={onSaved}
            onCancel={() => setCreating(false)}
            onAuthError={handleError}
          />
        ) : editing ? (
          <CategoryForm
            session={session}
            category={editing}
            onSaved={onSaved}
            onCancel={() => setEditing(null)}
            onAuthError={handleError}
          />
        ) : (
          <Button
            label="New category"
            onPress={() => {
              setNotice(null);
              setEditing(null);
              setCreating(true);
            }}
          />
        )}

        <ThemedText type="smallBold">My categories {!loading ? `(${categories.length})` : ''}</ThemedText>

        {loading && categories.length === 0 && <ActivityIndicator style={financeStyles.loader} />}

        {categories.map((category) => (
          <ThemedView key={category.id} type="backgroundElement" style={financeStyles.card}>
            <View style={financeStyles.headerRow}>
              <View style={financeStyles.row}>
                <View style={[financeStyles.dot, { backgroundColor: category.color ?? theme.textSecondary }]} />
                <ThemedText type="smallBold">{category.name}</ThemedText>
              </View>
              <ThemedText type="small" themeColor="textSecondary">
                {category.defaultCategoryId !== null ? 'Starter' : 'Created by you'}
              </ThemedText>
            </View>
            {category.description && (
              <ThemedText type="small" themeColor="textSecondary">
                {category.description}
              </ThemedText>
            )}

            <View style={financeStyles.row}>
              <Button
                label="Edit"
                secondary
                onCard
                onPress={() => {
                  setNotice(null);
                  setMessage(null);
                  setCreating(false);
                  setEditing(category);
                }}
              />
              <DeleteButton
                onConfirm={() => remove(category)}
                busy={deletingId === category.id}
              />
            </View>
          </ThemedView>
        ))}
      </View>
    </ScrollView>
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
  const [color, setColor] = useState(category?.color ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const save = async () => {
    // Same rules as the backend, checked first so the user gets instant feedback.
    const errors: Record<string, string> = {};
    if (!name.trim()) errors.name = 'Name is required';
    if (color.trim() && !HEX_COLOR.test(color.trim())) errors.color = 'Color must be a hex value like #1F9D55';
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
    <ThemedView type="backgroundElement" style={financeStyles.card}>
      <ThemedText type="smallBold">{category ? 'Edit category' : 'New category'}</ThemedText>

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
        <TextField
          placeholder="e.g. paw"
          autoCapitalize="none"
          autoCorrect={false}
          maxLength={50}
          value={icon}
          onChangeText={setIcon}
        />
      </Field>

      <Field label="Color (optional)" error={fieldErrors.color}>
        <View style={financeStyles.row}>
          {COLOR_PRESETS.map((preset) => (
            <Pressable
              key={preset}
              accessibilityLabel={`Use color ${preset}`}
              onPress={() => setColor(preset)}
              style={({ pressed }) => pressed && styles.pressed}>
              <View
                style={[
                  styles.swatch,
                  { backgroundColor: preset },
                  color.toUpperCase() === preset && { borderColor: theme.text },
                ]}
              />
            </Pressable>
          ))}
        </View>
        <TextField
          placeholder="#RRGGBB"
          autoCapitalize="characters"
          autoCorrect={false}
          maxLength={7}
          value={color}
          onChangeText={setColor}
        />
      </Field>

      {error && <ThemedText style={financeStyles.errorText}>{error}</ThemedText>}

      <View style={financeStyles.row}>
        <Button label={saving ? 'Saving…' : 'Save'} onPress={save} disabled={saving} />
        <Button label="Cancel" onPress={onCancel} disabled={saving} secondary onCard />
      </View>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  swatch: {
    width: 28,
    height: 28,
    borderRadius: 14,
    borderWidth: 2,
    borderColor: 'transparent',
  },
  pressed: {
    opacity: 0.6,
  },
});
