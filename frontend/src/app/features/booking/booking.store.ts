import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

import { ClinicApiService } from '../../core/api/clinic-api.service';
import { CalendarSlot, Doctor } from '../../core/models/booking.model';

export type BookingOutcome =
  | { kind: 'booked' }
  | { kind: 'taken' }
  | { kind: 'failed' };

/**
 * Calendar + booking state, all in signals.
 *
 * The optimistic update is the interesting part. On click the slot is marked
 * `pending` immediately, so the UI responds now rather than after a round
 * trip. What happens next depends on *why* the server said no:
 *
 * - 409 means someone else won the race for that slot. Rolling back to
 *   AVAILABLE would be wrong — the slot genuinely isn't available any more —
 *   so it's marked `takenByOther`, which the calendar renders as struck
 *   through with an explanation. "Undo the optimistic change" and "restore
 *   the previous state" are not the same thing here.
 * - Anything else (network, 500) means we don't know the server's state, so
 *   the slot is restored to AVAILABLE and the user can retry.
 */
@Injectable({ providedIn: 'root' })
export class BookingStore {
  private readonly api = inject(ClinicApiService);

  private readonly doctorsSignal = signal<Doctor[]>([]);
  private readonly slotsSignal = signal<CalendarSlot[]>([]);
  private readonly selectedDoctorSignal = signal<Doctor | null>(null);
  private readonly loadingSignal = signal(false);
  private readonly errorSignal = signal<string | null>(null);

  readonly doctors = this.doctorsSignal.asReadonly();
  readonly slots = this.slotsSignal.asReadonly();
  readonly selectedDoctor = this.selectedDoctorSignal.asReadonly();
  readonly loading = this.loadingSignal.asReadonly();
  readonly error = this.errorSignal.asReadonly();

  readonly bookableCount = computed(
    () => this.slotsSignal().filter((slot) => slot.status === 'AVAILABLE' && !slot.takenByOther).length,
  );

  loadDoctors(): void {
    this.api.doctors().subscribe({
      next: (doctors) => {
        this.doctorsSignal.set(doctors);
        if (!this.selectedDoctorSignal() && doctors.length > 0) {
          this.selectDoctor(doctors[0]);
        }
      },
      error: () => this.errorSignal.set('Could not load doctors.'),
    });
  }

  selectDoctor(doctor: Doctor): void {
    this.selectedDoctorSignal.set(doctor);
  }

  loadSlots(from: Date, to: Date): void {
    const doctor = this.selectedDoctorSignal();
    if (!doctor) {
      this.slotsSignal.set([]);
      return;
    }

    this.loadingSignal.set(true);
    this.errorSignal.set(null);

    this.api.availableSlots(doctor.id, from, to).subscribe({
      next: (slots) => {
        this.slotsSignal.set(slots);
        this.loadingSignal.set(false);
      },
      error: () => {
        this.errorSignal.set('Could not load availability.');
        this.loadingSignal.set(false);
      },
    });
  }

  /** Optimistically books, resolving to what actually happened. */
  book(slotId: string, onOutcome: (outcome: BookingOutcome) => void): void {
    this.patch(slotId, { pending: true });

    this.api.book(slotId).subscribe({
      next: () => {
        // Confirmed: the slot leaves the available set for good.
        this.patch(slotId, { pending: false, status: 'BOOKED' });
        onOutcome({ kind: 'booked' });
      },
      error: (response: HttpErrorResponse) => {
        if (response.status === 409) {
          this.patch(slotId, { pending: false, status: 'BOOKED', takenByOther: true });
          onOutcome({ kind: 'taken' });
          return;
        }

        // Unknown server state: put it back and let them try again.
        this.patch(slotId, { pending: false, status: 'AVAILABLE' });
        onOutcome({ kind: 'failed' });
      },
    });
  }

  /** Applied when a WebSocket event arrives in Phase 6. */
  markTakenExternally(slotId: string): void {
    this.patch(slotId, { status: 'BOOKED' });
  }

  private patch(slotId: string, changes: Partial<CalendarSlot>): void {
    this.slotsSignal.update((slots) =>
      slots.map((slot) => (slot.id === slotId ? { ...slot, ...changes } : slot)),
    );
  }
}
