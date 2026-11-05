import { createContext, useCallback, useContext, useMemo, useState } from 'react';

import type { Session } from '@/types/api';

type SessionContextValue = {
  /** The signed-in account's token and identity, or null when nobody is signed in. */
  session: Session | null;
  signIn: (session: Session) => void;
  signOut: () => void;
};

const SessionContext = createContext<SessionContextValue | null>(null);

/**
 * Holds the signed-in user's session for the whole app.
 *
 * Deliberately in-memory: the token is dropped when the app restarts, which is the safe default
 * while there is no secure storage wired up. To persist it later, add `expo-secure-store` and
 * read the token back in an effect here - nothing else has to change, because every screen asks
 * this provider rather than touching storage itself.
 */
export function SessionProvider({ children }: { children: React.ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);

  const signIn = useCallback((next: Session) => setSession(next), []);
  const signOut = useCallback(() => setSession(null), []);

  const value = useMemo<SessionContextValue>(
    () => ({ session, signIn, signOut }),
    [session, signIn, signOut],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

function useSessionContext(): SessionContextValue {
  const value = useContext(SessionContext);
  if (!value) {
    throw new Error('useSession must be used inside <SessionProvider> (see src/app/_layout.tsx).');
  }
  return value;
}

/** The whole value: `{ session, signIn, signOut }`. */
export function useSession() {
  return useSessionContext();
}

/** Convenience for the common case of only needing the current session. */
export function useCurrentSession(): Session | null {
  return useSessionContext().session;
}
