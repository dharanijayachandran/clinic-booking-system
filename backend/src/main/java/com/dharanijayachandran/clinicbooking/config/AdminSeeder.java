package com.dharanijayachandran.clinicbooking.config;

import com.dharanijayachandran.clinicbooking.user.Role;
import com.dharanijayachandran.clinicbooking.user.User;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Registration only ever creates PATIENT accounts (see RegisterRequest) — an
 * ADMIN has to come from somewhere. This creates exactly one, once, on first
 * boot, using dev-only default credentials unless overridden by env vars.
 * The Phase 7 admin UI is how every DOCTOR account after this one gets made.
 */
@Component
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email:admin@clinic.local}") String adminEmail,
            @Value("${app.admin.password:changeme123}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        User admin = User.builder()
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .firstName("Clinic")
                .lastName("Admin")
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        log.warn("Seeded initial admin account ({}) with a default/env-configured password. "
                + "Set app.admin.email / app.admin.password before any real deployment.", adminEmail);
    }
}
