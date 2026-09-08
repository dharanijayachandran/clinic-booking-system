package com.dharanijayachandran.clinicbooking.auth.dto;

import com.dharanijayachandran.clinicbooking.user.Role;
import com.dharanijayachandran.clinicbooking.user.User;
import java.util.UUID;

/**
 * What the client sees after register/login/me — the token itself never
 * appears in a response body, it's set as an httpOnly cookie the frontend
 * can't (and doesn't need to) read.
 */
public record UserResponse(UUID id, String email, String firstName, String lastName, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole());
    }
}
