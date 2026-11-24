import { LogicalDate } from '../../../core/time/logical-date';
import { isoWeek, mondayOf } from './run-week';

const d = (s: string) => s as LogicalDate;

describe('run week plan dates', () => {
  it('numbers weeks the ISO way', () => {
    expect(isoWeek(d('2026-01-01'))).toBe(1); // a Thursday: week 1
    expect(isoWeek(d('2026-11-23'))).toBe(48); // a Monday
    expect(isoWeek(d('2026-11-29'))).toBe(48); // that week's Sunday
    expect(isoWeek(d('2026-11-30'))).toBe(49);
    expect(isoWeek(d('2027-01-01'))).toBe(53); // a Friday still in 2026's last week
  });

  it('finds the Monday a week starts on', () => {
    expect(mondayOf(d('2026-11-24'))).toBe('2026-11-23');
    expect(mondayOf(d('2026-11-23'))).toBe('2026-11-23');
    expect(mondayOf(d('2026-11-29'))).toBe('2026-11-23');
  });
});
