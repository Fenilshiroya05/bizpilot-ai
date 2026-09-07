package com.bizpilot.security;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.entity.UserStatus;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RefreshTokenRequest;
import com.bizpilot.security.dto.RegisterRequest;
import com.bizpilot.security.jwt.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class AuthenticationFlowTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    // ---- Registration ----------------------------------------------------

    @Test
    void registerWithValidInputCreatesUserAndNeverReturnsThePasswordHash() {
        RegisterRequest request = new RegisterRequest("alice@example.com", "Passw0rd!", "Alice", "Smith");

        ResponseEntity<String> response = restTemplate.postForEntity(url("/api/v1/auth/register"), request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).contains("\"email\":\"alice@example.com\"");
        assertThat(response.getBody()).doesNotContain("Passw0rd!");
        assertThat(response.getBody()).doesNotContain("passwordHash");
        assertThat(response.getBody()).doesNotContain("password");
    }

    @Test
    void registerWithDuplicateEmailReturns409() {
        RegisterRequest request = new RegisterRequest("bob@example.com", "Passw0rd!", "Bob", "Jones");
        restTemplate.postForEntity(url("/api/v1/auth/register"), request, String.class);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(url("/api/v1/auth/register"), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("EMAIL_ALREADY_EXISTS");
    }

    @Test
    void registerWithWeakPasswordReturns400ValidationError() {
        RegisterRequest request = new RegisterRequest("weakpass@example.com", "short", "A", "B");

        ResponseEntity<ApiError> response = restTemplate.postForEntity(url("/api/v1/auth/register"), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    // ---- Login -------------------------------------------------------------

    @Test
    void loginWithValidCredentialsReturnsAccessAndRefreshTokens() {
        registerDirect("carol@example.com", "Passw0rd!", "Carol", "Lee", UserRole.EMPLOYEE, UserStatus.ACTIVE);

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("carol@example.com", "Passw0rd!"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().accessToken()).isNotBlank();
        assertThat(response.getBody().refreshToken()).isNotBlank();
        assertThat(response.getBody().tokenType()).isEqualTo("Bearer");
        assertThat(response.getBody().user().email()).isEqualTo("carol@example.com");
    }

    @Test
    void loginWithWrongPasswordAndLoginWithUnknownEmailReturnTheSameGenericError() {
        registerDirect("dave@example.com", "Passw0rd!", "Dave", "King", UserRole.EMPLOYEE, UserStatus.ACTIVE);

        ResponseEntity<ApiError> wrongPassword = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("dave@example.com", "WrongPassw0rd!"), ApiError.class);
        ResponseEntity<ApiError> unknownEmail = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("no-such-user@example.com", "Whatever1!"), ApiError.class);

        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unknownEmail.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(wrongPassword.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknownEmail.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(wrongPassword.getBody().message()).isEqualTo(unknownEmail.getBody().message());
    }

    @Test
    void loginWithDisabledAccountReturns403AfterPasswordIsVerified() {
        registerDirect("erin@example.com", "Passw0rd!", "Erin", "Moss", UserRole.EMPLOYEE, UserStatus.DISABLED);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("erin@example.com", "Passw0rd!"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void loginWithLockedAccountReturns403AfterPasswordIsVerified() {
        registerDirect("frank@example.com", "Passw0rd!", "Frank", "Cole", UserRole.EMPLOYEE, UserStatus.LOCKED);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("frank@example.com", "Passw0rd!"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void loginWithDisabledAccountAndWrongPasswordStillReturnsGenericInvalidCredentials() {
        // A disabled account's status must never leak to someone who doesn't know the password.
        registerDirect("grace@example.com", "Passw0rd!", "Grace", "Wu", UserRole.EMPLOYEE, UserStatus.DISABLED);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("grace@example.com", "WrongPassw0rd!"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CREDENTIALS");
    }

    // ---- /me (current user / protected endpoint) ---------------------------

    @Test
    void meWithoutTokenReturns401() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/v1/auth/me"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void meWithMalformedTokenReturns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer not-a-real-jwt");
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void meWithValidTokenReturnsCurrentUserProfile() {
        User user = registerDirect("heidi@example.com", "Passw0rd!", "Heidi", "Fox", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/auth/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"email\":\"heidi@example.com\"");
        assertThat(response.getBody()).doesNotContain("password");
    }

    // ---- Refresh / rotation / reuse detection -------------------------------

    @Test
    void refreshWithValidTokenRotatesAndReturnsNewTokens() {
        registerDirect("ivan@example.com", "Passw0rd!", "Ivan", "Petrov", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        AuthResponse loginResponse = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("ivan@example.com", "Passw0rd!"), AuthResponse.class).getBody();

        ResponseEntity<AuthResponse> refreshed = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(loginResponse.refreshToken()), AuthResponse.class);

        assertThat(refreshed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(refreshed.getBody().refreshToken()).isNotEqualTo(loginResponse.refreshToken());
        assertThat(refreshed.getBody().accessToken()).isNotBlank();
    }

    @Test
    void refreshingWithAStillValidTokenAfterTheAccountIsDisabledIsRejected() {
        User user = registerDirect("nadia@example.com", "Passw0rd!", "Nadia", "Rios", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        AuthResponse loginResponse = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("nadia@example.com", "Passw0rd!"), AuthResponse.class).getBody();

        // Simulate an admin disabling the account after the session was already issued.
        user.setStatus(UserStatus.DISABLED);
        userRepository.saveAndFlush(user);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(loginResponse.refreshToken()), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void reusingARotatedRefreshTokenIsRejectedAndRevokesTheWholeSession() {
        registerDirect("judy@example.com", "Passw0rd!", "Judy", "Diaz", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        AuthResponse loginResponse = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("judy@example.com", "Passw0rd!"), AuthResponse.class).getBody();
        String originalRefreshToken = loginResponse.refreshToken();

        AuthResponse firstRefresh = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(originalRefreshToken), AuthResponse.class).getBody();

        // Reuse the already-rotated (now-revoked) original token.
        ResponseEntity<ApiError> reuseAttempt = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(originalRefreshToken), ApiError.class);
        assertThat(reuseAttempt.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(reuseAttempt.getBody().code()).isEqualTo("INVALID_REFRESH_TOKEN");

        // The legitimately-rotated token must ALSO now be revoked (whole-family revocation).
        ResponseEntity<ApiError> useLegitimateNewToken = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(firstRefresh.refreshToken()), ApiError.class);
        assertThat(useLegitimateNewToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refreshWithAnUnknownTokenReturns401() {
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest("completely-made-up-token"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().code()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    // ---- Logout --------------------------------------------------------------

    @Test
    void logoutRevokesTheRefreshTokenSoItCanNoLongerBeUsed() {
        registerDirect("kevin@example.com", "Passw0rd!", "Kevin", "Ng", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        AuthResponse loginResponse = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest("kevin@example.com", "Passw0rd!"), AuthResponse.class).getBody();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(loginResponse.accessToken());
        ResponseEntity<Void> logoutResponse = restTemplate.exchange(
                url("/api/v1/auth/logout"), HttpMethod.POST,
                new HttpEntity<>(new RefreshTokenRequest(loginResponse.refreshToken()), headers), Void.class);
        assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiError> refreshAfterLogout = restTemplate.postForEntity(
                url("/api/v1/auth/refresh"), new RefreshTokenRequest(loginResponse.refreshToken()), ApiError.class);
        assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- Role-based authorization (method security) ---------------------------

    @Test
    void adminOnlyEndpointAllowsAdminRole() {
        User admin = registerDirect("laura@example.com", "Passw0rd!", "Laura", "Byrne", UserRole.ADMIN, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(admin.getId(), admin.getRole());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/v1/test/admin-only"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void adminOnlyEndpointForbidsNonAdminRole() {
        User employee = registerDirect("mallory@example.com", "Passw0rd!", "Mallory", "Cruz", UserRole.EMPLOYEE, UserStatus.ACTIVE);
        String token = jwtService.generateAccessToken(employee.getId(), employee.getRole());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/test/admin-only"), HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void adminOnlyEndpointRejectsUnauthenticatedRequests() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/v1/test/admin-only"), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- Helpers ---------------------------------------------------------------

    private User registerDirect(String email, String rawPassword, String firstName, String lastName,
                                 UserRole role, UserStatus status) {
        User user = new User(email, passwordEncoder.encode(rawPassword), firstName, lastName, role, status);
        return userRepository.save(user);
    }
}
