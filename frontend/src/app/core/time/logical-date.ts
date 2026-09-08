/**
 * Logical dates.
 *
 * A logical date is a plain `YYYY-MM-DD` string in the *user's* time zone. The server
 * decides what "today" is (spec §4.2); the frontend never does. That is why there is no
 * `today()` function here — `new Date()` for a logical date is banned, because it would
 * silently answer in the browser's zone rather than the user's.
 *
 * These helpers only shift and format a date the server already provided. Arithmetic
 * runs in UTC so a daylight-saving boundary in the local zone cannot skip or repeat a
 * day.
 */

export type LogicalDate = string; // 'YYYY-MM-DD'

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

export function isLogicalDate(value: string): value is LogicalDate {
  return ISO_DATE.test(value);
}

export function plusDays(date: LogicalDate, days: number): LogicalDate {
  const [year, month, day] = date.split('-').map(Number);
  const shifted = new Date(Date.UTC(year, month - 1, day + days));
  return toLogicalDate(shifted);
}

export function compare(a: LogicalDate, b: LogicalDate): number {
  // ISO dates sort lexicographically, which is the whole reason for this format.
  return a < b ? -1 : a > b ? 1 : 0;
}

/** Formats for display in the user's locale. Presentation only. */
export function formatLong(date: LogicalDate, locale = 'en-GB'): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    timeZone: 'UTC',
  }).format(new Date(Date.UTC(year, month - 1, day)));
}

function toLogicalDate(instant: Date): LogicalDate {
  const year = instant.getUTCFullYear().toString().padStart(4, '0');
  const month = (instant.getUTCMonth() + 1).toString().padStart(2, '0');
  const day = instant.getUTCDate().toString().padStart(2, '0');
  return `${year}-${month}-${day}`;
}
