import type { AccountState } from '@/types/api';

/** The time windows the dashboard offers, mirroring the reference design's range selector. */
export type AdminRangeKey = '24h' | '7d' | '30d';

export type AdminRange = {
  key: AdminRangeKey;
  /** Button text. */
  label: string;
  hours: number;
  /** How wide one chart column is. */
  bucketMs: number;
};

const HOUR = 3600 * 1000;
const DAY = 24 * HOUR;

export const ADMIN_RANGES: AdminRange[] = [
  // 24 hourly columns is too many for a phone, so this window is drawn in 3-hour steps.
  { key: '24h', label: '24 hours', hours: 24, bucketMs: 3 * HOUR },
  { key: '7d', label: '7 days', hours: 24 * 7, bucketMs: DAY },
  { key: '30d', label: '30 days', hours: 24 * 30, bucketMs: 3 * DAY },
];

export function rangeByKey(key: AdminRangeKey): AdminRange {
  return ADMIN_RANGES.find((range) => range.key === key) ?? ADMIN_RANGES[1];
}

/** The instant a range starts at, relative to `now`. */
export function rangeStart(range: AdminRange, now: Date = new Date()): Date {
  return new Date(now.getTime() - range.hours * HOUR);
}

/**
 * The account lifecycle states the backend accepts, in the order the UI shows them.
 *
 * `AccountState` is a closed union, so this list can be checked exhaustively at compile time -
 * adding a state to the backend type without adding it here is a type error.
 */
export const ADULT_ACCOUNT_STATES: AccountState[] = ['ACTIVE', 'PENDING', 'SUSPENDED', 'DELETED'];
