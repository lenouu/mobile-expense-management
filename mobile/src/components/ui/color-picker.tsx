import { LinearGradient } from 'expo-linear-gradient';
import { useCallback, useEffect, useRef, useState } from 'react';
import { PanResponder, Pressable, StyleSheet, View, type LayoutChangeEvent } from 'react-native';

import { ThemedText } from '@/components/themed-text';
import { Popup } from '@/components/ui/popup';
import { TextField } from '@/components/ui/text-field';
import { Radius, Shadows, Spacing } from '@/constants/theme';
import { useTheme } from '@/hooks/use-theme';
import { hexToHsv, hsvToHex, withAlpha } from '@/utils/color';

const HEX = /^#[0-9a-fA-F]{6}$/;
const SQUARE_HEIGHT = 150;
const HUE_HEIGHT = 24;
const THUMB = 20;

/** The rainbow, as a tuple: the gradient needs at least two stops and a readonly tuple type. */
const HUE_STOPS = ['#FF0000', '#FFFF00', '#00FF00', '#00FFFF', '#0000FF', '#FF00FF', '#FF0000'] as const;

/**
 * A colour picker you drag through, collapsed into a dropdown.
 *
 * Closed, it is a single row - the colour, its code, a chevron - because a 150 pt gradient sitting
 * permanently in a form pushes the buttons off screen and turns every attempt to scroll the form
 * into an accidental colour change.
 *
 * Open, it is the picker: the square is saturation across and brightness down, the strip chooses
 * the hue, and the code fills itself in as you drag. Typing a code moves the square and strip.
 * `Done` folds it back up.
 */
