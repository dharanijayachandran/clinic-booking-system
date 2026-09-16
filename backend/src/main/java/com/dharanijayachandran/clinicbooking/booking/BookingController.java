package com.dharanijayachandran.clinicbooking.booking;

import com.dharanijayachandran.clinicbooking.auth.UserPrincipal;
import com.dharanijayachandran.clinicbooking.booking.dto.AppointmentResponse;
import com.dharanijayachandran.clinicbooking.booking.dto.BookingRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    /** 201 on success, 409 when the slot was taken first, 404 if it never existed. */
    @PostMapping
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<AppointmentResponse> book(
            @Valid @RequestBody BookingRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        Appointment appointment = bookingService.book(request.slotId(), principal.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(AppointmentResponse.from(appointment));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<List<AppointmentResponse>> mine(@AuthenticationPrincipal UserPrincipal principal) {
        List<AppointmentResponse> appointments = bookingService.findForPatient(principal.id()).stream()
                .map(AppointmentResponse::from)
                .toList();
        return ResponseEntity.ok(appointments);
    }
}
