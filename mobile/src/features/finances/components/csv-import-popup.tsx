import * as DocumentPicker from 'expo-document-picker';
import { readAsStringAsync } from 'expo-file-system/legacy';
import { useState } from 'react';
import { ActivityIndicator, StyleSheet, View } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Icon } from '@/components/ui/icon';
import { Popup } from '@/components/ui/popup';
import { Button } from '@/components/ui/primitives';
import { Radius, Spacing, Status } from '@/constants/theme';
import { parseExpensesCsv, type CsvParseResult } from '@/features/finances/lib/csv';
import { useTheme } from '@/hooks/use-theme';

/** How many problems are listed before the rest are summarised as a count. */
const PROBLEMS_SHOWN = 4;

/**
 * Importing expenses from a CSV file.
 *
 * <p>Two steps, in the order the user needs them: what the file has to look like, then what was
 * found in the file they chose. The explanation comes first because a rejected file is only
 * frustrating when the rules were never stated.
 *
 * <p>Kept deliberately short: a pop-up that has to be scrolled before its own button can be reached
 * is a pop-up that does not work on a phone. The column spec is four compact lines and the example
 * is two, so the buttons sit on screen at both step one and step two.
 *
 * <p>Nothing is written anywhere yet: the rows are parsed and shown, and the import itself waits
 * for the endpoint. Saying so plainly is better than a button that appears to import and does not.
 */
export function CsvImportPopup({
  visible,
  onClose,
  categoryNames,
}: {
  visible: boolean;
  onClose: () => void;
  /** The account's own categories: a row naming anything else cannot be imported. */
  categoryNames: string[];
}) {
  const theme = useTheme();

  const [picking, setPicking] = useState(false);
  const [fileName, setFileName] = useState<string | null>(null);
  const [result, setResult] = useState<CsvParseResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const close = () => {
    setFileName(null);
    setResult(null);
    setError(null);
    onClose();
  };

  /** Opens the device's file browser, reads the file, and shows what it found. */
  const chooseFile = async () => {
    setPicking(true);
    setError(null);

    try {
      const picked = await DocumentPicker.getDocumentAsync({
        // Naming the CSV types opens the right folder; the wildcard keeps files with no MIME type
        // usable, since some file managers report none.
        type: ['text/csv', 'text/comma-separated-values', 'application/vnd.ms-excel', '*/*'],
        copyToCacheDirectory: true,
        multiple: false,
      });

      if (picked.canceled || !picked.assets || picked.assets.length === 0) return;

      const file = picked.assets[0];
      const text = await readAsStringAsync(file.uri, { encoding: 'utf8' });

      setFileName(file.name);
      setResult(parseExpensesCsv(text, categoryNames));
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
        <ThemedText type="hero">Import from a CSV file</ThemedText>

        {result === null ? (
          <>
            <ThemedText type="small" themeColor="textSecondary">
              The first line names the columns. Order does not matter, and extra columns are
              ignored.
            </ThemedText>

            <View style={[styles.spec, { borderColor: theme.border }]}>
              {[
                { name: 'date', format: '2026-11-02, not in the future' },
                { name: 'amount', format: '4500 or 4500.50' },
                { name: 'category', format: 'one of your categories' },
                { name: 'description', format: 'optional text' },
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
                {'date,amount,category\n2026-11-02,4500,Food & Groceries'}
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
                  {rows.length === 1 ? 'expense' : 'expenses'} ready
                  {problems.length > 0 ? ` · ${problems.length} to fix` : ''}
                </ThemedText>

                <View style={styles.preview}>
                  {rows.slice(0, 3).map((row) => (
                    <View key={row.line} style={[styles.previewRow, { borderColor: theme.border }]}>
                      <ThemedText type="caption" themeColor="textMuted" style={styles.previewDate}>
                        {row.date}
                      </ThemedText>
                      <ThemedText type="caption" style={styles.previewCategory} numberOfLines={1}>
                        {row.categoryName}
                      </ThemedText>
                      <ThemedText type="caption" style={styles.previewAmount}>
                        {row.amount}
                      </ThemedText>
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
                  Importing runs on the server. These rows are what will be sent once that endpoint
                  exists — nothing has been saved yet.
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
  body: {
    gap: Spacing.three,
  },
  /** The column spec, one compact row each - the same facts as a table, half the height. */
  spec: {
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.one,
    gap: Spacing.one,
  },
  specRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  specName: {
    width: 84,
  },
  specFormat: {
    flex: 1,
  },
  example: {
    borderWidth: 1,
    borderRadius: Radius.field,
    padding: Spacing.two,
  },
  exampleText: {
    fontSize: 11,
    lineHeight: 16,
  },
  actions: {
    flexDirection: 'row',
    gap: Spacing.two,
  },
  grow: {
    flex: 1,
  },
  fileRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  fileName: {
    flex: 1,
  },
  notice: {
    borderWidth: 1,
    borderRadius: Radius.field,
    padding: Spacing.two,
    gap: Spacing.one,
  },
  preview: {
    gap: Spacing.one,
  },
  previewRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderBottomWidth: StyleSheet.hairlineWidth,
    paddingVertical: Spacing.one,
  },
  previewDate: {
    width: 76,
  },
  previewCategory: {
    flex: 1,
  },
  previewAmount: {
    textAlign: 'right',
  },
  error: {
    color: Status.danger,
  },
  strong: {
    fontWeight: '700',
  },
});
