package com.sunasterisk.bookingtours.service.impl;

import com.sunasterisk.bookingtours.entity.User;
import com.sunasterisk.bookingtours.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation của UserDetailsService — được Spring Security gọi
 * khi cần load thông tin user theo username (trong quá trình xác thực).
 *
 * @Transactional(readOnly = true) để giữ JPA session mở khi truy cập
 * lazy association User.role — tránh LazyInitializationException.
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @NonNull
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + username));

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new UsernameNotFoundException("User account is disabled: " + username);
        }

        // Chuyển Role → GrantedAuthority (ROLE_ADMIN / ROLE_USER)
        // user.getRole() an toàn vì đang trong @Transactional
        String roleName = (user.getRole() != null) ? "ROLE_" + user.getRole().getName() : "ROLE_USER";
        SimpleGrantedAuthority authority = new SimpleGrantedAuthority(roleName);

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(List.of(authority))
                .build();
    }
}
