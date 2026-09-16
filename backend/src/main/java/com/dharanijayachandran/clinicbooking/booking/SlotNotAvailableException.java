package com.dharanijayachandran.clinicbooking.booking;

import java.util.UUID;

/**
 * The slot was taken (or blocked) by the time this booking got the row lock.
 * Maps to 409 Conflict — the request was well-formed, it just lost the race.
 */
public class SlotNotAvailableException extends RuntimeException {

    public SlotNotAvailableException(UUID slotId) {
        super("That slot is no longer available: " + slotId);
    }
}
