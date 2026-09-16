import { CalendarSlot } from '../../core/models/booking.model';

export interface CalendarDay {
  date: Date;
  /** Local YYYY-MM-DD, used as the grouping key and trackBy id. */
  key: string;
  inCurrentMonth: boolean;
  isToday: boolean;
  slots: CalendarSlot[];
}

export function localDayKey(date: Date): string {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function startOfDay(date: Date): Date {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
}

/** Weeks start Monday, matching how clinic schedules are usually read. */
export function startOfWeek(date: Date): Date {
  const copy = startOfDay(date);
  const mondayOffset = (copy.getDay() + 6) % 7;
  copy.setDate(copy.getDate() - mondayOffset);
  return copy;
}

export function addDays(date: Date, days: number): Date {
  const copy = new Date(date);
  copy.setDate(copy.getDate() + days);
  return copy;
}

export function startOfMonth(date: Date): Date {
  const copy = startOfDay(date);
  copy.setDate(1);
  return copy;
}

/**
 * The visible range for a month view is the whole weeks that contain it, so
 * the grid is always rectangular and the first row doesn't start mid-week.
 */
export function monthGridRange(month: Date): { from: Date; to: Date } {
  const from = startOfWeek(startOfMonth(month));
  const lastOfMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0);
  const to = addDays(startOfWeek(lastOfMonth), 7);
  return { from, to };
}

export function buildDays(from: Date, to: Date, slots: CalendarSlot[], referenceMonth: Date): CalendarDay[] {
  const byDay = new Map<string, CalendarSlot[]>();
  for (const slot of slots) {
    const key = localDayKey(new Date(slot.startsAt));
    const bucket = byDay.get(key);
    if (bucket) {
      bucket.push(slot);
    } else {
      byDay.set(key, [slot]);
    }
  }

  const todayKey = localDayKey(new Date());
  const days: CalendarDay[] = [];

  for (let cursor = new Date(from); cursor < to; cursor = addDays(cursor, 1)) {
    const key = localDayKey(cursor);
    days.push({
      date: new Date(cursor),
      key,
      inCurrentMonth: cursor.getMonth() === referenceMonth.getMonth(),
      isToday: key === todayKey,
      slots: (byDay.get(key) ?? []).sort(
        (a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime(),
      ),
    });
  }

  return days;
}
