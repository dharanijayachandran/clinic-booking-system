package com.dharanijayachandran.clinicbooking.booking.dto;

import com.dharanijayachandran.clinicbooking.booking.Appointment;
import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
        UUID id, UUID slotId, UUID patientId, String status, Instant createdAt, Instant cancelledAt) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getSlotId(),
                appointment.getPatientId(),
                appointment.getStatus().name(),
                appointment.getCreatedAt(),
                appointment.getCancelledAt());
    }
}
