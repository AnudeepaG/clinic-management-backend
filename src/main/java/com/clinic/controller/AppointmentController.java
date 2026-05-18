package com.clinic.controller;

import com.clinic.entity.*;
import com.clinic.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AppointmentController {

    @Autowired AppointmentRepository appointmentRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired UserRepository userRepository;

    // ─── Get available slots for a doctor on a date ────────────────────────────
    @GetMapping("/slots")
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam Long doctorId,
            @RequestParam String date) {
        Doctor doctor = doctorRepository.findById(doctorId)
            .orElseThrow(() -> new RuntimeException("Doctor not found"));
        LocalDate localDate = LocalDate.parse(date);

        String[] definedSlots = doctor.getAvailableSlots() != null
            ? doctor.getAvailableSlots().split(",") : new String[0];

        List<LocalTime> bookedSlots = appointmentRepository
            .findBookedSlotsByDoctorAndDate(doctor, localDate);

        List<Map<String, Object>> slotList = new ArrayList<>();
        for (String slot : definedSlots) {
            LocalTime time = LocalTime.parse(slot.trim());
            Map<String, Object> s = new HashMap<>();
            s.put("time", slot.trim());
            s.put("available", !bookedSlots.contains(time));
            slotList.add(s);
        }
        return ResponseEntity.ok(slotList);
    }

    // ─── Book appointment (patient) ────────────────────────────────────────────
    @PostMapping("/book")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> bookAppointment(@RequestBody Map<String, String> body, Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        Patient patient = patientRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Patient profile not found"));

        Doctor doctor = doctorRepository.findById(Long.parseLong(body.get("doctorId")))
            .orElseThrow(() -> new RuntimeException("Doctor not found"));

        LocalDate date = LocalDate.parse(body.get("date"));
        LocalTime slot = LocalTime.parse(body.get("timeSlot"));

        // Check double-booking
        if (appointmentRepository.existsByDoctorAndAppointmentDateAndTimeSlot(doctor, date, slot)) {
            return ResponseEntity.badRequest().body(Map.of("error", "This slot is already booked"));
        }

        // One appointment per patient per doctor per day
        appointmentRepository.findByPatientAndDoctorAndDate(patient, doctor, date).ifPresent(a -> {
            throw new RuntimeException("You already have an appointment with this doctor on this date");
        });

        // Validate slot is in doctor's defined slots
        String availableSlots = doctor.getAvailableSlots() != null ? doctor.getAvailableSlots() : "";
        boolean validSlot = Arrays.stream(availableSlots.split(","))
            .map(String::trim)
            .anyMatch(s -> LocalTime.parse(s).equals(slot));
        if (!validSlot) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid time slot"));
        }

        Appointment appointment = Appointment.builder()
            .doctor(doctor)
            .patient(patient)
            .appointmentDate(date)
            .timeSlot(slot)
            .status(Appointment.AppointmentStatus.SCHEDULED)
            .notes(body.getOrDefault("notes", ""))
            .build();
        appointmentRepository.save(appointment);
        return ResponseEntity.ok(mapAppointment(appointment));
    }

    // ─── Get my appointments (patient) ────────────────────────────────────────
    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyAppointments(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        Patient patient = patientRepository.findByUser(user).orElseThrow();
        List<Map<String, Object>> list = appointmentRepository.findByPatient(patient)
            .stream().map(this::mapAppointment).collect(Collectors.toList());
        return ResponseEntity.ok(list);
    }

    // ─── Get doctor's appointments ─────────────────────────────────────────────
    @GetMapping("/doctor/{doctorId}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<?> getDoctorAppointments(
            @PathVariable Long doctorId,
            @RequestParam(required = false) String date) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        List<Appointment> appointments;
        if (date != null) {
            appointments = appointmentRepository.findByDoctorAndAppointmentDate(doctor, LocalDate.parse(date));
        } else {
            appointments = appointmentRepository.findByDoctor(doctor);
        }
        return ResponseEntity.ok(appointments.stream().map(this::mapAppointment).collect(Collectors.toList()));
    }

    // ─── Update appointment status ─────────────────────────────────────────────
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, String> body) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));
        appointment.setStatus(Appointment.AppointmentStatus.valueOf(body.get("status")));
        appointmentRepository.save(appointment);
        return ResponseEntity.ok(mapAppointment(appointment));
    }

    // ─── Cancel appointment (patient) ─────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> cancelAppointment(@PathVariable Long id, Authentication auth) {
        Appointment appointment = appointmentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Appointment not found"));
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        Patient patient = patientRepository.findByUser(user).orElseThrow();

        if (!appointment.getPatient().getId().equals(patient.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }
        appointment.setStatus(Appointment.AppointmentStatus.CANCELLED);
        appointmentRepository.save(appointment);
        return ResponseEntity.ok(Map.of("message", "Appointment cancelled"));
    }

    private Map<String, Object> mapAppointment(Appointment a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("doctorId", a.getDoctor().getId());
        m.put("doctorName", a.getDoctor().getFullName());
        m.put("doctorSpecialty", a.getDoctor().getSpecialty());
        m.put("patientId", a.getPatient().getId());
        m.put("patientName", a.getPatient().getFullName());
        m.put("appointmentDate", a.getAppointmentDate().toString());
        m.put("timeSlot", a.getTimeSlot().toString());
        m.put("status", a.getStatus().name());
        m.put("notes", a.getNotes() != null ? a.getNotes() : "");
        m.put("createdAt", a.getCreatedAt() != null ? a.getCreatedAt().toString() : "");
        return m;
    }
}
