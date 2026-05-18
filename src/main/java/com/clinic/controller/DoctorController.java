package com.clinic.controller;

import com.clinic.entity.Doctor;
import com.clinic.entity.User;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/doctors")
@CrossOrigin(origins = "*", maxAge = 3600)
public class DoctorController {

    @Autowired DoctorRepository doctorRepository;
    @Autowired UserRepository userRepository;

    // ─── List all doctors (authenticated) ─────────────────────────────────────
    @GetMapping
    public ResponseEntity<?> getAllDoctors() {
        List<Map<String, Object>> doctors = doctorRepository.findAll().stream()
            .map(this::mapDoctor)
            .collect(Collectors.toList());
        return ResponseEntity.ok(doctors);
    }

    // ─── Get doctor by id ──────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getDoctorById(@PathVariable Long id) {
        return doctorRepository.findById(id)
            .map(d -> ResponseEntity.ok(mapDoctor(d)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Get my profile (doctor) ───────────────────────────────────────────────
    @GetMapping("/me")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> getMyProfile(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        return doctorRepository.findByUser(user)
            .map(d -> ResponseEntity.ok(mapDoctor(d)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Update available slots ────────────────────────────────────────────────
    @PutMapping("/{id}/slots")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> updateSlots(@PathVariable Long id,
                                         @RequestBody Map<String, String> body,
                                         Authentication auth) {
        Doctor doctor = doctorRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Doctor not found"));
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Only the doctor themselves can update slots
        if (!doctor.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "You can only update your own slots"));
        }

        doctor.setAvailableSlots(body.get("availableSlots"));
        doctorRepository.save(doctor);
        return ResponseEntity.ok(Map.of("message", "Slots updated", "availableSlots", doctor.getAvailableSlots()));
    }

    // ─── Update doctor profile ─────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> updateProfile(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           Authentication auth) {
        Doctor doctor = doctorRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Doctor not found"));
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (!doctor.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        if (body.containsKey("fullName")) doctor.setFullName(body.get("fullName"));
        if (body.containsKey("specialty")) doctor.setSpecialty(body.get("specialty"));
        if (body.containsKey("phone")) doctor.setPhone(body.get("phone"));
        doctorRepository.save(doctor);
        return ResponseEntity.ok(mapDoctor(doctor));
    }

    // ─── Admin: list all doctors ───────────────────────────────────────────────
    @GetMapping("/admin/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminListAll() {
        return ResponseEntity.ok(doctorRepository.findAll().stream().map(this::mapDoctor).collect(Collectors.toList()));
    }

    private Map<String, Object> mapDoctor(Doctor d) {
        return Map.of(
            "id", d.getId(),
            "userId", d.getUser().getId(),
            "username", d.getUser().getUsername(),
            "email", d.getUser().getEmail(),
            "fullName", d.getFullName(),
            "specialty", d.getSpecialty(),
            "phone", d.getPhone() != null ? d.getPhone() : "",
            "licenseNumber", d.getLicenseNumber() != null ? d.getLicenseNumber() : "",
            "availableSlots", d.getAvailableSlots() != null ? d.getAvailableSlots() : "",
            "slotsPerDay", d.getSlotsPerDay()
        );
    }
}
