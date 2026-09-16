package com.dharanijayachandran.clinicbooking.doctor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 1:1 extension of a User with role = DOCTOR — the id *is* the user id, so
 * there's no separate doctor key to keep in sync.
 */
@Entity
@Table(name = "doctors")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Doctor {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String specialty;

    private String bio;

    /** Drives how working-hours blocks are sliced into bookable slots. */
    @Column(name = "slot_duration_minutes", nullable = false)
    private short slotDurationMinutes;
}
