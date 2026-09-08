package com.dharanijayachandran.clinicbooking.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * No `role` field, deliberately. Self-registration always creates a PATIENT —
 * see AuthService.register() and the README's "who can register as what"
 * write-up. DOCTOR/ADMIN accounts are provisioned out-of-band.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 100) String password,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName) {
}
