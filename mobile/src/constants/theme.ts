/**
 * The design system: every colour, spacing step, radius and text size the app uses.
 *
 * The palette is lifted from the FinFlow Figma file (see figmaInspiration/) so that a screen
 * built here and a screen built in Figma agree on the same values. Nothing in the UI should
 * hardcode a hex value - reach for a token instead, otherwise the next rebrand misses a spot.
 */

import '@/global.css';

import { Platform } from 'react-native';

/**
 * Brand colours, identical in light and dark mode.
 *
 * `navy` is the logo's dark half and carries headings and body text; `green` is the logo's
 * bright half and is reserved for the single most important action on a screen.
 */
export const Brand = {
  navy: '#02284C',
  green: '#01916D',
} as const;

/** Semantic colours for validation and status messaging. */
export const Status = {
  success: '#1F9D55',
  danger: '#D93025',
  warning: '#B45309',
} as const;

/**
 * Per-scheme surface and text colours.
 *
 * The keys are what `useTheme()` returns and what `ThemedText`/`ThemedView` accept as `type`,
 * so the list is deliberately small: add a key only when a component genuinely needs a
 * different surface, not for every shade in the mock-up.
 */
export const Colors = {
  light: {
    /** Primary reading colour. */
    text: '#02284C',
    /** Supporting copy: captions, hints, placeholder text. */
    textSecondary: '#335372',
    /** Placeholder text inside a field, one step lighter than textSecondary. */
    textMuted: '#94A3B8',
    /** Page background, the pale blue wash behind the cards. */
    background: '#F5FAFF',
    /** A card or sheet sitting on the page background. */
    backgroundElement: '#FFFFFF',
    /** Chips, secondary buttons and anything that must read as "pressed". */
    backgroundSelected: '#E2E8F0',
    /** Hairline borders around fields and cards. */
    border: '#E2E8F0',
    /** Border used while a field has focus. */
    borderFocused: '#01916D',
    /** Strong text on a coloured surface (button labels on navy or green). */
    onBrand: '#FFFFFF',
    /** Tinted container behind the password checklist. */
    surfaceSubtle: 'rgba(245, 250, 255, 0.8)',
    /** Tinted fill inside a text input, per the Figma component. */
    inputBackground: 'rgba(245, 250, 255, 0.3)',
  },
  dark: {
    text: '#F1F5F9',
    textSecondary: '#B0B4BA',
    textMuted: '#64748B',
    background: '#0B1220',
    backgroundElement: '#111C2E',
    backgroundSelected: '#1E293B',
    border: '#1E293B',
    borderFocused: '#2BC49A',
    onBrand: '#FFFFFF',
    surfaceSubtle: 'rgba(17, 28, 46, 0.8)',
    inputBackground: 'rgba(30, 41, 59, 0.35)',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const Fonts = Platform.select({
  ios: {
    /** iOS `UIFontDescriptorSystemDesignDefault` */
    sans: 'system-ui',
    /** iOS `UIFontDescriptorSystemDesignSerif` */
    serif: 'ui-serif',
    /** iOS `UIFontDescriptorSystemDesignRounded` */
    rounded: 'ui-rounded',
    /** iOS `UIFontDescriptorSystemDesignMonospaced` */
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    serif: 'serif',
    rounded: 'normal',
    mono: 'monospace',
  },
  web: {
    sans: 'var(--font-display)',
    serif: 'var(--font-serif)',
    rounded: 'var(--font-rounded)',
    mono: 'var(--font-mono)',
  },
});

export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;

/** Corner radii straight from the Figma components (fields 12, cards 16, pills 9999). */
export const Radius = {
  small: 4,
  field: 12,
  card: 16,
  pill: 9999,
} as const;

/**
 * Type scale. Sizes are in points, and `lineHeight` is always explicit: without it Android and
 * iOS pick different defaults for the same font size, which shows up as jumpy spacing.
 */
export const Typography = {
  heading: { fontSize: 24, lineHeight: 32, fontWeight: '700', letterSpacing: -0.6 },
  title: { fontSize: 20, lineHeight: 28, fontWeight: '700' },
  subtitle: { fontSize: 16, lineHeight: 24, fontWeight: '600' },
  body: { fontSize: 16, lineHeight: 24, fontWeight: '400' },
  /** Labels above a field, and button labels. */
  label: { fontSize: 12, lineHeight: 16, fontWeight: '600' },
  caption: { fontSize: 12, lineHeight: 16, fontWeight: '400' },
  /** All-caps section headings such as "SECURITY REQUIREMENTS". */
  overline: { fontSize: 11, lineHeight: 16, fontWeight: '600', letterSpacing: 0.55 },
} as const;

/**
 * Shadow presets. `elevation` is the Android half of the same effect; without it Android
 * ignores the shadow properties entirely and the card looks flat next to the iOS build.
 */
export const Shadows = {
  card: {
    shadowColor: Brand.navy,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.06,
    shadowRadius: 20,
    elevation: 3,
  },
  button: {
    shadowColor: Brand.green,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.25,
    shadowRadius: 6,
    elevation: 2,
  },
  emblem: {
    shadowColor: '#000000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.1,
    shadowRadius: 6,
    elevation: 4,
  },
} as const;

export const BottomTabInset = Platform.select({ ios: 50, android: 80 }) ?? 0;
export const MaxContentWidth = 800;
