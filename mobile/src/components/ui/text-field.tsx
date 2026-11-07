import { forwardRef, useState } from 'react';
import { Pressable, StyleSheet, TextInput, View, type TextInputProps } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Radius, Spacing, Status } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';

export type TextFieldProps = TextInputProps & {
  label?: string;
  /** Message shown under the field; tints the border red when present. */
  error?: string;
  /** Renders a show/hide control, so the caller never has to add one. */
  revealable?: boolean;
  /** Hint shown under the field when there is no error. */
  hint?: string;
};

/**
 * A labelled text input, matching the Figma "Input" component: 44pt tall, 12pt radius,
 * a hairline border that turns brand-green on focus.
 *
 * The label and error live inside this component rather than in the screen, so every field in
 * the app is announced and spaced identically.
 */
export const TextField = forwardRef<TextInput, TextFieldProps>(function TextField(
  { label, error, revealable, hint, style, onFocus, onBlur, secureTextEntry, ...rest },
  ref,
) {
  const theme = useTheme();
  const [focused, setFocused] = useState(false);
  const [revealed, setRevealed] = useState(false);

  const borderColor = error ? Status.danger : focused ? theme.borderFocused : theme.border;
  const hidden = revealable ? secureTextEntry && !revealed : secureTextEntry;

  return (
    <View style={styles.field}>
      {label ? <ThemedText type="label">{label}</ThemedText> : null}

      <View style={styles.inputRow}>
        <TextInput
          ref={ref}
          placeholderTextColor={theme.textMuted}
          secureTextEntry={hidden}
          onFocus={(event) => {
            setFocused(true);
            onFocus?.(event);
          }}
          onBlur={(event) => {
            setFocused(false);
            onBlur?.(event);
          }}
          style={[
            styles.input,
            {
              color: theme.text,
              backgroundColor: theme.inputBackground,
              borderColor,
              // Room for the reveal button so long values never slide under it.
              paddingRight: revealable ? 48 : Spacing.three - 2,
            },
            style,
          ]}
          {...rest}
        />

        {revealable ? (
          <Pressable
            onPress={() => setRevealed((value) => !value)}
            accessibilityRole="button"
            accessibilityLabel={revealed ? 'Hide password' : 'Show password'}
            hitSlop={Spacing.two}
            style={styles.reveal}>
            <ThemedText type="caption" themeColor="textSecondary">
              {revealed ? 'Hide' : 'Show'}
            </ThemedText>
          </Pressable>
        ) : null}
      </View>

      {error ? (
        <ThemedText type="caption" style={styles.error}>
          {error}
        </ThemedText>
      ) : hint ? (
        <ThemedText type="caption" themeColor="textMuted">
          {hint}
        </ThemedText>
      ) : null}
    </View>
  );
});

const styles = StyleSheet.create({
  field: {
    gap: Spacing.one + 2,
  },
  inputRow: {
    justifyContent: 'center',
  },
  input: {
    minHeight: 44,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.three - 2,
    paddingVertical: Spacing.two + Spacing.half,
    fontSize: 16,
    lineHeight: 20,
  },
  reveal: {
    position: 'absolute',
    right: Spacing.three - 2,
    paddingVertical: Spacing.one,
  },
  error: {
    color: Status.danger,
  },
});
