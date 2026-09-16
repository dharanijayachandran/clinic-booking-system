package com.dharanijayachandran.clinicbooking.booking;

import java.util.UUID;

/** No such slot — maps to 404, as distinct from "exists but lost the race". */
public class SlotNotFoundException extends RuntimeException {

    public SlotNotFoundException(UUID slotId) {
        super("No such slot: " + slotId);
    }
}
