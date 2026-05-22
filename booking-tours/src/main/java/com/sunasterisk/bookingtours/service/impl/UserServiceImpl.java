package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.dto.RegisterRequest;
import com.sunasterisk.bookingtours.entity.Role;
import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.repository.RoleRepository;
import com.sunasterisk.bookingtours.repository.UserRepository;
import com.sunasterisk.bookingtours.service.UserService;
import lombok.RequiredArgsConstructor;
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
        // 1. Validate email unique
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already in use: " + request.getEmail());
        }

        // 2. Validate password confirm
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match");
        }

        // 3. Lookup default role USER
        Role userRole = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("Role USER not found. Please seed the database."));

        // 4. Build & save user
        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(userRole)
                .isActive(true)
                .build();

        return userRepository.save(user);
    }

    @Override
    public boolean emailExists(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
