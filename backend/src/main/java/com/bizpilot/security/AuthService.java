package com.bizpilot.security;

import com.bizpilot.identity.dto.UserResponse;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.mapper.UserMapper;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.identity.service.UserService;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RefreshTokenRequest;
import com.bizpilot.security.dto.RegisterRequest;
import com.bizpilot.security.exception.AccountNotActiveException;
import com.bizpilot.security.exception.InvalidCredentialsException;
import com.bizpilot.security.jwt.JwtService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates the authentication flow: registration, login, refresh, and
 * logout. Login is verified manually (not via {@code AuthenticationManager})
 * so the check order is guaranteed: password first, account status second —
 * this way, account status is never revealed to someone who doesn't already
 * know the password (see docs/security.md).
 */
@Service
public class AuthService {

    // Computed once, from a fixed non-secret string. Used only so that a
    // "user not found" login attempt performs a real BCrypt comparison of
    // similar cost to a real one, preventing a timing side-channel that
    // would otherwise reveal whether an email is registered.
    private static final String DUMMY_PASSWORD_HASH =
            new BCryptPasswordEncoder().encode("dummy-password-for-constant-time-comparison");

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, UserService userService, UserMapper userMapper,
                        PasswordEncoder passwordEncoder, JwtService jwtService,
                        RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        User user = userService.register(request.email(), request.password(), request.firstName(), request.lastName());
        return userMapper.toResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Optional<User> maybeUser = userRepository.findByEmailIgnoreCase(request.email().trim());
        String hashToVerifyAgainst = maybeUser.map(User::getPasswordHash).orElse(DUMMY_PASSWORD_HASH);
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToVerifyAgainst);

        if (maybeUser.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        User user = maybeUser.get();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException(user.getStatus());
        }

        String rawRefreshToken = refreshTokenService.issue(user);
        return buildAuthResponse(user, rawRefreshToken);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(request.refreshToken());
        return buildAuthResponse(rotated.user(), rotated.rawToken());
    }

    @Transactional
    public void logout(RefreshTokenRequest request, UUID currentUserId) {
        refreshTokenService.revoke(request.refreshToken(), currentUserId);
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists: " + userId));
        return userMapper.toResponse(user);
    }

    private AuthResponse buildAuthResponse(User user, String rawRefreshToken) {
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        long expiresInSeconds = jwtService.getAccessTokenTtl().toSeconds();
        return AuthResponse.of(accessToken, rawRefreshToken, expiresInSeconds, userMapper.toResponse(user));
    }
}
