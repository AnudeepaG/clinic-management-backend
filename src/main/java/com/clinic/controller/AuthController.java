package com.clinic.controller;

import com.clinic.dto.JwtResponse;
import com.clinic.dto.LoginRequest;
import com.clinic.dto.MessageResponse;
import com.clinic.dto.RegisterDoctorRequest;
import com.clinic.dto.RegisterPatientRequest;
import com.clinic.entity.Doctor;
import com.clinic.entity.Patient;
import com.clinic.entity.User;
import com.clinic.repository.DoctorRepository;
import com.clinic.repository.PatientRepository;
import com.clinic.repository.UserRepository;
import com.clinic.security.JwtUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;

    private final UserRepository userRepository;

    private final DoctorRepository doctorRepository;

    private final PatientRepository patientRepository;

    private final PasswordEncoder encoder;

    private final JwtUtils jwtUtils;

    // ───────────────── LOGIN ─────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @Valid @RequestBody LoginRequest request
    ) {

        try {

            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.getUsername(),
                                    request.getPassword()
                            )
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

            String jwt =
                    jwtUtils.generateJwtToken(authentication);

            UserDetails userDetails =
                    (UserDetails) authentication.getPrincipal();

            User user =
                    userRepository.findByUsername(
                            userDetails.getUsername()
                    ).orElseThrow();

            Long profileId = null;

            if (user.getRole() == User.Role.DOCTOR) {

                profileId =
                        doctorRepository.findByUser(user)
                                .map(Doctor::getId)
                                .orElse(null);
            }

            else if (user.getRole() == User.Role.PATIENT) {

                profileId =
                        patientRepository.findByUser(user)
                                .map(Patient::getId)
                                .orElse(null);
            }

            JwtResponse response =
                    JwtResponse.builder()
                            .token(jwt)
                            .type("Bearer")
                            .id(user.getId())
                            .username(user.getUsername())
                            .email(user.getEmail())
                            .role(user.getRole().name())
                            .profileId(profileId)
                            .build();

            return ResponseEntity.ok(response);

        }

        catch (BadCredentialsException ex) {

            return ResponseEntity
                    .status(401)
                    .body(new MessageResponse(
                            "Invalid username or password"
                    ));
        }
    }

    // ───────────────── REGISTER DOCTOR ─────────────────

    @PostMapping("/register/doctor")
    public ResponseEntity<?> registerDoctor(
            @Valid @RequestBody RegisterDoctorRequest request
    ) {

        if (userRepository.existsByUsername(request.getUsername())) {

            return ResponseEntity.badRequest()
                    .body(new MessageResponse(
                            "Username already taken"
                    ));
        }

        if (userRepository.existsByEmail(request.getEmail())) {

            return ResponseEntity.badRequest()
                    .body(new MessageResponse(
                            "Email already in use"
                    ));
        }

        User user =
                User.builder()
                        .username(request.getUsername())
                        .email(request.getEmail())
                        .password(
                                encoder.encode(
                                        request.getPassword()
                                )
                        )
                        .role(User.Role.DOCTOR)
                        .enabled(true)
                        .build();

        userRepository.save(user);

        Doctor doctor =
                Doctor.builder()
                        .user(user)
                        .fullName(request.getFullName())
                        .specialty(request.getSpecialty())
                        .phone(request.getPhone())
                        .licenseNumber(request.getLicenseNumber())
                        .availableSlots(generateDefaultSlots())
                        .slotsPerDay(20)
                        .build();

        doctorRepository.save(doctor);

        return ResponseEntity.ok(
                new MessageResponse(
                        "Doctor registered successfully"
                )
        );
    }

    // ───────────────── REGISTER PATIENT ─────────────────

    @PostMapping("/register/patient")
    public ResponseEntity<?> registerPatient(
            @Valid @RequestBody RegisterPatientRequest request
    ) {

        if (userRepository.existsByUsername(request.getUsername())) {

            return ResponseEntity.badRequest()
                    .body(new MessageResponse(
                            "Username already taken"
                    ));
        }

        if (userRepository.existsByEmail(request.getEmail())) {

            return ResponseEntity.badRequest()
                    .body(new MessageResponse(
                            "Email already in use"
                    ));
        }

        User user =
                User.builder()
                        .username(request.getUsername())
                        .email(request.getEmail())
                        .password(
                                encoder.encode(
                                        request.getPassword()
                                )
                        )
                        .role(User.Role.PATIENT)
                        .enabled(true)
                        .build();

        userRepository.save(user);

        LocalDate dob = null;

        if (request.getDateOfBirth() != null
                && !request.getDateOfBirth().isBlank()) {

            dob =
                    LocalDate.parse(
                            request.getDateOfBirth()
                    );
        }

        Patient patient =
                Patient.builder()
                        .user(user)
                        .fullName(request.getFullName())
                        .dateOfBirth(dob)
                        .gender(request.getGender())
                        .phone(request.getPhone())
                        .address(request.getAddress())
                        .bloodGroup(request.getBloodGroup())
                        .build();

        patientRepository.save(patient);

        return ResponseEntity.ok(
                new MessageResponse(
                        "Patient registered successfully"
                )
        );
    }

    // ───────────────── DEFAULT SLOTS ─────────────────

    private String generateDefaultSlots() {

        StringBuilder sb = new StringBuilder();

        int hour = 9;
        int minute = 0;

        for (int i = 0; i < 20; i++) {

            if (!sb.isEmpty()) {
                sb.append(",");
            }

            sb.append(
                    String.format(
                            "%02d:%02d",
                            hour,
                            minute
                    )
            );

            minute += 15;

            if (minute >= 60) {
                hour++;
                minute = 0;
            }
        }

        return sb.toString();
    }
}