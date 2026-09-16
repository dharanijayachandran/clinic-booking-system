package com.dharanijayachandran.clinicbooking.doctor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeOffRepository extends JpaRepository<TimeOff, UUID> {

    /** Any time-off overlapping the window — standard overlap test. */
    @Query("""
            select t from TimeOff t
            where t.doctorId = :doctorId
              and t.startsAt < :windowEnd
              and t.endsAt > :windowStart
            """)
    List<TimeOff> findOverlapping(
            @Param("doctorId") UUID doctorId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}
