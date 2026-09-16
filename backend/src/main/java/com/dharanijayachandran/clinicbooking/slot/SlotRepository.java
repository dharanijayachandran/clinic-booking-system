package com.dharanijayachandran.clinicbooking.slot;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, UUID> {

    /**
     * SELECT ... FOR UPDATE on exactly one slot row.
     *
     * This is the heart of the double-booking guarantee. Two concurrent
     * bookings for the same slot both reach this line; Postgres hands the
     * lock to one of them and *blocks* the other until the winner's
     * transaction commits. The loser then reads the committed row — now
     * BOOKED — and is rejected with a definitive answer rather than a
     * "maybe, try again".
     *
     * Note the deliberate absence of a lock timeout: waiting a few
     * milliseconds for a definitive result beats failing fast with an
     * ambiguous one. See BookingService for the surrounding transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") UUID id);

    List<Slot> findByDoctorIdAndStatusAndStartsAtBetweenOrderByStartsAt(
            UUID doctorId, SlotStatus status, Instant from, Instant to);

    boolean existsByDoctorIdAndStartsAt(UUID doctorId, Instant startsAt);

    List<Slot> findByDoctorIdAndStartsAtBetween(UUID doctorId, Instant from, Instant to);
}
