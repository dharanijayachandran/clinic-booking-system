import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Appointment, Doctor, Slot } from '../models/booking.model';

/** Thin typed wrapper over the real API. No mocks, no local fixtures. */
@Injectable({ providedIn: 'root' })
export class ClinicApiService {
  private readonly http = inject(HttpClient);

  doctors(): Observable<Doctor[]> {
    return this.http.get<Doctor[]>('/api/doctors');
  }

  availableSlots(doctorId: string, from: Date, to: Date): Observable<Slot[]> {
    const params = new HttpParams()
      .set('doctorId', doctorId)
      .set('from', from.toISOString())
      .set('to', to.toISOString());

    return this.http.get<Slot[]>('/api/slots', { params });
  }

  book(slotId: string): Observable<Appointment> {
    return this.http.post<Appointment>('/api/bookings', { slotId });
  }

  myAppointments(): Observable<Appointment[]> {
    return this.http.get<Appointment[]>('/api/bookings/mine');
  }
}
