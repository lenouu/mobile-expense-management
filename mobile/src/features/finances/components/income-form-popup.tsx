import { useEffect, useMemo, useState } from 'react';
import { Animated, Pressable, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Calendar } from '@/components/ui/calendar';
import * as ImagePicker from 'expo-image-picker';
import { Image } from 'expo-image';

import { Icon } from '@/components/ui/icon';
import { IncomeCsvPopup } from '@/features/finances/components/income-csv-popup';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { Brand, Radius, Spacing, Status } from '@/constants/theme';
import { ApiRequestError } from '@/features/auth/lib/api-client';
import { CategoryBadge } from '@/features/finances/components/category-badge';
import { TextField, financeStyles, formatLongDate, formatMoney } from '@/features/finances/components/finance-ui';
import type { IncomeEntry, IncomeSource } from '@/features/finances/lib/income-api';
import { createIncome, updateIncome } from '@/features/finances/lib/income-api';
import { useShake } from '@/hooks/use-shake';
import { useTheme } from '@/hooks/use-theme';
import type { Session } from '@/types/api';
import { describeError } from '@/utils/errors';

const DEFAULT_SOURCE_COLOR = '#1F9D55';

function todayIso(): string {
  const now = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`;
}

/**
 * Recording or changing one income entry.
 *
 * <p>Two things are required - an amount and a source - because a payment with no origin cannot be
 * counted anywhere. The name and the note are optional: "November salary" and "after tax, includes
 * bonus" are things a person adds, not things the app demands.
 */
export function IncomeFormPopup({
  visible,
  onClose,
  session,
  sources,
  entry,
  onSaved,
  onDelete,
  onAuthError,
}: {
  visible: boolean;
  onClose: () => void;
  session: Session;
  sources: IncomeSource[];
  /** Present when changing an existing entry. */
  entry: IncomeEntry | null;
  onSaved: (message: string, kind: 'created' | 'edited') => void;
  onDelete?: () => void;
  onAuthError: () => void;
}) {
  const theme = useTheme();
  const { offset, shake } = useShake();

  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(todayIso());
  const [sourceId, setSourceId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [pickerOpen, setPickerOpen] = useState(false);
  const [calendarOpen, setCalendarOpen] = useState(false);
  const [csvOpen, setCsvOpen] = useState(false);
  const [photo, setPhoto] = useState<{ uri: string; source: 'camera' | 'library' } | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;

    setError(null);
    setPickerOpen(false);
    setCalendarOpen(false);
    setCsvOpen(false);
    setPhoto(null);

    if (entry) {
      setAmount(String(entry.amount.amount));
      setDate(entry.date);
      setSourceId(entry.source?.id ?? null);
      setName(entry.name ?? '');
      setDescription(entry.description ?? '');
    } else {
      setAmount('');
      setDate(todayIso());
      setSourceId(sources[0]?.id ?? null);
      setName('');
      setDescription('');
    }
  }, [visible, entry, sources]);

  const parsed = useMemo(() => {
    const value = Number(amount.replace(/\s/g, '').replace(',', '.'));
    return Number.isFinite(value) && value > 0 ? value : 0;
  }, [amount]);

  const problem = useMemo(() => {
    if (parsed <= 0) return 'Enter how much you received.';
    if (!sourceId) return 'Choose where it came from.';
    if (date > todayIso()) return 'Income cannot be dated in the future.';
    return null;
  }, [parsed, sourceId, date]);

  const selected = sources.find((source) => source.id === sourceId) ?? null;

  const save = async () => {
    if (problem) {
      setError(problem);
      shake();
      return;
    }

    setSaving(true);
    setError(null);

    const body = {
      amount: { amount: parsed, currency: session.baseCurrency },
      date,
      sourceId: sourceId!,
      name: name.trim() || undefined,
      description: description.trim() || undefined,
    };

    try {
      if (entry) {
        await updateIncome(session, entry.id, body);
        onSaved('Income updated.', 'edited');
      } else {
        await createIncome(session, body);
        onSaved(
          `${formatMoney({ amount: parsed, currency: session.baseCurrency })} recorded.`,
          'created',
        );
      }
    } catch (e) {
      if (e instanceof ApiRequestError && e.status === 401) {
        onAuthError();
      } else {
        setError(describeError(e));
        shake();
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Popup visible={visible} onClose={onClose}>
      <Animated.View style={[styles.body, { transform: [{ translateX: offset }] }]}>
        <ThemedText type="hero">{entry ? 'Edit income' : 'Add income'}</ThemedText>

        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            How much
          </ThemedText>
          <View style={styles.amountRow}>
            <TextField
              placeholder="0"
              keyboardType="decimal-pad"
              value={amount}
              onChangeText={setAmount}
              style={styles.amountInput}
              accessibilityLabel="Amount received"
            />
            <ThemedText type="hero" themeColor="textMuted">
              {session.baseCurrency}
            </ThemedText>
          </View>
        </View>

        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            Where it came from
          </ThemedText>

          <Pressable
            onPress={() => setPickerOpen((open) => !open)}
            accessibilityRole="button"
            accessibilityLabel="Choose a source"
            style={({ pressed }) => [styles.picker, { borderColor: theme.border }, pressed && styles.pressed]}>
            <CategoryBadge
              icon={selected?.icon ?? null}
              color={selected?.color ?? DEFAULT_SOURCE_COLOR}
              size={26}
            />
            <ThemedText type="small" style={styles.grow} numberOfLines={1}>
              {selected?.name ?? 'Choose a source'}
            </ThemedText>
            <Icon name={pickerOpen ? 'chevronUp' : 'chevronDown'} size={18} color={theme.textSecondary} />
          </Pressable>

          {pickerOpen
            ? sources.map((source) => (
                <Pressable
                  key={source.id}
                  onPress={() => {
                    setSourceId(source.id);
                    setPickerOpen(false);
                  }}
                  accessibilityRole="button"
                  accessibilityLabel={source.name}
                  style={({ pressed }) => [
                    styles.picker,
                    { borderColor: source.id === sourceId ? Brand.green : theme.border },
                    pressed && styles.pressed,
                  ]}>
                  <CategoryBadge icon={source.icon} color={source.color ?? DEFAULT_SOURCE_COLOR} size={26} />
                  <ThemedText type="small" style={styles.grow} numberOfLines={1}>
                    {source.name}
                  </ThemedText>
                  {source.id === sourceId ? <Icon name="verified" size={18} color={Brand.green} /> : null}
                </Pressable>
              ))
            : null}
        </View>

        <Pressable
          onPress={() => setCalendarOpen((open) => !open)}
          accessibilityRole="button"
          accessibilityLabel={`Received on ${formatLongDate(date)}. Change it.`}
          style={({ pressed }) => [styles.picker, { borderColor: theme.border }, pressed && styles.pressed]}>
          <Icon name="clock" size={18} color={theme.textSecondary} />
          <ThemedText type="small" style={styles.grow}>
            {formatLongDate(date)}
          </ThemedText>
          <Icon name={calendarOpen ? 'chevronUp' : 'chevronDown'} size={18} color={theme.textSecondary} />
        </Pressable>

        {calendarOpen ? (
          <Calendar
            value={date}
            max={todayIso()}
            onSelect={(isoDate) => {
              setDate(isoDate);
              setCalendarOpen(false);
            }}
          />
        ) : null}

        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            Name (optional)
          </ThemedText>
          <TextField
            placeholder="e.g. November salary"
            maxLength={100}
            value={name}
            onChangeText={setName}
            accessibilityLabel="Income name"
          />
        </View>

        <View style={styles.block}>
          <ThemedText type="small" themeColor="textSecondary">
            Description (optional)
          </ThemedText>
          <TextField
            placeholder="e.g. after tax, includes bonus"
            maxLength={255}
            value={description}
            onChangeText={setDescription}
            accessibilityLabel="Income description"
          />
        </View>

        {error ? <ThemedText style={financeStyles.errorText}>{error}</ThemedText> : null}

        {photo ? (
          <View style={[styles.photoCard, { borderColor: theme.border }]}>
            <Image source={{ uri: photo.uri }} style={styles.photo} contentFit="cover" />
            <View style={styles.photoCopy}>
              <ThemedText type="small">
                {photo.source === 'camera' ? 'Photographed' : 'From your library'}
              </ThemedText>
              <ThemedText type="caption" themeColor="textMuted">
                Preview only — not uploaded yet
              </ThemedText>
            </View>
            <Pressable
              onPress={() => setPhoto(null)}
              accessibilityRole="button"
              accessibilityLabel="Remove the photo"
              hitSlop={8}
              style={({ pressed }) => [pressed && styles.pressed]}>
              <Icon name="close" size={16} color={theme.textSecondary} />
            </Pressable>
          </View>
        ) : null}

        {/* The same three ways in as an expense: type it, import a file, or photograph it. */}
        <View style={styles.ways}>
          <Pressable
            onPress={() => setCsvOpen(true)}
            accessibilityRole="button"
            accessibilityLabel="Import income from a CSV file"
            style={({ pressed }) => [styles.way, { borderColor: theme.border }, pressed && styles.pressed]}>
            <Icon name="upload" size={18} color={theme.textSecondary} />
            <ThemedText type="caption" themeColor="textSecondary">
              Import CSV
            </ThemedText>
          </Pressable>

          <Pressable
            onPress={async () => {
              const result = await ImagePicker.launchImageLibraryAsync({
                mediaTypes: ['images'],
                quality: 0.6,
              });
              if (!result.canceled && result.assets[0]) {
                setPhoto({ uri: result.assets[0].uri, source: 'library' });
              }
            }}
            accessibilityRole="button"
            accessibilityLabel="Choose a photo of a receipt"
            style={({ pressed }) => [styles.way, { borderColor: theme.border }, pressed && styles.pressed]}>
            <Icon name="document" size={18} color={theme.textSecondary} />
            <ThemedText type="caption" themeColor="textSecondary">
              File
            </ThemedText>
          </Pressable>

          <Pressable
            onPress={async () => {
              const permission = await ImagePicker.requestCameraPermissionsAsync();
              if (!permission.granted) return;

              const result = await ImagePicker.launchCameraAsync({ quality: 0.6 });
              if (!result.canceled && result.assets[0]) {
                setPhoto({ uri: result.assets[0].uri, source: 'camera' });
              }
            }}
            accessibilityRole="button"
            accessibilityLabel="Take a photo"
            style={({ pressed }) => [styles.way, { borderColor: theme.border }, pressed && styles.pressed]}>
            <Icon name="camera" size={18} color={theme.textSecondary} />
            <ThemedText type="caption" themeColor="textSecondary">
              Photo
            </ThemedText>
          </Pressable>
        </View>

        <View style={styles.actions}>
          <Button
            primary={problem === null}
            label={saving ? 'Saving…' : entry ? 'Save changes' : 'Add income'}
            onPress={save}
            disabled={saving}
            style={styles.grow}
          />
          <Button label="Cancel" onPress={onClose} secondary onCard style={styles.grow} />
        </View>

        {onDelete ? (
          <Pressable
            onPress={onDelete}
            accessibilityRole="button"
            accessibilityLabel="Delete this income entry"
            style={({ pressed }) => [styles.deleteRow, pressed && styles.pressed]}>
            <Icon name="delete" size={16} color={Status.danger} />
            <ThemedText type="small" style={{ color: Status.danger }}>
              Delete this entry
            </ThemedText>
          </Pressable>
        ) : null}
      </Animated.View>

      <IncomeCsvPopup
        visible={csvOpen}
        onClose={() => setCsvOpen(false)}
        sources={sources.map((source) => ({ id: source.id, name: source.name }))}
      />
    </Popup>
  );
}

const styles = StyleSheet.create({
  body: { gap: Spacing.three },
  block: { gap: Spacing.two },
  amountRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  amountInput: { flex: 1, fontSize: 28, lineHeight: 36, fontWeight: '700', minHeight: 52 },
  picker: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  grow: { flex: 1, minWidth: 0 },
  ways: { flexDirection: 'row', gap: Spacing.two },
  way: {
    flex: 1,
    alignItems: 'center',
    gap: Spacing.half,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderRadius: Radius.field,
    paddingVertical: Spacing.two,
  },
  photoCard: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    padding: Spacing.two,
  },
  photo: { width: 52, height: 52, borderRadius: Radius.field },
  photoCopy: { flex: 1, gap: Spacing.half },
  actions: { flexDirection: 'row', gap: Spacing.two },
  deleteRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.one,
    paddingVertical: Spacing.two,
  },
  pressed: { opacity: 0.7 },
});
