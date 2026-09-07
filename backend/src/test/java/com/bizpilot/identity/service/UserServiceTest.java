package com.bizpilot.identity.service;

import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.exception.EmailAlreadyExistsException;
import com.bizpilot.identity.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService() {
        return new UserService(userRepository, passwordEncoder);
    }

    @Test
    void registrationHashesThePasswordAndNeverStoresItRaw() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode("MyPassw0rd")).thenReturn("bcrypt-hashed-value");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService().register("New.User@Example.com", "MyPassw0rd", "New", "User");

        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hashed-value");
        assertThat(user.getPasswordHash()).doesNotContain("MyPassw0rd");
        verify(passwordEncoder).encode("MyPassw0rd");
    }

    @Test
    void registrationNormalizesEmailToLowercaseAndTrims() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        when(userRepository.saveAndFlush(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        userService().register("New.User@Example.com", "MyPassw0rd", " New ", " User ");

        assertThat(captor.getValue().getEmail()).isEqualTo("new.user@example.com");
        assertThat(captor.getValue().getFirstName()).isEqualTo("New");
        assertThat(captor.getValue().getLastName()).isEqualTo("User");
    }

    @Test
    void registrationDefaultsToEmployeeRoleAndActiveStatus() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService().register("a@example.com", "MyPassw0rd", "A", "B");

        assertThat(user.getRole()).isEqualTo(UserRole.EMPLOYEE);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void rejectsRegistrationWithDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService().register("taken@example.com", "MyPassw0rd", "A", "B"))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsRegistrationAsDuplicateEmailWhenAConcurrentRegistrationWinsTheDatabaseRace() {
        // existsByEmailIgnoreCase says "free" (race: another request hasn't committed
        // yet), but the DB unique constraint catches it at flush time regardless.
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertThatThrownBy(() -> userService().register("raced@example.com", "MyPassw0rd", "A", "B"))
                .isInstanceOf(EmailAlreadyExistsException.class);
    }
}
