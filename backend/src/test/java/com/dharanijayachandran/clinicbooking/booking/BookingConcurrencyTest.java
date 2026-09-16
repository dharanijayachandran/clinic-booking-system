package com.dharanijayachandran.clinicbooking.booking;

import static org.assertj.core.api.Assertions.assertThat;

import com.dharanijayachandran.clinicbooking.doctor.Doctor;
import com.dharanijayachandran.clinicbooking.doctor.DoctorRepository;
import com.dharanijayachandran.clinicbooking.slot.Slot;
import com.dharanijayachandran.clinicbooking.slot.SlotRepository;
import com.dharanijayachandran.clinicbooking.slot.SlotStatus;
import com.dharanijayachandran.clinicbooking.support.DatabaseCleaner;
import com.dharanijayachandran.clinicbooking.support.PostgresTestBase;
import com.dharanijayachandran.clinicbooking.user.Role;
import com.dharanijayachandran.clinicbooking.user.User;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The test this whole project exists to pass.
 *
 * Note it drives BookingService directly rather than going through MockMvc:
 * each thread needs its own real transaction against real Postgres, which is
 * what exercises SELECT ... FOR UPDATE. Running this against H2 would prove
 * nothing — hence Testcontainers (see PostgresTestBase).
 */
class BookingConcurrencyTest extends PostgresTestBase {

    @Autowired private BookingService bookingService;
    @Autowired private SlotRepository slotRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private DatabaseCleaner databaseCleaner;

    private UUID slotId;
    private UUID patientA;
    private UUID patientB;

    @BeforeEach
    void seedOneContendedSlot() {
        databaseCleaner.clean();

        User doctorUser = userRepository.save(user("doctor@example.com", Role.DOCTOR));
        doctorRepository.save(Doctor.builder()
                .userId(doctorUser.getId())
                .specialty("General")
                .slotDurationMinutes((short) 30)
                .build());

        patientA = userRepository.save(user("a@example.com", Role.PATIENT)).getId();
        patientB = userRepository.save(user("b@example.com", Role.PATIENT)).getId();

        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        slotId = slotRepository.save(Slot.builder()
                .doctorId(doctorUser.getId())
                .startsAt(start)
                .endsAt(start.plus(30, ChronoUnit.MINUTES))
                .status(SlotStatus.AVAILABLE)
                .build()).getId();
    }

    @Test
    void twoSimultaneousBookingsForTheSameSlot_exactlyOneWins() throws Exception {
        // Both threads are held at the latch, then released together, so they
        // genuinely race for the row instead of running back to back.
        CountDownLatch startLine = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> races = List.of(
                    pool.submit(attemptBooking(startLine, patientA, succeeded, rejected)),
                    pool.submit(attemptBooking(startLine, patientB, succeeded, rejected)));

            startLine.countDown();
            for (Future<?> race : races) {
                race.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded.get()).as("exactly one booking succeeds").isEqualTo(1);
        assertThat(rejected.get()).as("the other is cleanly rejected").isEqualTo(1);

        assertThat(appointmentRepository.countBySlotIdAndStatus(slotId, AppointmentStatus.CONFIRMED))
                .as("the database holds exactly one confirmed appointment")
                .isEqualTo(1);

        assertThat(slotRepository.findById(slotId).orElseThrow().getStatus())
                .as("and the slot is marked booked")
                .isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void bookingAnAlreadyBookedSlotIsRejected() {
        bookingService.book(slotId, patientA);

        assertThatSlotNotAvailable(() -> bookingService.book(slotId, patientB));
        assertThat(appointmentRepository.countBySlotIdAndStatus(slotId, AppointmentStatus.CONFIRMED))
                .isEqualTo(1);
    }

    /**
     * Defence in depth: even if the service's locking were bypassed entirely,
     * the partial unique index still refuses a second CONFIRMED appointment.
     */
    @Test
    void theDatabaseItselfRefusesASecondConfirmedAppointment() {
        appointmentRepository.saveAndFlush(Appointment.builder()
                .slotId(slotId)
                .patientId(patientA)
                .status(AppointmentStatus.CONFIRMED)
                .build());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        appointmentRepository.saveAndFlush(Appointment.builder()
                                .slotId(slotId)
                                .patientId(patientB)
                                .status(AppointmentStatus.CONFIRMED)
                                .build()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private Callable<Void> attemptBooking(
            CountDownLatch startLine, UUID patientId, AtomicInteger succeeded, AtomicInteger rejected) {
        return () -> {
            startLine.await();
            try {
                bookingService.book(slotId, patientId);
                succeeded.incrementAndGet();
            } catch (SlotNotAvailableException e) {
                rejected.incrementAndGet();
            }
            return null;
        };
    }

    private void assertThatSlotNotAvailable(Runnable action) {
        org.assertj.core.api.Assertions.assertThatThrownBy(action::run)
                .isInstanceOf(SlotNotAvailableException.class);
    }

    private User user(String email, Role role) {
        return User.builder()
                .email(email)
                .passwordHash("irrelevant-for-this-test")
                .firstName("Test")
                .lastName("User")
                .role(role)
                .build();
    }
}
