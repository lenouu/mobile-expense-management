import * as DocumentPicker from 'expo-document-picker';
import { readAsStringAsync } from 'expo-file-system/legacy';
import { useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { Radius, Spacing, Status } from '@/constants/theme';
import {
  INCOME_CSV_TEMPLATE_EXAMPLE,
  parseIncomesCsv,
  type IncomeCsvResult,
} from '@/features/finances/lib/income-csv';
import { useTheme } from '@/hooks/use-theme';

/** How many problems are listed before the rest are summarised as a count. */
const PROBLEMS_SHOWN = 4;

/**
 * Importing income from a CSV file.
 *
 * <p>The same two steps as the expense importer - the shape of the file, then what was found in the
 * one you chose - with income's own columns: a **source** instead of a category, and an optional
 * label.
 *
 * <p>Importing itself runs on the server and that endpoint does not exist yet, so the rows are
 * parsed, checked and shown, and the sheet says so rather than pretending to save.
 */
export function IncomeCsvPopup({
  visible,
  onClose,
  sources,
}: {
  visible: boolean;
  onClose: () => void;
  /** The account's own sources: a row naming anything else cannot be imported. */
  sources: { id: string; name: string }[];
}) {
  const theme = useTheme();

  const [picking, setPicking] = useState(false);
  const [fileName, setFileName] = useState<string | null>(null);
  const [result, setResult] = useState<IncomeCsvResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    setFileName(null);
    setResult(null);
    setError(null);
    onClose();
  };

  const chooseFile = async () => {
    setPicking(true);
    setError(null);

    try {
      const picked = await DocumentPicker.getDocumentAsync({
        type: ['text/csv', 'text/comma-separated-values', 'application/vnd.ms-excel', '*/*'],
        copyToCacheDirectory: true,
        multiple: false,
      });

      if (picked.canceled || !picked.assets || picked.assets.length === 0) return;

      const file = picked.assets[0];
      const text = await readAsStringAsync(file.uri, { encoding: 'utf8' });

      setFileName(file.name);
      setResult(parseIncomesCsv(text, sources));
    } catch (e) {
      const message = e instanceof Error ? e.message : 'The file could not be read.';
      setError(`${message} Try exporting the sheet as CSV UTF-8.`);
    } finally {
      setPicking(false);
    }
  };

  const rows = result?.rows ?? [];
  const problems = result?.problems ?? [];

  return (
    <Popup visible={visible} onClose={close}>
      <View style={styles.body}>
        <ThemedText type="hero">Import income from a CSV</ThemedText>

        {result === null ? (
          <>
            <ThemedText type="small" themeColor="textSecondary">
              The first line names the columns. Order does not matter and extra columns are ignored.
            </ThemedText>

            <View style={[styles.spec, { borderColor: theme.border }]}>
              {[
                { name: 'date', format: '2026-11-05, not in the future' },
                { name: 'amount', format: '480000 or 480000.50' },
                { name: 'source', format: 'one of your sources' },
                { name: 'name', format: 'optional label' },
              ].map((column) => (
                <View key={column.name} style={styles.specRow}>
                  <ThemedText type="code" style={styles.specName}>
                    {column.name}
                  </ThemedText>
                  <ThemedText type="caption" themeColor="textMuted" style={styles.specFormat}>
                    {column.format}
                  </ThemedText>
                </View>
              ))}
            </View>

            <View
              style={[
                styles.example,
                { borderColor: theme.border, backgroundColor: theme.inputBackground },
              ]}>
              <ThemedText type="code" style={styles.exampleText}>
                {INCOME_CSV_TEMPLATE_EXAMPLE}
              </ThemedText>
            </View>

            {error ? (
              <ThemedText type="small" style={styles.error}>
                {error}
              </ThemedText>
            ) : null}

            <View style={styles.actions}>
              <Button
                primary
                label={picking ? 'Opening…' : 'Choose a file'}
                onPress={chooseFile}
                disabled={picking}
                style={styles.grow}
              />
              <Button label="Cancel" onPress={close} secondary onCard style={styles.grow} />
            </View>
          </>
        ) : (
          <>
            <View style={styles.fileRow}>
              <Icon name="document" size={16} color={theme.textSecondary} />
              <ThemedText type="caption" style={styles.fileName} numberOfLines={1}>
                {fileName}
              </ThemedText>
            </View>

            {result.headerProblems.length > 0 ? (
              <View style={[styles.notice, { borderColor: Status.danger }]}>
                {result.headerProblems.map((problem) => (
                  <ThemedText key={problem} type="caption" style={styles.error}>
                    {problem}
                  </ThemedText>
                ))}
              </View>
            ) : (
              <>
                <ThemedText type="body">
                  <ThemedText type="body" style={styles.strong}>
                    {rows.length}
                  </ThemedText>{' '}
                  {rows.length === 1 ? 'payment' : 'payments'} ready
                  {problems.length > 0 ? ` · ${problems.length} to fix` : ''}
                </ThemedText>

                <View style={styles.preview}>
                  {rows.slice(0, 3).map((row) => (
                    <View key={row.line} style={[styles.previewRow, { borderColor: theme.border }]}>
                      <ThemedText type="caption" themeColor="textMuted" style={styles.previewDate}>
                        {row.date}
                      </ThemedText>
                      <ThemedText type="caption" style={styles.previewSource} numberOfLines={1}>
                        {row.name || row.sourceName}
                      </ThemedText>
                      <ThemedText type="caption">{row.amount}</ThemedText>
                    </View>
                  ))}
                  {rows.length > 3 ? (
                    <ThemedText type="caption" themeColor="textMuted">
                      and {rows.length - 3} more
                    </ThemedText>
                  ) : null}
                </View>

                {problems.slice(0, PROBLEMS_SHOWN).map((problem) => (
                  <ThemedText key={problem.line} type="caption" style={styles.error}>
                    Line {problem.line}: {problem.message}
                  </ThemedText>
                ))}
                {problems.length > PROBLEMS_SHOWN ? (
                  <ThemedText type="caption" themeColor="textMuted">
                    and {problems.length - PROBLEMS_SHOWN} more problems
                  </ThemedText>
                ) : null}

                <ThemedText type="caption" themeColor="textMuted">
                  Importing runs on the server, and that endpoint is not built yet — these rows are
                  what will be sent. Nothing has been saved.
                </ThemedText>
              </>
            )}

            <View style={styles.actions}>
              <Button
                label={picking ? 'Opening…' : 'Choose another'}
                onPress={chooseFile}
                disabled={picking}
                secondary
                onCard
                style={styles.grow}
              />
              <Button label="Close" onPress={close} style={styles.grow} />
            </View>
          </>
        )}

        {picking ? <ActivityIndicator /> : null}
      </View>
    </Popup>
  );
}

const styles = StyleSheet.create({
  body: { gap: Spacing.three },
  spec: {
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.one,
    gap: Spacing.one,
  },
  specRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  specName: { width: 84 },
  specFormat: { flex: 1 },
  example: { borderWidth: 1, borderRadius: Radius.field, padding: Spacing.two },
  exampleText: { fontSize: 11, lineHeight: 16 },
  actions: { flexDirection: 'row', gap: Spacing.two },
  grow: { flex: 1 },
  fileRow: { flexDirection: 'row', alignItems: 'center', gap: Spacing.two },
  fileName: { flex: 1 },
  notice: {
    borderWidth: 1,
    borderRadius: Radius.field,
    padding: Spacing.two,
    gap: Spacing.one,
  },
  preview: { gap: Spacing.one },
  previewRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderBottomWidth: StyleSheet.hairlineWidth,
    paddingVertical: Spacing.one,
  },
  previewDate: { width: 76 },
  previewSource: { flex: 1 },
  error: { color: Status.danger },
  strong: { fontWeight: '700' },
});
