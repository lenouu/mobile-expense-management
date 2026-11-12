import { useSyncExternalStore } from 'react';
import { useColorScheme as useSystemColorScheme } from 'react-native';

/**
 * The appearance the user chose, and the scheme the app should draw with.
 *
 * In memory on purpose, like the rest of the session: the settings screens are frontend-only until
 * the account can store preferences, and a preference that silently resets on restart is better
 * than one that pretends to persist and then disagrees with the server.
 */
export type ThemePreference = 'system' | 'light' | 'dark';

const THEME_LABELS: Record<ThemePreference, string> = {
  system: 'Follow the device',
  light: 'Light',
  dark: 'Dark',
};

let preference: ThemePreference = 'system';
const listeners = new Set<() => void>();

export function setThemePreference(next: ThemePreference): void {
  preference = next;
  listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function snapshot(): ThemePreference {
  return preference;
}

export function useThemePreference(): ThemePreference {
  return useSyncExternalStore(subscribe, snapshot, snapshot);
}

export function themeLabel(value: ThemePreference): string {
  return THEME_LABELS[value];
}

/**
 * 'light' or 'dark', resolved: the explicit choice when there is one, the device's otherwise.
 *
 * Every screen reaches this through `useColorScheme()`, so switching here re-themes the whole app
 * without a reload.
 */
export function useEffectiveColorScheme(): 'light' | 'dark' {
  const system = useSystemColorScheme();
  const chosen = useThemePreference();

  if (chosen === 'system') return system ?? 'light';
  return chosen;
}
