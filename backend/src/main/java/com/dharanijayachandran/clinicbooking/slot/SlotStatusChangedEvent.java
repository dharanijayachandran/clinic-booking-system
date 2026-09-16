package com.dharanijayachandran.clinicbooking.slot;

import java.util.UUID;

/**
 * Raised when a slot's availability changes. Published inside the booking
 * transaction but delivered only after it commits — see SlotBroadcaster.
 */
public record SlotStatusChangedEvent(UUID slotId, UUID doctorId, SlotStatus status) {
}
