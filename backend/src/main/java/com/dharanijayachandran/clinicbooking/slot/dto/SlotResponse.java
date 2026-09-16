package com.dharanijayachandran.clinicbooking.slot.dto;

import com.dharanijayachandran.clinicbooking.slot.Slot;
import java.time.Instant;
import java.util.UUID;

public record SlotResponse(UUID id, UUID doctorId, Instant startsAt, Instant endsAt, String status) {

    public static SlotResponse from(Slot slot) {
        return new SlotResponse(
                slot.getId(), slot.getDoctorId(), slot.getStartsAt(), slot.getEndsAt(), slot.getStatus().name());
    }
}
