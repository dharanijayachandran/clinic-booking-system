package com.dharanijayachandran.clinicbooking.slot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes slot changes to subscribed clients.
 *
 * AFTER_COMMIT is the entire point of this class existing separately from
 * BookingService. Broadcasting from inside the booking transaction would be
 * wrong twice over:
 *
 * 1. It would hold the contended slot's row lock while doing network I/O,
 *    turning a lock measured in milliseconds into one measured in whatever
 *    the slowest subscriber costs.
 * 2. If the transaction then rolled back, every connected client would have
 *    been told a slot was taken when it is in fact still free — and nothing
 *    would ever correct them.
 *
 * Publishing per doctor rather than one global topic keeps a client browsing
 * one doctor's calendar from being woken by every booking in the clinic.
 */
@Component
public class SlotBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(SlotBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public SlotBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSlotStatusChanged(SlotStatusChangedEvent event) {
        String destination = "/topic/slots/" + event.doctorId();
        try {
            messagingTemplate.convertAndSend(destination, event);
        } catch (RuntimeException e) {
            // A failed broadcast must never fail the booking — it is already
            // committed and durable. The client simply finds out on its next
            // refresh instead of instantly.
            log.warn("Could not broadcast slot change to {}", destination, e);
        }
    }
}
