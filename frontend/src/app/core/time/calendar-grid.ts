/**
 * Pure calendar math on an already-server-provided date — presentation-only, the same
 * reasoning the heatmap's own original comment used: this never invents "today", it
 * only lays out the days of a month the caller already named.
 */
import { LogicalDate } from './logical-date';

export interface MonthGrid {
  label: string;
  year: number;
  month0: number;
  /** Each week is 7 cells, Monday first; null holes pad the first/last week. */
  weeks: (LogicalDate | null)[][];
}

export const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

/** The first day of the month `monthsBack` months before `date`'s month. */
export function monthsAgoStart(date: LogicalDate, monthsBack: number): LogicalDate {
  const [year, month] = date.split('-').map(Number);
  const total = year * 12 + (month - 1) - monthsBack;
  const y = Math.floor(total / 12);
  const m = total % 12;
  return `${y.toString().padStart(4, '0')}-${(m + 1).toString().padStart(2, '0')}-01`;
}

/** January 1st of `date`'s own year — the anchor a monthly recap steps by, a yearly one by twelve. */
export function yearStart(date: LogicalDate): LogicalDate {
  const [year] = date.split('-').map(Number);
  return `${year.toString().padStart(4, '0')}-01-01`;
}

/** `date`'s calendar year, `yearsBack` years earlier — the milestones page's own "prev year" step. */
export function yearsAgoStart(date: LogicalDate, yearsBack: number): LogicalDate {
  const [year] = date.split('-').map(Number);
  return `${(year - yearsBack).toString().padStart(4, '0')}-01-01`;
}

/** `monthCount` months ending at (and including) `today`'s month, oldest first. */
export function buildMonths(today: LogicalDate, monthCount = 12): MonthGrid[] {
  const [todayYear, todayMonth] = today.split('-').map(Number);
  const months: MonthGrid[] = [];

  for (let back = monthCount - 1; back >= 0; back--) {
    const total = todayYear * 12 + (todayMonth - 1) - back;
    const year = Math.floor(total / 12);
    const month = total % 12; // 0-indexed
    months.push(buildMonth(year, month));
  }
  return months;
}

export function buildMonth(year: number, month0: number): MonthGrid {
  const daysInMonth = new Date(Date.UTC(year, month0 + 1, 0)).getUTCDate();
  const firstWeekday = new Date(Date.UTC(year, month0, 1)).getUTCDay(); // 0=Sun..6=Sat
  const mondayFirstOffset = (firstWeekday + 6) % 7; // 0=Mon..6=Sun

  const dates: (LogicalDate | null)[] = new Array(mondayFirstOffset).fill(null);
  for (let d = 1; d <= daysInMonth; d++) {
    dates.push(`${year.toString().padStart(4, '0')}-${(month0 + 1).toString().padStart(2, '0')}-${d.toString().padStart(2, '0')}`);
  }
  while (dates.length % 7 !== 0) {
    dates.push(null);
  }

  const weeks: (LogicalDate | null)[][] = [];
  for (let i = 0; i < dates.length; i += 7) {
    weeks.push(dates.slice(i, i + 7));
  }

  return { label: `${MONTH_NAMES[month0]} ${year}`, year, month0, weeks };
}

export function formatCellDate(date: LogicalDate): string {
  const [year, month, day] = date.split('-').map(Number);
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', timeZone: 'UTC' }).format(
    new Date(Date.UTC(year, month - 1, day)),
  );
}
