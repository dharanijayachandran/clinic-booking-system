package com.dharanijayachandran.clinicbooking.slot;

import com.dharanijayachandran.clinicbooking.slot.dto.SlotResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/slots")
public class SlotController {

    private final SlotRepository slotRepository;

    public SlotController(SlotRepository slotRepository) {
        this.slotRepository = slotRepository;
    }

    /** Available slots for one doctor in a window — what the calendar renders. */
    @GetMapping
    public ResponseEntity<List<SlotResponse>> available(
            @RequestParam UUID doctorId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        List<SlotResponse> slots = slotRepository
                .findByDoctorIdAndStatusAndStartsAtBetweenOrderByStartsAt(doctorId, SlotStatus.AVAILABLE, from, to)
                .stream()
                .map(SlotResponse::from)
                .toList();

        return ResponseEntity.ok(slots);
    }
}
