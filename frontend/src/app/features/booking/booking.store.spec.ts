import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Slot } from '../../core/models/booking.model';
import { BookingOutcome, BookingStore } from './booking.store';

describe('BookingStore', () => {
  let store: BookingStore;
  let http: HttpTestingController;

  const doctor = {
    id: 'doc-1',
    name: 'Dr Ana Silva',
    specialty: 'General',
    bio: null,
    slotDurationMinutes: 30,
  };

  const slot: Slot = {
    id: 'slot-1',
    doctorId: 'doc-1',
    startsAt: '2026-10-01T09:00:00Z',
    endsAt: '2026-10-01T09:30:00Z',
    status: 'AVAILABLE',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(BookingStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function loadOneSlot(): void {
    store.selectDoctor(doctor);
    store.loadSlots(new Date('2026-10-01T00:00:00Z'), new Date('2026-10-02T00:00:00Z'));
    http.expectOne((r) => r.url === '/api/slots').flush([slot]);
  }

  it('loads doctors and selects the first automatically', () => {
    store.loadDoctors();
    http.expectOne('/api/doctors').flush([doctor]);

    expect(store.doctors()).toHaveLength(1);
    expect(store.selectedDoctor()).toEqual(doctor);
  });

  it('marks the slot pending immediately, before the server replies', () => {
    loadOneSlot();

    store.book('slot-1', () => {});

    // Not yet flushed — this is the optimistic state the user sees at once.
    expect(store.slots()[0].pending).toBe(true);

    http.expectOne('/api/bookings').flush({ id: 'a-1' });
  });

  it('confirms the booking on success', () => {
    loadOneSlot();
    const outcomes: BookingOutcome[] = [];

    store.book('slot-1', (o) => outcomes.push(o));
    http.expectOne('/api/bookings').flush({ id: 'a-1' });

    expect(outcomes).toEqual([{ kind: 'booked' }]);
    expect(store.slots()[0].pending).toBe(false);
    expect(store.slots()[0].status).toBe('BOOKED');
    expect(store.bookableCount()).toBe(0);
  });

  it('on 409 keeps the slot unavailable rather than restoring it', () => {
    loadOneSlot();
    const outcomes: BookingOutcome[] = [];

    store.book('slot-1', (o) => outcomes.push(o));
    http
      .expectOne('/api/bookings')
      .flush({ message: 'taken' }, { status: 409, statusText: 'Conflict' });

    expect(outcomes).toEqual([{ kind: 'taken' }]);

    // Someone else genuinely won the race, so rolling back to AVAILABLE would
    // be a lie. It stays booked, flagged so the UI can explain why.
    const updated = store.slots()[0];
    expect(updated.status).toBe('BOOKED');
    expect(updated.takenByOther).toBe(true);
    expect(updated.pending).toBe(false);
    expect(store.bookableCount()).toBe(0);
  });

  it('restores the slot when the failure leaves the server state unknown', () => {
    loadOneSlot();
    const outcomes: BookingOutcome[] = [];

    store.book('slot-1', (o) => outcomes.push(o));
    http
      .expectOne('/api/bookings')
      .flush({ message: 'boom' }, { status: 500, statusText: 'Server Error' });

    expect(outcomes).toEqual([{ kind: 'failed' }]);

    // We don't know whether it was booked, so let the user retry.
    const updated = store.slots()[0];
    expect(updated.status).toBe('AVAILABLE');
    expect(updated.takenByOther).toBeFalsy();
    expect(store.bookableCount()).toBe(1);
  });

  describe('live updates', () => {
    it('greys out a slot someone else booked', () => {
      loadOneSlot();

      store.applyExternalChange('slot-1', 'BOOKED');

      expect(store.slots()[0].status).toBe('BOOKED');
      expect(store.bookableCount()).toBe(0);
    });

    it('reopens a slot that was released', () => {
      loadOneSlot();
      store.applyExternalChange('slot-1', 'BOOKED');

      store.applyExternalChange('slot-1', 'AVAILABLE');

      expect(store.bookableCount()).toBe(1);
    });

    it('ignores events for slots not in the current view', () => {
      loadOneSlot();

      store.applyExternalChange('some-other-slot', 'BOOKED');

      expect(store.slots()).toHaveLength(1);
      expect(store.bookableCount()).toBe(1);
    });

    it('does not clobber a slot whose booking is still in flight', () => {
      loadOneSlot();

      store.book('slot-1', () => {});
      expect(store.slots()[0].pending).toBe(true);

      // The echo of our own booking (or a racing one) arrives mid-flight.
      // Our request's response is authoritative, so this must not win.
      store.applyExternalChange('slot-1', 'BOOKED');
      expect(store.slots()[0].pending).toBe(true);

      http.expectOne('/api/bookings').flush({ id: 'a-1' });

      expect(store.slots()[0].pending).toBe(false);
      expect(store.slots()[0].status).toBe('BOOKED');
      expect(store.slots()[0].takenByOther).toBeFalsy();
    });
  });
});
