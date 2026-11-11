import { useCallback, useEffect, useState } from 'react';
import { ActivityIndicator, Animated, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { EmojiPicker } from '@/components/ui/emoji-picker';
import { Icon, type IconName } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { TextField, financeStyles } from '@/features/finances/components/finance-ui';
import { Brand, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import {
  type IncomeSource,
  createIncomeSource,
  deleteIncomeSource,
  fetchIncomeSources,
  updateIncomeSource,
} from '@/features/finances/lib/income-api';
import { useShake } from '@/hooks/use-shake';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

/** The icons an income source may wear. A short list on purpose: a long one is a decision, not a choice. */
const ICONS: IconName[] = [
  'wallet',
  'trendUp',
  'bolt',
  'heart',
  'verified',
  'document',
  'database',
  'cloud',
  'verified',
  'person',
  'analytics',
  'more',
];

/** The palette, drawn from the app's own colours so a new source never clashes. */
const COLORS = ['#1F9D55', '#2D7FF9', '#8E44AD', '#F1C40F', '#D93025', '#16A085', '#D35400', '#7F8C8D'];

/**
 * Managing where income comes from.
 *
 * <p>Deliberately the same shape as the expense categories: a list, an add button, a form with a
 * name, an icon and a colour. Sources are the vocabulary of the Income list, so they get the same
 * care as categories get for spending.
 *
 * <p>A source in use cannot be deleted, and the server says which entries block it - by name, or by
 * date when the entry was never named.
 */
export function IncomeSourcesManager({
  session,
  onToast,
  onSignOut,
}: {
  session: Session;
  onToast: (kind: 'created' | 'edited' | 'deleted', message: string) => void;
  onSignOut: (message?: string) => void;
}) {
  const theme = useTheme();
  const { offset, shake } = useShake();

  const [sources, setSources] = useState<IncomeSource[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<IncomeSource | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  /** An emoji (or a legacy symbol name) - whatever the badge should draw. */
  const [icon, setIcon] = useState<string>('💼');
  const [color, setColor] = useState(COLORS[0]);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

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

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setSources(await fetchIncomeSources(session));
    } catch (e) {
      handleError(e);
    } finally {
      setLoading(false);
    }
  }, [session, handleError]);

  useEffect(() => {
    load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    setName('');
    setDescription('');
    setIcon('💼');
    setColor(COLORS[0]);
    setFormError(null);
    setFormOpen(true);
  };

  const openEdit = (source: IncomeSource) => {
    setEditing(source);
    setName(source.name);
    setDescription(source.description ?? '');
    setIcon(source.icon ?? '💼');
    setColor(source.color ?? COLORS[0]);
    setFormError(null);
    setFormOpen(true);
  };

  const save = async () => {
    if (!name.trim()) {
      setFormError('Give the source a name.');
      shake();
      return;
    }

    setSaving(true);
    setFormError(null);

    try {
      if (editing) {
        await updateIncomeSource(session, editing.id, {
          name: name.trim(),
          icon,
          color,
          description: description.trim() || undefined,
        });
        onToast('edited', `${name.trim()} has been updated.`);
      } else {
        await createIncomeSource(session, {
          name: name.trim(),
          icon,
          color,
          description: description.trim() || undefined,
        });
        onToast('created', `${name.trim()} is ready to use.`);
      }
      setFormOpen(false);
      load();
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        onSignOut('Your session has expired. Please sign in again.');
      } else {
        setFormError(describeError(e));
        shake();
      }
    } finally {
      setSaving(false);
    }
  };

  const remove = async (source: IncomeSource) => {
    try {
      await deleteIncomeSource(session, source.id);
      onToast('deleted', `${source.name} has been deleted.`);
      setFormOpen(false);
      load();
    } catch (e) {
      // A refusal names the entries in the way; show it rather than a generic failure.
      setFormError(describeError(e));
      shake();
    }
  };

  return (
    <View style={styles.screen}>
      {error ? <ThemedText style={styles.error}>{error}</ThemedText> : null}
      {loading && sources.length === 0 ? <ActivityIndicator style={styles.loader} /> : null}

      {sources.map((source) => (
        <Pressable
          key={source.id}
          onPress={() => openEdit(source)}
          accessibilityRole="button"
          accessibilityLabel={`Edit ${source.name}`}
          style={({ pressed }) => [pressed && styles.pressed]}>
          <ThemedView type="backgroundElement" style={styles.row}>
            <CategoryBadge icon={source.icon} color={source.color ?? COLORS[0]} size={32} />

            <View style={styles.rowCopy}>
              <ThemedText type="small" numberOfLines={1}>
                {source.name}
              </ThemedText>
              <ThemedText type="caption" themeColor="textMuted" numberOfLines={1}>
                {source.incomeCount === 0
                  ? 'Not used yet'
                  : `${source.incomeCount} ${source.incomeCount === 1 ? 'entry' : 'entries'}`}
                {source.description ? ` · ${source.description}` : ''}
              </ThemedText>
            </View>

            <Icon name="chevronRight" size={16} color={theme.textMuted} />
          </ThemedView>
        </Pressable>
      ))}

      {!loading && sources.length === 0 ? (
        <ThemedText themeColor="textSecondary">
          No sources yet. Add the first one - Salary, or whatever your money arrives as.
        </ThemedText>
      ) : null}

      <Pressable
        onPress={openCreate}
        accessibilityRole="button"
        accessibilityLabel="Add an income source"
        style={({ pressed }) => [styles.addRow, { borderColor: theme.border }, pressed && styles.pressed]}>
        <Icon name="add" size={18} color={Brand.green} />
        <ThemedText type="small" style={{ color: Brand.green }}>
          Add an income source
        </ThemedText>
      </Pressable>

      <Popup visible={formOpen} onClose={() => setFormOpen(false)} expand>
        <Animated.View style={[styles.body, { transform: [{ translateX: offset }] }]}>
          <ThemedText type="hero">{editing ? 'Edit source' : 'New source'}</ThemedText>

          <View style={styles.block}>
            <ThemedText type="small" themeColor="textSecondary">
              Name
            </ThemedText>
            <TextField
              placeholder="e.g. Salary"
              maxLength={60}
              value={name}
              onChangeText={setName}
              accessibilityLabel="Source name"
            />
          </View>

          <View style={styles.block}>
            <ThemedText type="small" themeColor="textSecondary">
              Icon
            </ThemedText>

            <View style={styles.swatches}>
              <EmojiPicker value={icon} onChange={setIcon} accent={color} />
            </View>
          </View>

          <View style={styles.block}>
            <ThemedText type="small" themeColor="textSecondary">
              Colour
            </ThemedText>

            <View style={styles.swatches}>
              {COLORS.map((candidate) => (
                <Pressable
                  key={candidate}
                  onPress={() => setColor(candidate)}
                  accessibilityRole="button"
                  accessibilityLabel={`Colour ${candidate}`}
                  accessibilityState={{ selected: candidate === color }}
                  style={({ pressed }) => [
                    styles.colorSwatch,
                    { backgroundColor: candidate, borderColor: candidate === color ? theme.text : 'transparent' },
                    pressed && styles.pressed,
                  ]}
                />
              ))}
            </View>
          </View>

          <View style={styles.block}>
            <ThemedText type="small" themeColor="textSecondary">
              Description (optional)
            </ThemedText>
            <TextField
              placeholder="e.g. Your regular pay"
              maxLength={200}
              value={description}
              onChangeText={setDescription}
              accessibilityLabel="Source description"
            />
          </View>

          {/* The preview: what the row will look like in the income list. */}
          <ThemedView type="backgroundElement" style={styles.preview}>
            <CategoryBadge icon={icon} color={color} size={32} />
            <ThemedText type="small" numberOfLines={1}>
              {name.trim() || 'Your source'}
            </ThemedText>
          </ThemedView>

          {formError ? <ThemedText style={financeStyles.errorText}>{formError}</ThemedText> : null}

          <View style={styles.actions}>
            <Button
              primary
              label={saving ? 'Saving…' : editing ? 'Save changes' : 'Add source'}
              onPress={save}
              disabled={saving}
              style={styles.grow}
            />
            <Button label="Cancel" onPress={() => setFormOpen(false)} secondary onCard style={styles.grow} />
          </View>

          {editing ? (
            <Pressable
              onPress={() => remove(editing)}
              accessibilityRole="button"
              accessibilityLabel={`Delete ${editing.name}`}
              style={({ pressed }) => [styles.deleteRow, pressed && styles.pressed]}>
              <Icon name="delete" size={16} color={Status.danger} />
              <ThemedText type="small" style={{ color: Status.danger }}>
                Delete this source
              </ThemedText>
            </Pressable>
          ) : null}
        </Animated.View>
      </Popup>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { gap: Spacing.two },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two + Spacing.one,
  },
  rowCopy: { flex: 1, gap: Spacing.half, minWidth: 0 },
  addRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderRadius: Radius.field,
    paddingVertical: Spacing.three,
    marginTop: Spacing.one,
  },
  body: { gap: Spacing.three },
  block: { gap: Spacing.two },
  swatches: { flexDirection: 'row', flexWrap: 'wrap', gap: Spacing.two },
  iconSwatch: {
    width: 44,
    height: 44,
    borderWidth: 1,
    borderRadius: Radius.field,
    alignItems: 'center',
    justifyContent: 'center',
  },
  colorSwatch: { width: 32, height: 32, borderRadius: 16, borderWidth: 2 },
  preview: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderRadius: Radius.field,
    padding: Spacing.two,
  },
  actions: { flexDirection: 'row', gap: Spacing.two },
  grow: { flex: 1 },
  deleteRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
    paddingVertical: Spacing.two,
  },
  loader: { marginTop: Spacing.four },
  error: { color: Status.danger },
  pressed: { opacity: 0.7 },
});
