package com.dharanijayachandran.clinicbooking.user;

/** Mirrors the CHECK constraint on users.role in V1__init_schema.sql. */
public enum Role {
    PATIENT,
    DOCTOR,
    ADMIN
}
