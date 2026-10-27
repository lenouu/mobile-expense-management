import { ReactNode, useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Modal, Pressable, RefreshControl, ScrollView, StyleSheet, View } from 'react-native';

import { Button } from '@/components/ui/primitives';
import { Field, financeStyles, TextField } from '@/features/finances/components/finance-ui';
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
} from '@/features/finances/lib/finances-api';
import { Radius, Shadows, Spacing } from '@/constants/theme';

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
  const [categoryToDelete, setCategoryToDelete] = useState<MyCategory | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [modalError, setModalError] = useState<string | null>(null);

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

  const onCreated = (created: MyCategory) => {
    setCreating(false);
    setNotice(`“${created.name}” created. You can now use it when adding an expense.`);
    load();
  };

  const handleDeleteConfirm = async () => {
    if (!categoryToDelete) return;
    setDeleting(true);
    setModalError(null);
    setMessage(null);
    try {
      await deleteMyCategory(session, categoryToDelete.id);
      const name = categoryToDelete.name;
      setCategoryToDelete(null);
      setNotice(`“${name}” deleted.`);
      load();
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        setCategoryToDelete(null);
        handleError(e);
      } else if (e instanceof ApiRequestError && e.status === 409) {
        const errorMsg = e.message || 'Cannot delete category because it has active linked expenses.';
        setModalError(errorMsg);
        setMessage(errorMsg);
      } else if (e instanceof ApiRequestError && e.status === 404) {
        setCategoryToDelete(null);
        setNotice('This category was already deleted.');
        load();
      } else {
        const errorMsg = describeError(e);
        setModalError(errorMsg);
        setMessage(errorMsg);
      }
    } finally {
      setDeleting(false);
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
        <View style={financeStyles.headerRow}>
          <View>
            <ThemedText type="subtitle">Categories</ThemedText>
            <ThemedText type="small" themeColor="textSecondary">
              Signed in as {session.userName}
            </ThemedText>
          </View>
          <View style={financeStyles.row}>
            <Button label="Refresh" onPress={load} disabled={loading} secondary />
            <Button label="Sign out" onPress={() => onSignOut()} secondary />
          </View>
        </View>

        {message && <ThemedText style={financeStyles.errorText}>{message}</ThemedText>}
        {notice && <ThemedText style={financeStyles.noticeText}>{notice}</ThemedText>}

        {creating ? (
          <CategoryForm
            session={session}
            onCreated={onCreated}
            onCancel={() => setCreating(false)}
            onAuthError={handleError}
          />
        ) : (
          <Button
            label="New category"
            onPress={() => {
              setNotice(null);
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
                label="Delete"
                onPress={() => {
                  setMessage(null);
                  setNotice(null);
                  setModalError(null);
                  setCategoryToDelete(category);
                }}
                secondary
                onCard
              />
            </View>
          </ThemedView>
        ))}

        <Modal
          visible={categoryToDelete !== null}
          transparent
          animationType="fade"
          onRequestClose={() => {
            if (!deleting) {
              setCategoryToDelete(null);
              setModalError(null);
            }
          }}>
          <Pressable
            style={styles.modalBackdrop}
            onPress={() => {
              if (!deleting) {
                setCategoryToDelete(null);
                setModalError(null);
              }
            }}
            accessibilityRole="button">
            <Pressable
              style={[
                styles.modalCard,
                Shadows.card,
                { backgroundColor: theme.backgroundElement, borderColor: theme.border },
              ]}
              onPress={(e) => e.stopPropagation()}>
              <ThemedText type="subtitle">Delete Category</ThemedText>
              <ThemedText type="body" style={styles.modalBodyText}>
                Are you sure you want to delete this category? This action cannot be undone.
              </ThemedText>
              {categoryToDelete && (
                <View style={[financeStyles.row, { marginVertical: Spacing.one }]}>
                  <View
                    style={[
                      financeStyles.dot,
                      { backgroundColor: categoryToDelete.color ?? theme.textSecondary },
                    ]}
                  />
                  <ThemedText type="smallBold">{categoryToDelete.name}</ThemedText>
                </View>
              )}
              {modalError && <ThemedText style={financeStyles.errorText}>{modalError}</ThemedText>}
              <View style={[financeStyles.row, { justifyContent: 'flex-end', marginTop: Spacing.two }]}>
                <Button
                  label="Cancel"
                  onPress={() => {
                    setCategoryToDelete(null);
                    setModalError(null);
                  }}
                  disabled={deleting}
                  secondary
                  onCard
                />
                <Button
                  label={deleting ? 'Deleting…' : 'Delete'}
                  onPress={handleDeleteConfirm}
                  disabled={deleting}
                />
              </View>
            </Pressable>
          </Pressable>
        </Modal>
      </View>
    </ScrollView>
  );
}

function CategoryForm({
  session,
  onCreated,
  onCancel,
  onAuthError,
}: {
  session: Session;
  onCreated: (created: MyCategory) => void;
  onCancel: () => void;
  onAuthError: (e: unknown) => void;
}) {
  const theme = useTheme();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [icon, setIcon] = useState('');
  const [color, setColor] = useState('');
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
      onCreated(await createMyCategory(session, body));
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        onAuthError(e);
      } else if (e instanceof ApiRequestError && e.status === 409) {
        // "You already have a category named ..." belongs next to the name.
        setFieldErrors({ name: e.message });
      } else {
        setFieldErrors(e instanceof ApiRequestError ? e.fieldErrors : {});
        setError(describeError(e));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <ThemedView type="backgroundElement" style={financeStyles.card}>
      <ThemedText type="smallBold">New category</ThemedText>

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
  modalBackdrop: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: Spacing.four,
    backgroundColor: 'rgba(2, 40, 76, 0.45)',
  },
  modalCard: {
    width: '100%',
    maxWidth: 360,
    borderRadius: Radius.card,
    borderWidth: 1,
    padding: Spacing.four,
    alignItems: 'center',
    gap: Spacing.two,
  },
  modalBodyText: {
    textAlign: 'center',
  },
});
