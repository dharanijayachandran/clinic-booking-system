package com.dharanijayachandran.clinicbooking.booking;

import com.dharanijayachandran.clinicbooking.slot.Slot;
import com.dharanijayachandran.clinicbooking.slot.SlotRepository;
import com.dharanijayachandran.clinicbooking.slot.SlotStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public BookingService(SlotRepository slotRepository, AppointmentRepository appointmentRepository) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    /**
     * Books a slot for a patient, guaranteeing that exactly one of any number
     * of simultaneous attempts succeeds.
     *
     * How the race is actually won, in order:
     *
     * 1. findByIdForUpdate issues SELECT ... FOR UPDATE. Postgres grants the
     *    row lock to one transaction; every other transaction asking for the
     *    same row *blocks here* until the winner commits or rolls back.
     * 2. The status check below therefore reads committed state, not a stale
     *    snapshot. The loser wakes up, sees BOOKED, and is rejected cleanly.
     *    This ordering is the point — checking before locking is the classic
     *    TOCTOU bug this method exists to avoid.
     * 3. The insert is still wrapped against DataIntegrityViolationException,
     *    because idx_appointments_one_confirmed_per_slot (a partial unique
     *    index, see V1__init_schema.sql) is the real guarantee. If a future
     *    refactor ever loses the lock above, the database still refuses to
     *    hold two CONFIRMED appointments for one slot, and the caller still
     *    gets a 409 rather than corrupt data.
     *
     * The transaction stays deliberately small: two writes, no I/O. The
     * Phase 6 WebSocket broadcast belongs in an AFTER_COMMIT listener —
     * holding a contended row lock across a network call is how a correct
     * design becomes a bottleneck.
     */
    @Transactional
    public Appointment book(UUID slotId, UUID patientId) {
        Slot slot = slotRepository.findByIdForUpdate(slotId)
                .orElseThrow(() -> new SlotNotFoundException(slotId));

        if (slot.getStatus() != SlotStatus.AVAILABLE) {
            throw new SlotNotAvailableException(slotId);
        }

        slot.setStatus(SlotStatus.BOOKED);
        slotRepository.save(slot);

        Appointment appointment = Appointment.builder()
                .slotId(slotId)
                .patientId(patientId)
                .status(AppointmentStatus.CONFIRMED)
                .build();

        try {
            // saveAndFlush so the unique-index violation surfaces here, inside
            // the try, rather than at transaction commit where it can't be
            // translated into a clean 409.
            return appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException e) {
            throw new SlotNotAvailableException(slotId);
        }
    }

    @Transactional(readOnly = true)
    public List<Appointment> findForPatient(UUID patientId) {
        return appointmentRepository.findByPatientIdOrderByCreatedAtDesc(patientId);
    }
}
