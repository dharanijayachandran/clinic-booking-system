import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';

import { SlotStatus } from '../models/booking.model';

export interface SlotStatusChanged {
  slotId: string;
  doctorId: string;
  status: SlotStatus;
}

/**
 * Live slot availability over STOMP.
 *
 * Subscribes per doctor (/topic/slots/{doctorId}) rather than to one global
 * topic, so browsing one doctor's calendar doesn't wake the client for every
 * booking in the clinic.
 *
 * The socket is a genuine long-lived subscription — unlike HttpClient, it
 * does not complete on its own — so it is deactivated on DestroyRef and the
 * previous topic is unsubscribed whenever the doctor changes. This is the one
 * place in the app where forgetting cleanup would actually leak.
 */
@Injectable({ providedIn: 'root' })
export class SlotEventsService {
  private readonly destroyRef = inject(DestroyRef);

  private client: Client | null = null;
  private subscription: StompSubscription | null = null;
  private currentDoctorId: string | null = null;
  private handler: ((event: SlotStatusChanged) => void) | null = null;

  /** Exposed so the UI can be honest about whether updates are actually live. */
  readonly connected = signal(false);

  constructor() {
    this.destroyRef.onDestroy(() => this.disconnect());
  }

  connect(): void {
    if (this.client) {
      return;
    }

    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      // Auth rides on the cookies sent with the handshake; nothing to attach.
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected.set(true);
        // Re-subscribe after a reconnect, otherwise the calendar would go
        // quietly stale while appearing connected.
        if (this.currentDoctorId) {
          this.subscribeToDoctor(this.currentDoctorId);
        }
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false),
    });

    client.activate();
    this.client = client;
  }

  /** Registers the callback invoked for every incoming change. */
  onSlotChanged(handler: (event: SlotStatusChanged) => void): void {
    this.handler = handler;
  }

  watchDoctor(doctorId: string): void {
    this.currentDoctorId = doctorId;
    this.connect();

    if (this.client?.connected) {
      this.subscribeToDoctor(doctorId);
    }
  }

  private subscribeToDoctor(doctorId: string): void {
    this.subscription?.unsubscribe();
    this.subscription =
      this.client?.subscribe(`/topic/slots/${doctorId}`, (message) => {
        try {
          this.handler?.(JSON.parse(message.body) as SlotStatusChanged);
        } catch {
          // A malformed frame shouldn't take the calendar down; the next
          // refresh reconciles against the API anyway.
        }
      }) ?? null;
  }

  private disconnect(): void {
    this.subscription?.unsubscribe();
    this.subscription = null;
    void this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
  }
}
