package com.dharanijayachandran.clinicbooking.booking;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    Optional<Appointment> findBySlotIdAndStatus(UUID slotId, AppointmentStatus status);

    List<Appointment> findByPatientIdOrderByCreatedAtDesc(UUID patientId);

    long countBySlotIdAndStatus(UUID slotId, AppointmentStatus status);
}
