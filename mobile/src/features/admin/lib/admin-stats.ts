import type { ErrorLogEntry } from '@/features/admin/lib/admin-api';
import { rangeStart, type AdminRange } from '@/features/admin/lib/admin-range';

/**
 * Pure helpers behind the admin dashboard.
 *
 * Everything here is a plain function over plain data with no React or React Native import, which
 * is what makes the dashboard's numbers testable without a running backend or a renderer.
 */

/** One column of a chart. Declared here so the chart stays a presentational component. */
export type ChartBar = {
  /** Label under the bar; empty when the label was thinned out to fit a phone. */
  label: string;
  value: number;
  /** Second series drawn next to `value`, when the chart compares two things. */
  compare?: number;
};

/** Short label for a bucket: hours for the day view, dates for the longer ones. */
function bucketLabel(date: Date, range: AdminRange): string {
  if (range.key === '24h') {
    return `${String(date.getHours()).padStart(2, '0')}h`;
  }
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
}

/**
 * The bucket edges for a range, oldest first.
 *
 * Computed from the range rather than from the data, so a quiet period shows as an empty column
 * instead of vanishing - otherwise three events spread over a week would look identical to three
 * in one minute.
 *
 * `bucketCount` is capped so a 30-day window at 3-day steps (10 columns) stays readable on a
 * phone, and so a caller cannot ask for a chart with a thousand columns.
 */
export function rangeBuckets(range: AdminRange, now: Date = new Date(), maxBuckets = 14): Date[] {
  const start = rangeStart(range, now).getTime();
  const span = now.getTime() - start;
  const natural = Math.max(1, Math.ceil(span / range.bucketMs));
  const count = Math.min(natural, maxBuckets);
  const step = span / count;

  const starts: Date[] = [];
  for (let index = 0; index < count; index += 1) {
    starts.push(new Date(start + index * step));
  }
  return starts;
}

/** The label for each column of a range, thinned so at most ~7 fit under a phone-wide chart. */
export function rangeLabels(starts: Date[], range: AdminRange): string[] {
  const stride = Math.ceil(starts.length / 7);
  return starts.map((date, index) => (index % stride === 0 ? bucketLabel(date, range) : ''));
}

/**
 * Counts timestamped items into a range's columns.
 *
 * One helper for every chart in the console: registrations per day and errors per day are the
 * same operation over different arrays, and sharing it keeps their column counts aligned.
 */
export function countIntoBuckets<T>(
  items: T[],
  at: (item: T) => string,
  range: AdminRange,
  now: Date = new Date(),
): number[] {
  const starts = rangeBuckets(range, now);
  const start = starts[0]?.getTime() ?? 0;
  const end = now.getTime();
  const step = starts.length > 1 ? starts[1].getTime() - start : end - start || 1;

  const counts = new Array(starts.length).fill(0) as number[];
  for (const item of items) {
    const time = new Date(at(item)).getTime();
    if (Number.isNaN(time) || time < start || time > end) continue;
    const index = Math.min(starts.length - 1, Math.floor((time - start) / step));
    counts[index] += 1;
  }
  return counts;
}

/** Joins labelled columns with one or two series into the chart's data shape. */
export function toChartBars(labels: string[], primary: number[], compare?: number[]): ChartBar[] {
  return labels.map((label, index) => ({
    label,
    value: primary[index] ?? 0,
    ...(compare ? { compare: compare[index] ?? 0 } : {}),
  }));
}

/**
 * Buckets error-log entries into chart columns for a range.
 *
 * Kept as its own function because the error log is the one dataset every admin screen charts.
 */
export function bucketErrors(
  entries: ErrorLogEntry[],
  range: AdminRange,
  now: Date = new Date(),
): ChartBar[] {
  const starts = rangeBuckets(range, now);
  const counts = countIntoBuckets(entries, (entry) => entry.occurredAt, range, now);
  return toChartBars(rangeLabels(starts, range), counts);
}

/** Groups a numeric value into a named bucket, for the incidents breakdown. */
export function countBy<T>(items: T[], key: (item: T) => string): Record<string, number> {
  const counts: Record<string, number> = {};
  for (const item of items) {
    const name = key(item);
    counts[name] = (counts[name] ?? 0) + 1;
  }
  return counts;
}

/**
 * The HTTP status family of an entry, which is what an administrator triages by.
 *
 * The error log only ever records 5xx (the backend logs unexpected failures), so in practice
 * everything lands in "Server error" - but deriving it keeps the breakdown honest if that
 * changes, rather than hardcoding a label that could become wrong.
 */
export function statusFamily(status: number): string {
  if (status >= 500) return 'Server error (5xx)';
  if (status >= 400) return 'Client error (4xx)';
  if (status >= 300) return 'Redirect (3xx)';
  return 'Other';
}

/** The shortest useful name for an exception: the class, without its package. */
export function shortExceptionName(exceptionType: string | null | undefined): string {
  if (!exceptionType) return 'Unknown';
  const parts = exceptionType.split('.');
  return parts[parts.length - 1] || exceptionType;
}

/** `2026-10-10T00:41:14Z` -> the reader's local date and time. */
export function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString();
}

/** `3m ago`, `2h ago`, `5d ago`. */
export function formatRelative(iso: string, now: Date = new Date()): string {
  const at = new Date(iso).getTime();
  if (Number.isNaN(at)) return iso;
  const seconds = Math.max(0, Math.round((now.getTime() - at) / 1000));
  if (seconds < 60) return `${seconds}s ago`;
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.round(hours / 24)}d ago`;
}

/** `0.75` -> `75%`, guarding against a missing or nonsensical value. */
export function toPercent(part: number, whole: number): number {
  if (!whole || whole <= 0) return 0;
  return Math.round((part / whole) * 100);
}

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

/** `189942140928` -> `177 GB`. */
export function formatBytes(bytes: number): string {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 10 || unit === 0 ? 0 : 1)} ${units[unit]}`;
}

/** `2675` -> `44m 35s`, `200000` -> `2d 7h`. */
export function formatUptime(totalSeconds: number): string {
  const days = Math.floor(totalSeconds / 86400);
  const hours = Math.floor((totalSeconds % 86400) / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (days > 0) return `${days}d ${hours}h`;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m ${totalSeconds % 60}s`;
}

/** `Amina Haddad` -> `AH`, for the avatar circles in the reference design. */
export function initialsOf(firstName: string, lastName: string, fallback = '?'): string {
  const letters = `${firstName.charAt(0)}${lastName.charAt(0)}`.trim();
  return letters ? letters.toUpperCase() : fallback.toUpperCase();
}
