package com.dharanijayachandran.clinicbooking.config;

import com.dharanijayachandran.clinicbooking.doctor.Doctor;
import com.dharanijayachandran.clinicbooking.doctor.DoctorRepository;
import com.dharanijayachandran.clinicbooking.doctor.WorkingHours;
import com.dharanijayachandran.clinicbooking.doctor.WorkingHoursRepository;
import com.dharanijayachandran.clinicbooking.slot.SlotGenerationService;
import com.dharanijayachandran.clinicbooking.user.Role;
import com.dharanijayachandran.clinicbooking.user.User;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a couple of doctors with working hours and generates their slots,
 * so a fresh deployment has something to actually book.
 *
 * This is real data in a real database, not frontend mocks — the rule is that
 * the UI always talks to the real API, and it still does. Without this, the
 * calendar on a clean environment is simply empty, which demonstrates
 * nothing. Off by default; switched on for the demo deployment and local
 * compose via app.demo.seed.
 *
 * Idempotent: it checks for an existing doctor before doing anything, so
 * restarts don't pile up duplicates.
 */
@Component
@ConditionalOnProperty(name = "app.demo.seed", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final int DAYS_OF_SLOTS = 21;

    private final UserRepository userRepository;
    private final DoctorRepository doctorRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final SlotGenerationService slotGenerationService;
    private final PasswordEncoder passwordEncoder;
    private final String demoPassword;

    public DemoDataSeeder(
            UserRepository userRepository,
            DoctorRepository doctorRepository,
            WorkingHoursRepository workingHoursRepository,
            SlotGenerationService slotGenerationService,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.password:demo12345}") String demoPassword) {
        this.userRepository = userRepository;
        this.doctorRepository = doctorRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.slotGenerationService = slotGenerationService;
        this.passwordEncoder = passwordEncoder;
        this.demoPassword = demoPassword;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (doctorRepository.count() > 0) {
            return;
        }

        seedDoctor("ana.silva@clinic.local", "Ana", "Silva", "General Practice", (short) 30);
        seedDoctor("raj.mehta@clinic.local", "Raj", "Mehta", "Dermatology", (short) 20);

        log.warn("Seeded demo doctors and {} days of slots (app.demo.seed=true). "
                + "Demo accounts share the password from app.demo.password.", DAYS_OF_SLOTS);
    }

    private void seedDoctor(String email, String firstName, String lastName, String specialty, short slotMinutes) {
        User user = userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(demoPassword))
                .firstName(firstName)
                .lastName(lastName)
                .role(Role.DOCTOR)
                .build());

        doctorRepository.save(Doctor.builder()
                .userId(user.getId())
                .specialty(specialty)
                .bio("Demo doctor for the public sandbox.")
                .slotDurationMinutes(slotMinutes)
                .build());

        // Monday–Friday, 09:00–17:00 (day_of_week is 0 = Sunday).
        List<WorkingHours> week = List.of(1, 2, 3, 4, 5).stream()
                .map(day -> WorkingHours.builder()
                        .doctorId(user.getId())
                        .dayOfWeek(day.shortValue())
                        .startTime(LocalTime.of(9, 0))
                        .endTime(LocalTime.of(17, 0))
                        .build())
                .toList();
        workingHoursRepository.saveAll(week);

        LocalDate today = LocalDate.now();
        slotGenerationService.generateFor(user.getId(), today, today.plusDays(DAYS_OF_SLOTS));
    }
}
