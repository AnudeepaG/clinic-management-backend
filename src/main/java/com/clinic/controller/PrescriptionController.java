package com.clinic.controller;

import com.clinic.entity.*;
import com.clinic.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/prescriptions")
@CrossOrigin(origins = "*", maxAge = 3600)
public class PrescriptionController {

    @Autowired PrescriptionRepository prescriptionRepository;
    @Autowired DoctorRepository doctorRepository;
    @Autowired PatientRepository patientRepository;
    @Autowired AppointmentRepository appointmentRepository;
    @Autowired UserRepository userRepository;

    // ─── All prescriptions (all doctors can read) ──────────────────────────────
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('DOCTOR','ADMIN')")
    public ResponseEntity<?> getAllPrescriptions() {
        return ResponseEntity.ok(prescriptionRepository.findAllOrderByTimestampDesc()
            .stream().map(this::mapPrescription).collect(Collectors.toList()));
    }

    // ─── My prescriptions (patient) ───────────────────────────────────────────
    @GetMapping("/my")
    @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<?> getMyPrescriptions(Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        Patient patient = patientRepository.findByUser(user).orElseThrow();
        return ResponseEntity.ok(
            prescriptionRepository.findByPatientOrderByTimestampDesc(patient)
                .stream().map(this::mapPrescription).collect(Collectors.toList()));
    }

    // ─── Get prescription by id ────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<?> getPrescriptionById(@PathVariable Long id, Authentication auth) {
        Prescription p = prescriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Prescription not found"));
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();

        // Patient can only see their own; doctors/admin can see all
        if (user.getRole() == User.Role.PATIENT) {
            Patient patient = patientRepository.findByUser(user).orElseThrow();
            if (!p.getPatient().getId().equals(patient.getId())) {
                return ResponseEntity.status(403).body(Map.of("error", "Forbidden"));
            }
        }
        return ResponseEntity.ok(mapPrescription(p));
    }

    // ─── Create prescription (doctor only) ────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> createPrescription(@RequestBody Map<String, Object> body, Authentication auth) {
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        Doctor doctor = doctorRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Doctor profile not found"));
        Patient patient = patientRepository.findById(Long.parseLong(body.get("patientId").toString()))
            .orElseThrow(() -> new RuntimeException("Patient not found"));

        Prescription prescription = Prescription.builder()
            .patient(patient)
            .doctor(doctor)
            .disease(body.get("disease").toString())
            .medications(body.getOrDefault("medications", "").toString())
            .notes(body.getOrDefault("notes", "").toString())
            .dosageInstructions(body.getOrDefault("dosageInstructions", "").toString())
            .editableByDoctor(doctor)   // only prescribing doctor can edit
            .timestamp(LocalDateTime.now())
            .build();

        if (body.get("appointmentId") != null) {
            appointmentRepository.findById(Long.parseLong(body.get("appointmentId").toString()))
                .ifPresent(prescription::setAppointment);
        }

        if (body.get("followUpDate") != null && !body.get("followUpDate").toString().isEmpty()) {
            prescription.setFollowUpDate(LocalDateTime.parse(body.get("followUpDate").toString()));
        }

        prescriptionRepository.save(prescription);
        return ResponseEntity.ok(mapPrescription(prescription));
    }

    // ─── Update prescription (only prescribing doctor) ────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> updatePrescription(@PathVariable Long id,
                                                @RequestBody Map<String, Object> body,
                                                Authentication auth) {
        Prescription prescription = prescriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Prescription not found"));
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        Doctor doctor = doctorRepository.findByUser(user)
            .orElseThrow(() -> new RuntimeException("Doctor profile not found"));

        // Only the prescribing doctor can edit
        if (!prescription.getEditableByDoctor().getId().equals(doctor.getId())) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "Only the prescribing doctor can edit this prescription"));
        }

        if (body.containsKey("disease")) prescription.setDisease(body.get("disease").toString());
        if (body.containsKey("medications")) prescription.setMedications(body.get("medications").toString());
        if (body.containsKey("notes")) prescription.setNotes(body.get("notes").toString());
        if (body.containsKey("dosageInstructions")) prescription.setDosageInstructions(body.get("dosageInstructions").toString());

        prescriptionRepository.save(prescription);
        return ResponseEntity.ok(mapPrescription(prescription));
    }

    // ─── Delete prescription (only prescribing doctor) ────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<?> deletePrescription(@PathVariable Long id, Authentication auth) {
        Prescription prescription = prescriptionRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Prescription not found"));
        User user = userRepository.findByUsername(auth.getName()).orElseThrow();
        Doctor doctor = doctorRepository.findByUser(user).orElseThrow();

        if (!prescription.getEditableByDoctor().getId().equals(doctor.getId())) {
            return ResponseEntity.status(403).body(Map.of("error", "Only the prescribing doctor can delete"));
        }
        prescriptionRepository.delete(prescription);
        return ResponseEntity.ok(Map.of("message", "Prescription deleted"));
    }

    private Map<String, Object> mapPrescription(Prescription p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.getId());
        m.put("patientId", p.getPatient().getId());
        m.put("patientName", p.getPatient().getFullName());
        m.put("doctorId", p.getDoctor().getId());
        m.put("doctorName", p.getDoctor().getFullName());
        m.put("doctorSpecialty", p.getDoctor().getSpecialty());
        m.put("disease", p.getDisease());
        m.put("medications", p.getMedications() != null ? p.getMedications() : "");
        m.put("notes", p.getNotes() != null ? p.getNotes() : "");
        m.put("dosageInstructions", p.getDosageInstructions() != null ? p.getDosageInstructions() : "");
        m.put("timestamp", p.getTimestamp() != null ? p.getTimestamp().toString() : "");
        m.put("editableByDoctorId", p.getEditableByDoctor().getId());
        m.put("followUpDate", p.getFollowUpDate() != null ? p.getFollowUpDate().toString() : "");
        m.put("appointmentId", p.getAppointment() != null ? p.getAppointment().getId() : null);
        return m;
    }
}
