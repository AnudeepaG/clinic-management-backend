package com.clinic.repository;

import com.clinic.entity.Appointment;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findByDoctorAndAppointmentDate(Doctor doctor, LocalDate date);

    List<Appointment> findByPatient(Patient patient);

    List<Appointment> findByDoctor(Doctor doctor);

    Optional<Appointment> findByDoctorAndAppointmentDateAndTimeSlot(
        Doctor doctor, LocalDate date, LocalTime timeSlot);

    boolean existsByDoctorAndAppointmentDateAndTimeSlot(
        Doctor doctor, LocalDate date, LocalTime timeSlot);

    @Query("SELECT a FROM Appointment a WHERE a.patient = :patient AND a.doctor = :doctor AND a.appointmentDate = :date")
    Optional<Appointment> findByPatientAndDoctorAndDate(
        @Param("patient") Patient patient,
        @Param("doctor") Doctor doctor,
        @Param("date") LocalDate date);

    @Query("SELECT a.timeSlot FROM Appointment a WHERE a.doctor = :doctor AND a.appointmentDate = :date AND a.status != 'CANCELLED'")
    List<LocalTime> findBookedSlotsByDoctorAndDate(
        @Param("doctor") Doctor doctor,
        @Param("date") LocalDate date);
}
