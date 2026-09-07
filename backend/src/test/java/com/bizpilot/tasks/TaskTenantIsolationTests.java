package com.bizpilot.tasks;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.sales.dto.LeadCreateRequest;
import com.bizpilot.sales.dto.LeadResponse;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
import com.bizpilot.tasks.dto.TaskAssignRequest;
import com.bizpilot.tasks.dto.TaskCreateRequest;
import com.bizpilot.tasks.dto.TaskResponse;
import com.bizpilot.tasks.dto.TaskUpdateRequest;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mandatory Phase 12 tenant-isolation / IDOR tests — mirrors
 * {@code InvoiceTenantIsolationTests}. An authenticated user from
 * Organization A must never read, modify, assign, or cancel Organization
 * B's tasks, and must never be able to attach Organization B's assignee,
 * customer, or lead to a task of their own.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TaskTenantIsolationTests {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void organizationACannotReadOrganizationBsTask() {
        String tokenA = managerToken("task-isoA-read@example.com", "Iso Org A Read");
        String tokenB = managerToken("task-isoB-read@example.com", "Iso Org B Read");
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken("/api/v1/tasks/" + taskB.id(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void organizationACannotUpdateOrganizationBsTask() {
        String tokenA = managerToken("task-isoA-update@example.com", "Iso Org A Update");
        String tokenB = managerToken("task-isoB-update@example.com", "Iso Org B Update");
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        TaskUpdateRequest patch = new TaskUpdateRequest(
                "Hijacked title", null, null, null, false, null, false, null, false, null, null);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/tasks/" + taskB.id()), HttpMethod.PATCH,
                new HttpEntity<>(patch, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotCancelOrganizationBsTask() {
        String tokenA = managerToken("task-isoA-cancel@example.com", "Iso Org A Cancel");
        String tokenB = managerToken("task-isoB-cancel@example.com", "Iso Org B Cancel");
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/tasks/" + taskB.id()), HttpMethod.DELETE,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationACannotAssignOrganizationBsTask() {
        String tokenA = managerToken("task-isoA-assign@example.com", "Iso Org A Assign");
        String tokenB = managerToken("task-isoB-assign@example.com", "Iso Org B Assign");
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/tasks/" + taskB.id() + "/assign"), HttpMethod.POST,
                new HttpEntity<>(new TaskAssignRequest(null), headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void organizationAsTaskListingNeverIncludesOrganizationBsTasks() {
        String tokenA = managerToken("task-isoA-listing@example.com", "Iso Org A Listing");
        String tokenB = managerToken("task-isoB-listing@example.com", "Iso Org B Listing");
        TaskResponse taskA = createTaskForOwnOrg(tokenA);
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        ResponseEntity<String> response = getWithToken("/api/v1/tasks", tokenA, String.class);

        assertThat(response.getBody()).contains(taskA.id().toString());
        assertThat(response.getBody()).doesNotContain(taskB.id().toString());
    }

    @Test
    void organizationACannotAssignATaskToAnOrganizationBUser() {
        String tokenA = managerToken("task-isoA-assignee@example.com", "Iso Org A Assignee");
        String tokenB = managerToken("task-isoB-assignee@example.com", "Iso Org B Assignee");
        TaskResponse taskA = createTaskForOwnOrg(tokenA);
        UUID userB = userRepository.findByEmailIgnoreCase("task-isoB-assignee@example.com").orElseThrow().getId();

        ResponseEntity<ApiError> response = postWithToken(
                "/api/v1/tasks/" + taskA.id() + "/assign", new TaskAssignRequest(userB), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ASSIGNEE");
    }

    @Test
    void organizationACannotAttachACustomerFromOrganizationBToItsOwnTask() {
        String tokenA = managerToken("task-isoA-customer@example.com", "Iso Org A Customer");
        String tokenB = managerToken("task-isoB-customer@example.com", "Iso Org B Customer");
        UUID customerB = createCustomer(tokenB, "Org B Customer").id();

        TaskCreateRequest request = new TaskCreateRequest("Task", null, null, null, customerB, null);
        ResponseEntity<ApiError> response = postWithToken("/api/v1/tasks", request, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_REFERENCE");
    }

    @Test
    void organizationACannotAttachALeadFromOrganizationBToItsOwnTask() {
        String tokenA = managerToken("task-isoA-lead@example.com", "Iso Org A Lead");
        String tokenB = managerToken("task-isoB-lead@example.com", "Iso Org B Lead");
        UUID leadB = createLead(tokenB, "Org B Lead").id();

        TaskCreateRequest request = new TaskCreateRequest("Task", null, null, null, null, leadB);
        ResponseEntity<ApiError> response = postWithToken("/api/v1/tasks", request, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_LEAD_REFERENCE");
    }

    @Test
    void organizationACannotReassignATaskToACustomerFromOrganizationBViaUpdate() {
        String tokenA = managerToken("task-isoA-reassign@example.com", "Iso Org A Reassign");
        String tokenB = managerToken("task-isoB-reassign@example.com", "Iso Org B Reassign");
        TaskResponse taskA = createTaskForOwnOrg(tokenA);
        UUID customerB = createCustomer(tokenB, "Org B Customer").id();

        TaskUpdateRequest patch = new TaskUpdateRequest(
                null, null, null, null, false, customerB, false, null, false, null, null);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/tasks/" + taskA.id(), patch, tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_REFERENCE");
    }

    @Test
    void aClientCannotSmuggleAnotherOrganizationIdThroughTheCreateRequestBody() {
        String tokenA = managerToken("task-isoA-mass-assign@example.com", "Iso Org A Mass Assign");
        String tokenB = managerToken("task-isoB-mass-assign@example.com", "Iso Org B Mass Assign");
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String bodyWithSmuggledOrgId = "{\"title\":\"Task\","
                + "\"organizationId\":\"" + taskB.organizationId() + "\"}";

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
                url("/api/v1/tasks"), HttpMethod.POST,
                new HttpEntity<>(bodyWithSmuggledOrgId, headers), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().organizationId()).isNotEqualTo(taskB.organizationId());
    }

    @Test
    void aClientCannotOverrideTheTenantViaAQueryParameterWhenReadingAnotherOrganizationsTask() {
        String tokenA = managerToken("task-isoA-query@example.com", "Iso Org A Query");
        String tokenB = managerToken("task-isoB-query@example.com", "Iso Org B Query");
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/tasks/" + taskB.id() + "?organizationId=" + taskB.organizationId(), tokenA, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void aClientCannotOverrideTheTenantViaAHeaderWhenReadingAnotherOrganizationsTask() {
        String tokenA = managerToken("task-isoA-header@example.com", "Iso Org A Header");
        String tokenB = managerToken("task-isoB-header@example.com", "Iso Org B Header");
        TaskResponse taskB = createTaskForOwnOrg(tokenB);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokenA);
        headers.set("X-Organization-Id", taskB.organizationId().toString());

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/tasks/" + taskB.id()), HttpMethod.GET,
                new HttpEntity<>(headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ---- Helpers -----------------------------------------------------------------

    private TaskResponse createTaskForOwnOrg(String token) {
        TaskCreateRequest request = new TaskCreateRequest("Task " + UUID.randomUUID(), null, null, null, null, null);
        ResponseEntity<TaskResponse> response = postWithToken("/api/v1/tasks", request, token, TaskResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private CustomerResponse createCustomer(String token, String name) {
        ResponseEntity<CustomerResponse> response = postWithToken("/api/v1/customers",
                new CustomerCreateRequest(name, null, null, null, null, null, null), token, CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private LeadResponse createLead(String token, String name) {
        ResponseEntity<LeadResponse> response = postWithToken("/api/v1/leads",
                new LeadCreateRequest(name, null, null, null, LeadSource.WEBSITE, null, null), token,
                LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private <T> ResponseEntity<T> getWithToken(String path, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), responseType);
    }

    private <T> ResponseEntity<T> postWithToken(String path, Object body, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.POST, new HttpEntity<>(body, headers), responseType);
    }

    private <T> ResponseEntity<T> patchWithToken(String path, Object body, String token, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.PATCH, new HttpEntity<>(body, headers), responseType);
    }

    private String managerToken(String email, String organizationName) {
        register(email, organizationName);
        promoteToManager(email);
        return login(email).accessToken();
    }

    private void register(String email, String organizationName) {
        restTemplate.postForEntity(url("/api/v1/auth/register"),
                new RegisterRequest(email, "Passw0rd!", "First", "Last", organizationName), Void.class);
    }

    private AuthResponse login(String email) {
        return restTemplate.postForEntity(
                url("/api/v1/auth/login"), new LoginRequest(email, "Passw0rd!"), AuthResponse.class).getBody();
    }

    private void promoteToManager(String email) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            user.getRoles().clear();
            user.getRoles().add(roleRepository.findByName(UserRole.MANAGER.name()).orElseThrow());
            userRepository.saveAndFlush(user);
        });
    }
}
