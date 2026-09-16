package com.dharanijayachandran.clinicbooking.booking.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Only the slot id. The patient is taken from the authenticated principal,
 * never from the request body — otherwise anyone could book on someone
 * else's behalf just by changing a field.
 */
public record BookingRequest(@NotNull UUID slotId) {
}
