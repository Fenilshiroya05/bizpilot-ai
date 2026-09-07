package com.bizpilot.identity.service;

import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.exception.EmailAlreadyExistsException;
import com.bizpilot.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns user creation and lookup. Registration always assigns the
 * lowest-privilege role ({@link UserRole#EMPLOYEE}) and {@link UserStatus#ACTIVE}
 * status — proper role assignment/invite flows arrive with Organizations
 * (Phase 5) and full RBAC (Phase 6).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword, String firstName, String lastName) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                firstName.trim(),
                lastName.trim(),
                UserRole.EMPLOYEE,
                UserStatus.ACTIVE
        );
        return userRepository.save(user);
    }
}
