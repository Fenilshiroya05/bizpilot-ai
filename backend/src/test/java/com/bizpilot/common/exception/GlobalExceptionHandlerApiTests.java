package com.bizpilot.common.exception;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
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
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Production-readiness audit finding (post-Phase-19), a cross-cutting API
 * contract regression test: a malformed {@code @PathVariable UUID}
 * previously produced Spring Boot's own default error shape ({@code
 * {timestamp,status,error,path}}, no {@code code}/{@code message}) instead
 * of this project's own {@link ApiError} contract (CLAUDE.md §27) — now
 * fixed by a dedicated {@code MethodArgumentTypeMismatchException} handler
 * in {@code GlobalExceptionHandler}, which applies identically to every
 * controller (exercised once here, against a representative endpoint,
 * {@code /api/v1/customers} — not resource-specific behavior needing
 * per-module duplication).
 *
 * <p>An invalid {@code ?sort=} property (e.g. {@code
 * ?sort=nonExistentField}) was investigated during the same audit and found
 * to already fail safely: it surfaces as Hibernate's own {@code
 * IllegalArgumentException}/{@code UnknownPathException} (not Spring Data's
 * {@code PropertyReferenceException}, since every list query in this
 * codebase is a custom {@code @Query}, not a derived query method), caught
 * by the existing generic {@link GlobalExceptionHandler#handleUnexpected}
 * fallback — a {@code 500} with no internal detail exposed, confirmed by
 * the second test below. A dedicated handler was deliberately NOT added for
 * this case: narrowing on {@code IllegalArgumentException} specifically
 * would be unsafe, since that exception type is thrown throughout this
 * codebase for many unrelated reasons — a blanket handler for it risks
 * masking genuine bugs behind a falsely-reassuring {@code 400}, exactly the
 * anti-pattern this project's exception-handling discipline already avoids
 * elsewhere (see {@code GlobalExceptionHandler}'s own documented reasoning
 * for not adding a blanket {@code IllegalStateException} handler).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class GlobalExceptionHandlerApiTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void aMalformedUuidPathVariableReturnsTheStandardApiErrorShape() {
        String token = registerAndLogin("errorhandling-uuid@example.com", "Error Handling UUID Org");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/customers/not-a-uuid", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Invalid request");
        assertThat(response.getBody().path()).isEqualTo("/api/v1/customers/not-a-uuid");
    }

    @Test
    void anInvalidSortPropertyFailsSafelyWithNoInternalDetailExposed() {
        String token = registerAndLogin("errorhandling-sort@example.com", "Error Handling Sort Org");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/customers?sort=thisPropertyDoesNotExistOnCustomer", token);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        // The critical assertion: no Hibernate/SQL/entity-attribute detail
        // ever reaches the client, regardless of which internal exception
        // type the framework happens to throw for this case.
        assertThat(response.getBody().message()).doesNotContain("thisPropertyDoesNotExistOnCustomer", "Hibernate",
                "UnknownPathException", "Customer");
    }

    private ResponseEntity<ApiError> getWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);
    }

    private String registerAndLogin(String email, String organizationName) {
        restTemplate.postForEntity(url("/api/v1/auth/register"),
                new RegisterRequest(email, "Passw0rd!", "First", "Last", organizationName), Void.class);
        AuthResponse auth = restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class).getBody();
        return auth.accessToken();
    }
}
