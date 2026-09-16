package com.dharanijayachandran.clinicbooking.doctor.dto;

import java.util.UUID;

public record DoctorResponse(UUID id, String name, String specialty, String bio, int slotDurationMinutes) {
}
