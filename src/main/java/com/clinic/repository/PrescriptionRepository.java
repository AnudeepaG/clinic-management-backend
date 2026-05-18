package com.clinic.repository;

import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.Prescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, Long> {

    List<Prescription> findByPatient(Patient patient);

    List<Prescription> findByDoctor(Doctor doctor);

    List<Prescription> findByEditableByDoctor(Doctor doctor);

    @Query("SELECT p FROM Prescription p ORDER BY p.timestamp DESC")
    List<Prescription> findAllOrderByTimestampDesc();

    @Query("SELECT p FROM Prescription p WHERE p.patient = :patient ORDER BY p.timestamp DESC")
    List<Prescription> findByPatientOrderByTimestampDesc(@Param("patient") Patient patient);
}
