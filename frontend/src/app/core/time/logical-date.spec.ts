import { compare, formatLong, isLogicalDate, plusDays } from './logical-date';

describe('logical date', () => {
  it('steps forward and back across a month boundary', () => {
    expect(plusDays('2026-03-31', 1)).toBe('2026-04-01');
    expect(plusDays('2026-04-01', -1)).toBe('2026-03-31');
  });

  it('steps across a year boundary', () => {
    expect(plusDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(plusDays('2027-01-01', -1)).toBe('2026-12-31');
  });

  it('handles a leap day', () => {
    expect(plusDays('2028-02-28', 1)).toBe('2028-02-29');
    expect(plusDays('2028-02-29', 1)).toBe('2028-03-01');
    expect(plusDays('2027-02-28', 1)).toBe('2027-03-01');
  });

  /**
   * The reason arithmetic runs in UTC. On 29 March 2026 Europe/London springs forward,
   * and a local-time implementation can produce a 23-hour day that skips or repeats a
   * date. A logical day is always exactly one day.
   */
  it('does not skip or repeat a day across a daylight-saving transition', () => {
    expect(plusDays('2026-03-28', 1)).toBe('2026-03-29');
    expect(plusDays('2026-03-29', 1)).toBe('2026-03-30');
    expect(plusDays('2026-10-24', 1)).toBe('2026-10-25');
    expect(plusDays('2026-10-25', 1)).toBe('2026-10-26');
  });

  it('walks a whole year and returns to the same date', () => {
    let date = '2026-01-01';
    for (let i = 0; i < 365; i++) {
      date = plusDays(date, 1);
    }
    expect(date).toBe('2027-01-01');
    for (let i = 0; i < 365; i++) {
      date = plusDays(date, -1);
    }
    expect(date).toBe('2026-01-01');
  });

  it('orders dates lexicographically', () => {
    expect(compare('2026-03-01', '2026-03-02')).toBe(-1);
    expect(compare('2026-03-02', '2026-03-01')).toBe(1);
    expect(compare('2026-03-01', '2026-03-01')).toBe(0);
    expect(['2026-10-01', '2026-02-01', '2026-05-01'].sort()).toEqual([
      '2026-02-01',
      '2026-05-01',
      '2026-10-01',
    ]);
  });

  it('recognises the ISO shape only', () => {
    expect(isLogicalDate('2026-03-12')).toBe(true);
    expect(isLogicalDate('12/03/2026')).toBe(false);
    expect(isLogicalDate('2026-3-12')).toBe(false);
    expect(isLogicalDate('')).toBe(false);
  });

  it('formats the date it was given, regardless of the machine time zone', () => {
    // Formatting is pinned to UTC, so a browser in UTC-11 still shows 1 March.
    expect(formatLong('2026-03-01')).toContain('1 Mar');
  });
});
