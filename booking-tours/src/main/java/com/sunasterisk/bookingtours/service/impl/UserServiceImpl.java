package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.RegisterRequest;
import com.sunasterisk.bookingtours.entity.Role;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.exception.DuplicateEmailException;
import com.sunasterisk.bookingtours.repository.RoleRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        // 1. Validate password confirm
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // 2. Lookup default role USER
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Role USER not found. Please seed the database."));

        // 3. Build & save user
        //    The DB unique constraint on `email` is the authoritative guard.
        //    Catching DataIntegrityViolationException here ensures that even under
        //    concurrent registrations (where the optimistic pre-read in the
        //    controller can race) the user always receives the same friendly error
        //    instead of an unhandled 500.
        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(userRole)
                .isActive(true)
                .build();

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Translate the constraint violation into a domain exception so the
            // controller (and any future caller) can present a proper user-facing
            // message without leaking persistence details.
            throw new DuplicateEmailException(request.getEmail());
        }
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
