package com.clinic.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "prescriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "disease", nullable = false, length = 200)
    private String disease;

    @Column(name = "medications", columnDefinition = "TEXT")
    private String medications;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "dosage_instructions", columnDefinition = "TEXT")
    private String dosageInstructions;

    @Column(name = "follow_up_date")
    private LocalDateTime followUpDate;

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    // The doctor who can edit this prescription (prescribing doctor)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "editable_by_doctor_id", nullable = false)
    private Doctor editableByDoctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
