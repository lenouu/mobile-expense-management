import { useEffect, useState } from 'react';

/**
 * A tiny app-wide "something changed" signal.
 *
 * The screens each load their own slice (the transaction list, the overview, the budget)
 * and nothing ties those requests together. Rather than lifting all of that data into one
 * store, a mutation announces itself here and every screen that cares re-runs its own load.
 * That is what makes an expense added from the receipt preview show up in Overview,
 * Transactions and Budget without a manual refresh.
 */
type Listener = () => void;

let version = 0;
const listeners = new Set<Listener>();

/** Call after a create, edit or delete that another screen might be showing. */
export function notifyDataChanged(): void {
  version += 1;
  // Copied first: a listener may unsubscribe while this loop runs.
  for (const listener of [...listeners]) listener();
}

/**
 * The current version, re-rendering the caller whenever it changes.
 *
 * Put the returned number in a load effect's dependency list and the effect re-runs on
 * every announced change.
 */
export function useDataVersion(): number {
  const [value, setValue] = useState(version);

  useEffect(() => {
    const listener = () => setValue(version);
    listeners.add(listener);
    // A change may have landed between the first render and this subscription.
    listener();
    return () => {
      listeners.delete(listener);
    };
  }, []);

  return value;
}
