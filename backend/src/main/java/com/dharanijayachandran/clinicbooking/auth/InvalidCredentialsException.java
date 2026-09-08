package com.dharanijayachandran.clinicbooking.auth;

/**
 * Deliberately the same message whether the email doesn't exist or the
 * password is wrong — never let a login error reveal which one it was,
 * that's an account-enumeration leak.
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