export function ColorPicker({
  value,
  onChange,
}: {
  /** `#RRGGBB`. */
  value: string;
  onChange: (hex: string) => void;
}) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);

  const initial = hexToHsv(value) ?? { h: 158, s: 0.99, v: 0.57 };
  const [hue, setHue] = useState(initial.h);
  const [sat, setSat] = useState(initial.s);
  const [brightness, setBrightness] = useState(initial.v);
  const [text, setText] = useState(value);
  const [invalid, setInvalid] = useState(false);

  const squareSize = useRef({ width: 0, height: SQUARE_HEIGHT });
  const hueSize = useRef({ width: 0 });

  // Follow the value when it changes from outside (a different category opened in the form).
  useEffect(() => {
    const hsv = hexToHsv(value);
    if (!hsv) return;
    if (hsvToHex(hsv.h, hsv.s, hsv.v) === hsvToHex(hue, sat, brightness)) return;
    setHue(hsv.h);
    setSat(hsv.s);
    setBrightness(hsv.v);
    setText(value.toUpperCase());
    setInvalid(false);
    // Deliberately reacts only to `value`: hue/sat/brightness are updated by dragging.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value]);

  // The pan responders are created once, so they must not close over a render's values: a drag
  // after changing the hue would otherwise still be mixing in the hue from the first render.
  const latest = useRef({ hue, sat, brightness, onChange });
  latest.current = { hue, sat, brightness, onChange };

  const emit = useCallback((h: number, s: number, v: number) => {
    const hex = hsvToHex(h, s, v);
    setText(hex);
    setInvalid(false);
    latest.current.onChange(hex);
  }, []);

  const squarePan = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (event) => applySquare(event.nativeEvent.locationX, event.nativeEvent.locationY),
      onPanResponderMove: (event) => applySquare(event.nativeEvent.locationX, event.nativeEvent.locationY),
    }),
  ).current;

  const huePan = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: (event) => applyHue(event.nativeEvent.locationX),
      onPanResponderMove: (event) => applyHue(event.nativeEvent.locationX),
    }),
  ).current;

  function applySquare(x: number, y: number) {
    const { width, height } = squareSize.current;
    if (width === 0 || height === 0) return;

    const nextSat = clamp01(x / width);
    const nextValue = 1 - clamp01(y / height);
    setSat(nextSat);
    setBrightness(nextValue);
    emit(latest.current.hue, nextSat, nextValue);
  }

  function applyHue(x: number) {
    const { width } = hueSize.current;
    if (width === 0) return;

    const nextHue = clamp01(x / width) * 360;
    setHue(nextHue);
    emit(nextHue, latest.current.sat, latest.current.brightness);
  }

  const onSquareLayout = (event: LayoutChangeEvent) => {
    const { width, height } = event.nativeEvent.layout;
    squareSize.current = { width, height };
  };

  const onHueLayout = (event: LayoutChangeEvent) => {
    hueSize.current = { width: event.nativeEvent.layout.width };
  };

  const onType = (next: string) => {
    setText(next);
    if (!HEX.test(next.trim())) {
      setInvalid(next.trim().length > 0);
      return;
    }

    const hsv = hexToHsv(next);
    if (!hsv) return;
    setInvalid(false);
    setHue(hsv.h);
    setSat(hsv.s);
    setBrightness(hsv.v);
    onChange(next.toUpperCase());
  };

  const hueColor = hsvToHex(hue, 1, 1);
  const selected = hsvToHex(hue, sat, brightness);

  return (
    <View style={styles.container}>
      {/* The row itself is inert: only the colour square opens the picker, which is what the
          screen asks for - and it keeps the hex code selectable as text. */}
      <View style={[styles.field, { borderColor: theme.border, backgroundColor: theme.background }]}>
        <Pressable
          onPress={() => setOpen(true)}
          accessibilityRole="button"
          accessibilityLabel={`Colour ${selected}, change it`}
          hitSlop={8}
          style={({ pressed }) => [
            styles.swatch,
            { backgroundColor: selected, borderColor: withAlpha(selected, 0.5) },
            pressed && styles.pressed,
          ]}
        />
        <ThemedText type="small" style={styles.fieldLabel}>
          {selected.toUpperCase()}
        </ThemedText>
        <ThemedText type="caption" themeColor="textMuted">
          Tap the colour
        </ThemedText>
      </View>

      <Popup visible={open} onClose={() => setOpen(false)}>
        <View style={styles.picker}>
          <ThemedText type="subtitle">Choose a colour</ThemedText>

          <View
            onLayout={onSquareLayout}
            style={[styles.square, { borderColor: theme.border }]}
            {...squarePan.panHandlers}>
            <View style={[StyleSheet.absoluteFill, { backgroundColor: hueColor }]} />
            <LinearGradient
              colors={['#FFFFFF', 'rgba(255,255,255,0)']}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 0 }}
              style={StyleSheet.absoluteFill}
            />
            <LinearGradient
              colors={['rgba(0,0,0,0)', '#000000']}
              start={{ x: 0, y: 0 }}
              end={{ x: 0, y: 1 }}
              style={StyleSheet.absoluteFill}
            />
            <View
              pointerEvents="none"
              style={[
                styles.thumb,
                Shadows.emblem,
                {
                  left: sat * squareSize.current.width - THUMB / 2,
                  top: (1 - brightness) * squareSize.current.height - THUMB / 2,
                  backgroundColor: selected,
                },
              ]}
            />
          </View>

          <View onLayout={onHueLayout} style={styles.hue} {...huePan.panHandlers}>
            <LinearGradient
              colors={HUE_STOPS}
              start={{ x: 0, y: 0 }}
              end={{ x: 1, y: 0 }}
              style={StyleSheet.absoluteFill}
            />
            <View
              pointerEvents="none"
              style={[
                styles.hueThumb,
                Shadows.emblem,
                { left: (hue / 360) * hueSize.current.width - HUE_HEIGHT / 2 },
              ]}
            />
          </View>

          <View style={styles.hexRow}>
            <View style={styles.hexField}>
              <TextField
                value={text}
                onChangeText={onType}
                autoCapitalize="characters"
                autoCorrect={false}
                maxLength={7}
                placeholder="#01916D"
                accessibilityLabel="Colour code"
              />
            </View>
            <Pressable
              onPress={() => setOpen(false)}
              accessibilityRole="button"
              accessibilityLabel="Done"
              style={({ pressed }) => [styles.done, pressed && styles.pressed]}>
              <ThemedText type="smallBold" style={{ color: '#FFFFFF' }}>
                Done
              </ThemedText>
            </Pressable>
          </View>

          {invalid ? (
            <ThemedText type="caption" style={styles.error}>
              A colour code is six hex digits, like #01916D
            </ThemedText>
          ) : (
            <ThemedText type="caption" themeColor="textMuted">
              Drag the square to pick - the code fills in for you.
            </ThemedText>
          )}
        </View>
      </Popup>
    </View>
  );
}

function clamp01(value: number): number {
  return Math.min(1, Math.max(0, value));
}

const styles = StyleSheet.create({
  container: {
    gap: Spacing.two,
  },
  field: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    borderWidth: 1,
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.two,
    paddingVertical: Spacing.two,
  },
  swatch: {
    width: 36,
    height: 36,
    borderRadius: Radius.field,
    borderWidth: 1,
  },
  fieldLabel: {
    flex: 1,
  },
  picker: {
    gap: Spacing.two,
  },
  square: {
    height: SQUARE_HEIGHT,
    borderRadius: Radius.field,
    borderWidth: 1,
    overflow: 'hidden',
    position: 'relative',
  },
  thumb: {
    position: 'absolute',
    width: THUMB,
    height: THUMB,
    borderRadius: THUMB / 2,
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
  hue: {
    height: HUE_HEIGHT,
    borderRadius: HUE_HEIGHT / 2,
    overflow: 'hidden',
    position: 'relative',
  },
  hueThumb: {
    position: 'absolute',
    top: 0,
    width: HUE_HEIGHT,
    height: HUE_HEIGHT,
    borderRadius: HUE_HEIGHT / 2,
    borderWidth: 2,
    borderColor: '#FFFFFF',
  },
  hexRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
  },
  hexField: {
    flex: 1,
  },
  done: {
    backgroundColor: '#01916D',
    borderRadius: Radius.field,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two + Spacing.one,
  },
  error: {
    color: '#D93025',
  },
  pressed: {
    opacity: 0.7,
  },
});
