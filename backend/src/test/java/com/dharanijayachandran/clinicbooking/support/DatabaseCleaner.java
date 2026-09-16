package com.dharanijayachandran.clinicbooking.support;

import com.dharanijayachandran.clinicbooking.booking.AppointmentRepository;
import com.dharanijayachandran.clinicbooking.doctor.DoctorRepository;
import com.dharanijayachandran.clinicbooking.doctor.TimeOffRepository;
import com.dharanijayachandran.clinicbooking.doctor.WorkingHoursRepository;
import com.dharanijayachandran.clinicbooking.slot.SlotRepository;
import com.dharanijayachandran.clinicbooking.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Wipes test data in foreign-key-safe order.
 *
 * This exists because order matters and getting it wrong fails confusingly:
 * appointments.slot_id has no ON DELETE CASCADE (deliberately — an
 * appointment is a historical record and shouldn't silently vanish because a
 * slot was removed), so appointments must go before slots, and slots before
 * the doctors/users they cascade from.
 */
@Component
public class DatabaseCleaner {

    private final AppointmentRepository appointments;
    private final SlotRepository slots;
    private final TimeOffRepository timeOff;
    private final WorkingHoursRepository workingHours;
    private final DoctorRepository doctors;
    private final UserRepository users;

    public DatabaseCleaner(
            AppointmentRepository appointments,
            SlotRepository slots,
            TimeOffRepository timeOff,
            WorkingHoursRepository workingHours,
            DoctorRepository doctors,
            UserRepository users) {
        this.appointments = appointments;
        this.slots = slots;
        this.timeOff = timeOff;
        this.workingHours = workingHours;
        this.doctors = doctors;
        this.users = users;
    }

    @Transactional
    public void clean() {
        appointments.deleteAllInBatch();
        slots.deleteAllInBatch();
        timeOff.deleteAllInBatch();
        workingHours.deleteAllInBatch();
        doctors.deleteAllInBatch();
        users.deleteAllInBatch();
    }
}
