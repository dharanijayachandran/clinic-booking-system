import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';

import { CalendarSlot, Doctor } from '../../core/models/booking.model';
import { BookingStore } from './booking.store';
import {
  addDays,
  buildDays,
  CalendarDay,
  monthGridRange,
  startOfDay,
  startOfMonth,
  startOfWeek,
} from './calendar.util';

type ViewMode = 'week' | 'month';

@Component({
  selector: 'app-booking-calendar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatFormFieldModule,
    MatSelectModule,
    MatIconModule,
    MatProgressBarModule,
  ],
  templateUrl: './booking-calendar.component.html',
  styleUrl: './booking-calendar.component.css',
})
export class BookingCalendarComponent implements OnInit {
  protected readonly store = inject(BookingStore);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly view = signal<ViewMode>('week');
  protected readonly anchor = signal(startOfDay(new Date()));

  /** The window currently displayed, derived from the view and the anchor. */
  private readonly range = computed(() => {
    const anchor = this.anchor();
    if (this.view() === 'week') {
      const from = startOfWeek(anchor);
      return { from, to: addDays(from, 7) };
    }
    return monthGridRange(anchor);
  });

  protected readonly days = computed<CalendarDay[]>(() => {
    const { from, to } = this.range();
    return buildDays(from, to, this.store.slots(), this.anchor());
  });

  protected readonly heading = computed(() => {
    const { from } = this.range();
    return this.view() === 'week' ? from : startOfMonth(this.anchor());
  });

  ngOnInit(): void {
    this.store.loadDoctors();
    this.refresh();
  }

  protected onDoctorChange(doctor: Doctor): void {
    this.store.selectDoctor(doctor);
    this.refresh();
  }

  /** The list is refetched, so selection has to match by id, not reference. */
  protected compareDoctors = (a: Doctor | null, b: Doctor | null): boolean => a?.id === b?.id;

  protected setView(view: ViewMode): void {
    this.view.set(view);
    this.refresh();
  }

  protected move(direction: -1 | 1): void {
    const anchor = this.anchor();
    if (this.view() === 'week') {
      this.anchor.set(addDays(anchor, direction * 7));
    } else {
      this.anchor.set(new Date(anchor.getFullYear(), anchor.getMonth() + direction, 1));
    }
    this.refresh();
  }

  protected today(): void {
    this.anchor.set(startOfDay(new Date()));
    this.refresh();
  }

  protected bookable(slot: CalendarSlot): boolean {
    return slot.status === 'AVAILABLE' && !slot.pending && !slot.takenByOther;
  }

  protected book(slot: CalendarSlot): void {
    if (!this.bookable(slot)) {
      return;
    }

    this.store.book(slot.id, (outcome) => {
      switch (outcome.kind) {
        case 'booked':
          this.snackBar.open('Appointment booked.', 'Dismiss', { duration: 4000 });
          break;
        case 'taken':
          // The optimistic booking is rolled *forward*, not back: the slot is
          // genuinely gone, so it stays unavailable and says why.
          this.snackBar.open('That slot was just taken by someone else.', 'Dismiss', {
            duration: 6000,
          });
          break;
        case 'failed':
          this.snackBar.open('Could not book that slot. Please try again.', 'Dismiss', {
            duration: 6000,
          });
          break;
      }
    });
  }

  protected trackDay = (_: number, day: CalendarDay) => day.key;
  protected trackSlot = (_: number, slot: CalendarSlot) => slot.id;

  private refresh(): void {
    const { from, to } = this.range();
    this.store.loadSlots(from, to);
  }
}
