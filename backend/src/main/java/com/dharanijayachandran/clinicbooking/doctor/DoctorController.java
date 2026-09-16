package com.dharanijayachandran.clinicbooking.doctor;

import com.dharanijayachandran.clinicbooking.doctor.dto.DoctorResponse;
import com.dharanijayachandran.clinicbooking.user.User;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;

    public DoctorController(DoctorRepository doctorRepository, UserRepository userRepository) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
    }

    /**
     * Doctors, with their names. Two queries rather than a JPA association:
     * Doctor is deliberately association-free (see Slot's javadoc for the
     * same reasoning), and a doctor list is small and rarely changes, so a
     * second lookup keyed by id is cheaper than the mapping complexity.
     */
    @GetMapping
    public ResponseEntity<List<DoctorResponse>> list() {
        List<Doctor> doctors = doctorRepository.findAll();

        Map<UUID, User> usersById = userRepository
                .findAllById(doctors.stream().map(Doctor::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<DoctorResponse> response = doctors.stream()
                .map(doctor -> {
                    User user = usersById.get(doctor.getUserId());
                    String name = user == null
                            ? "Unknown"
                            : "Dr %s %s".formatted(user.getFirstName(), user.getLastName());
                    return new DoctorResponse(
                            doctor.getUserId(),
                            name,
                            doctor.getSpecialty(),
                            doctor.getBio(),
                            doctor.getSlotDurationMinutes());
                })
                .sorted((a, b) -> a.name().compareToIgnoreCase(b.name()))
                .toList();

        return ResponseEntity.ok(response);
    }
}
