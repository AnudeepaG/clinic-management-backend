package com.clinic.controller;

import com.clinic.entity.Patient;
import com.clinic.entity.User;
import com.clinic.repository.PatientRepository;
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
@RequestMapping("/api/patients")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PatientController {

    @Autowired PatientRepository patientRepository;
    @Autowired UserRepository userRepository;

    // ─── Get my profile (patient) ──────────────────────────────────────────────
    @GetMapping("/me")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyProfile(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        return patientRepository.findByUser(user)
            .map(p -> ResponseEntity.ok(mapPatient(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Get patient by id (doctor/admin can view) ─────────────────────────────
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<?> getPatientById(@PathVariable Long id) {
        return patientRepository.findById(id)
            .map(p -> ResponseEntity.ok(mapPatient(p)))
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── List all patients (doctor/admin) ─────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<?> listAllPatients() {
        List<Map<String, Object>> patients = patientRepository.findAll()
            .stream().map(this::mapPatient).collect(Collectors.toList());
        return ResponseEntity.ok(patients);
    }

    // ─── Update patient profile ────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> updateProfile(@PathVariable Long id,
                                           @RequestBody Map<String, String> body,
                                           Authentication auth) {
        Patient patient = patientRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Patient not found"));
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        if (!patient.getUser().getId().equals(user.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
        }

        if (body.containsKey("phone")) patient.setPhone(body.get("phone"));
        if (body.containsKey("address")) patient.setAddress(body.get("address"));
        if (body.containsKey("emergencyContact")) patient.setEmergencyContact(body.get("emergencyContact"));
        patientRepository.save(patient);
        return ResponseEntity.ok(mapPatient(patient));
    }

    private Map<String, Object> mapPatient(Patient p) {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("id", p.getId());
        map.put("userId", p.getUser().getId());
        map.put("username", p.getUser().getUsername());
        map.put("email", p.getUser().getEmail());
        map.put("fullName", p.getFullName());
        map.put("dateOfBirth", p.getDateOfBirth() != null ? p.getDateOfBirth().toString() : "");
        map.put("gender", p.getGender() != null ? p.getGender() : "");
        map.put("phone", p.getPhone() != null ? p.getPhone() : "");
        map.put("address", p.getAddress() != null ? p.getAddress() : "");
        map.put("bloodGroup", p.getBloodGroup() != null ? p.getBloodGroup() : "");
        map.put("emergencyContact", p.getEmergencyContact() != null ? p.getEmergencyContact() : "");
        return map;
    }
}
