import { LogicalDate } from '../../../core/time/logical-date';

/** Parses a YYYY-MM-DD logical date as a UTC calendar day, so time zones never shift it. */
function utcDay(date: LogicalDate): Date {
  const [y, m, d] = date.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

/** ISO-8601 week number (weeks start Monday; week 1 holds the year's first Thursday). */
export function isoWeek(date: LogicalDate): number {
  const d = utcDay(date);
  const day = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - day);
  const yearStart = Date.UTC(d.getUTCFullYear(), 0, 1);
  return Math.ceil(((d.getTime() - yearStart) / 86400000 + 1) / 7);
}

/** The Monday of the week holding `date`, as YYYY-MM-DD. */
export function mondayOf(date: LogicalDate): LogicalDate {
  const d = utcDay(date);
  d.setUTCDate(d.getUTCDate() - ((d.getUTCDay() || 7) - 1));
  return d.toISOString().slice(0, 10) as LogicalDate;
}
