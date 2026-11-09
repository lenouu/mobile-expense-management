import { createContext, useContext, useMemo } from 'react';

/**
 * Navigation shared by the console screens, without threading a callback through every one.
 *
 * The profile is a *view inside* the console rather than a route of its own. It is opened from the
 * header, which every console screen renders, and it must work from whichever tab you are on - so
 * the console holds the state and the header asks for it.
 */
type ConsoleNav = { openProfile: () => void };

const ConsoleNavContext = createContext<ConsoleNav | null>(null);

export function ConsoleNavProvider({
  openProfile,
  children,
}: ConsoleNav & { children: React.ReactNode }) {
  const value = useMemo<ConsoleNav>(() => ({ openProfile }), [openProfile]);
  return <ConsoleNavContext.Provider value={value}>{children}</ConsoleNavContext.Provider>;
}

/** Null outside the console, which lets the header stay usable in any other context. */
export function useConsoleNav(): ConsoleNav | null {
  return useContext(ConsoleNavContext);
}
