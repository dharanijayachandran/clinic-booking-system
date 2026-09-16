export type SlotStatus = 'AVAILABLE' | 'BOOKED' | 'BLOCKED';

export interface Doctor {
  id: string;
  name: string;
  specialty: string;
  bio: string | null;
  slotDurationMinutes: number;
}

export interface Slot {
  id: string;
  doctorId: string;
  startsAt: string;
  endsAt: string;
  status: SlotStatus;
}

export interface Appointment {
  id: string;
  slotId: string;
  patientId: string;
  status: 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
  createdAt: string;
  cancelledAt: string | null;
}

/**
 * What the calendar renders. `pending` is the optimistic state: the booking
 * has been shown to the user but the server hasn't confirmed it yet.
 * `takenByOther` is the outcome of losing the race — distinct from simply
 * being unavailable, because it's worth telling the user what happened.
 */
export interface CalendarSlot extends Slot {
  pending?: boolean;
  takenByOther?: boolean;
}
