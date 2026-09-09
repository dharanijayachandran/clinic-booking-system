package com.dharanijayachandran.clinicbooking;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@OpenAPIDefinition(
        info = @Info(
                title = "Clinic Appointment Booking API",
                description = "There's no frontend yet (that's Phase 4+) — this is the "
                        + "interactive way to try the auth flow this demo currently has. "
                        + "Register a patient, log in (sets httpOnly cookies — click the "
                        + "padlock icons, no manual token copying needed), then call /me. "
                        + "Source and architecture write-up: "
                        + "https://github.com/dharanijayachandran/clinic-booking-system",
                version = "0.2.0 — Phase 2 of 8 (auth)"))
public class ClinicBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClinicBookingApplication.class, args);
    }
}
