import { CalendarSlot } from '../../core/models/booking.model';
import { buildDays, localDayKey, monthGridRange, startOfWeek } from './calendar.util';

describe('calendar utils', () => {
  it('starts weeks on Monday', () => {
    // 2026-10-01 is a Thursday.
    const monday = startOfWeek(new Date(2026, 9, 1));
    expect(monday.getDay()).toBe(1);
    expect(localDayKey(monday)).toBe('2026-09-28');
  });

  it('treats Sunday as the end of the week, not the start', () => {
    // 2026-10-04 is a Sunday; its week began the preceding Monday.
    const monday = startOfWeek(new Date(2026, 9, 4));
    expect(localDayKey(monday)).toBe('2026-09-28');
  });

  it('expands a month to whole weeks so the grid stays rectangular', () => {
    const { from, to } = monthGridRange(new Date(2026, 9, 15));

    expect(from.getDay()).toBe(1);
    const days = Math.round((to.getTime() - from.getTime()) / 86_400_000);
    expect(days % 7).toBe(0);
  });

  it('groups slots into their local day', () => {
    const slots: CalendarSlot[] = [
      slotAt('2026-10-01T09:00:00'),
      slotAt('2026-10-01T10:00:00'),
      slotAt('2026-10-02T09:00:00'),
    ];

    const days = buildDays(new Date(2026, 9, 1), new Date(2026, 9, 3), slots, new Date(2026, 9, 1));

    expect(days).toHaveLength(2);
    expect(days[0].slots).toHaveLength(2);
    expect(days[1].slots).toHaveLength(1);
  });

  it('orders slots within a day chronologically', () => {
    const slots: CalendarSlot[] = [
      slotAt('2026-10-01T15:00:00'),
      slotAt('2026-10-01T09:00:00'),
    ];

    const days = buildDays(new Date(2026, 9, 1), new Date(2026, 9, 2), slots, new Date(2026, 9, 1));

    expect(days[0].slots.map((s) => new Date(s.startsAt).getHours())).toEqual([9, 15]);
  });

  it('flags days outside the reference month so the grid can dim them', () => {
    const { from, to } = monthGridRange(new Date(2026, 9, 15));
    const days = buildDays(from, to, [], new Date(2026, 9, 15));

    expect(days.some((d) => !d.inCurrentMonth)).toBe(true);
    expect(days.filter((d) => d.inCurrentMonth)).toHaveLength(31);
  });

  function slotAt(local: string): CalendarSlot {
    const start = new Date(local);
    return {
      id: local,
      doctorId: 'doc-1',
      startsAt: start.toISOString(),
      endsAt: new Date(start.getTime() + 30 * 60_000).toISOString(),
      status: 'AVAILABLE',
    };
  }
});
