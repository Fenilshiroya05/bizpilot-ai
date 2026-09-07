package com.bizpilot.identity.service;

import com.bizpilot.identity.entity.Role;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.exception.EmailAlreadyExistsException;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.organization.entity.Organization;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns user creation and lookup. Registration always assigns the
 * lowest-privilege role ({@link UserRole#EMPLOYEE}, looked up from the
 * Flyway-seeded {@code roles} table — see V4) and {@link UserStatus#ACTIVE}
 * status. The organization the new user belongs to is created by the caller
 * (see {@code security.AuthService}, which auto-provisions one per
 * registration) and passed in here — {@code UserService} doesn't decide
 * tenant assignment, and it doesn't decide role assignment beyond this one
 * fixed default (there is no self-service role picker; CLAUDE.md defines no
 * such flow).
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(String email, String rawPassword, String firstName, String lastName,
                          Organization organization) {
        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }

        Role defaultRole = roleRepository.findByName(UserRole.EMPLOYEE.name())
                .orElseThrow(() -> new IllegalStateException(
                        "RBAC seed data missing: role " + UserRole.EMPLOYEE.name() + " not found"));

        User user = new User(
                normalizedEmail,
                passwordEncoder.encode(rawPassword),
                firstName.trim(),
                lastName.trim(),
                UserStatus.ACTIVE,
                organization,
                defaultRole
        );
        try {
            // saveAndFlush (not save): forces the INSERT — and therefore the unique
            // constraint check — to happen synchronously here, so a concurrent
            // registration for the same email that won the race between the
            // existsByEmailIgnoreCase check above and this insert is still caught
            // and reported as 409, not a generic 500.
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw new EmailAlreadyExistsException(normalizedEmail);
        }
    }
}
