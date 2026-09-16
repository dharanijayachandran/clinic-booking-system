package com.dharanijayachandran.clinicbooking.slot;

import com.dharanijayachandran.clinicbooking.doctor.Doctor;
import com.dharanijayachandran.clinicbooking.doctor.DoctorRepository;
import com.dharanijayachandran.clinicbooking.doctor.TimeOff;
import com.dharanijayachandran.clinicbooking.doctor.TimeOffRepository;
import com.dharanijayachandran.clinicbooking.doctor.WorkingHours;
import com.dharanijayachandran.clinicbooking.doctor.WorkingHoursRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a doctor's recurring working hours into concrete, bookable slot rows
 * for a date range, skipping anything covered by time off.
 *
 * Slots are materialised rather than computed per request for two reasons:
 * the booking race needs a real row to lock (see BookingService), and live
 * availability in Phase 6 becomes "broadcast that slot X changed status"
 * rather than "recompute and diff a derived view".
 *
 * Timezone: working hours are wall-clock times, so they're resolved against a
 * single configured clinic zone. Multi-timezone clinics would need a zone per
 * doctor — deliberately out of scope, not an oversight.
 */
@Service
public class SlotGenerationService {

    private final DoctorRepository doctorRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final TimeOffRepository timeOffRepository;
    private final SlotRepository slotRepository;
    private final ZoneId clinicZone;

    public SlotGenerationService(
            DoctorRepository doctorRepository,
            WorkingHoursRepository workingHoursRepository,
            TimeOffRepository timeOffRepository,
            SlotRepository slotRepository,
            @Value("${app.clinic.timezone:UTC}") String clinicZone) {
        this.doctorRepository = doctorRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.timeOffRepository = timeOffRepository;
        this.slotRepository = slotRepository;
        this.clinicZone = ZoneId.of(clinicZone);
    }

    /**
     * Generates missing slots for [from, toExclusive). Idempotent: existing
     * slots are left alone, so re-running never duplicates or resets a
     * booking. (The UNIQUE (doctor_id, starts_at) constraint backs this up if
     * two generation runs ever overlap.)
     *
     * @return how many new slots were created
     */
    @Transactional
    public int generateFor(UUID doctorId, LocalDate from, LocalDate toExclusive) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new IllegalArgumentException("No such doctor: " + doctorId));

        List<WorkingHours> workingHours = workingHoursRepository.findByDoctorId(doctorId);
        if (workingHours.isEmpty()) {
            return 0;
        }

        Instant windowStart = from.atStartOfDay(clinicZone).toInstant();
        Instant windowEnd = toExclusive.atStartOfDay(clinicZone).toInstant();
        List<TimeOff> timeOff = timeOffRepository.findOverlapping(doctorId, windowStart, windowEnd);

        Duration slotLength = Duration.ofMinutes(doctor.getSlotDurationMinutes());
        List<Slot> toCreate = new ArrayList<>();

        for (LocalDate date = from; date.isBefore(toExclusive); date = date.plusDays(1)) {
            // DayOfWeek is 1=Monday..7=Sunday; the schema uses 0=Sunday.
            int schemaDayOfWeek = date.getDayOfWeek().getValue() % 7;

            for (WorkingHours block : workingHours) {
                if (block.getDayOfWeek() != schemaDayOfWeek) {
                    continue;
                }

                Instant blockStart = date.atTime(block.getStartTime()).atZone(clinicZone).toInstant();
                Instant blockEnd = date.atTime(block.getEndTime()).atZone(clinicZone).toInstant();

                for (Instant start = blockStart;
                        !start.plus(slotLength).isAfter(blockEnd);
                        start = start.plus(slotLength)) {

                    Instant end = start.plus(slotLength);
                    if (overlapsTimeOff(timeOff, start, end)
                            || slotRepository.existsByDoctorIdAndStartsAt(doctorId, start)) {
                        continue;
                    }

                    toCreate.add(Slot.builder()
                            .doctorId(doctorId)
                            .startsAt(start)
                            .endsAt(end)
                            .status(SlotStatus.AVAILABLE)
                            .build());
                }
            }
        }

        slotRepository.saveAll(toCreate);
        return toCreate.size();
    }

    private boolean overlapsTimeOff(List<TimeOff> timeOff, Instant start, Instant end) {
        return timeOff.stream().anyMatch(t -> t.getStartsAt().isBefore(end) && t.getEndsAt().isAfter(start));
    }
}
