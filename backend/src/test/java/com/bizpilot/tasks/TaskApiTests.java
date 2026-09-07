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
import com.bizpilot.tasks.entity.TaskPriority;
import com.bizpilot.tasks.entity.TaskStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * End-to-end CRUD/validation/search/assignment coverage for the Phase 12
 * Task API, against a real PostgreSQL instance. Every test operates as a
 * MANAGER within a single organization — RBAC/tenant-isolation-specific
 * scenarios live in {@link TaskAuthorizationTests} and
 * {@link TaskTenantIsolationTests}. Mirrors {@code InvoiceApiTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TaskApiTests {

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

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void creatingATaskReturns201WithDefaultStatusAndPriority() {
        String token = managerToken("task12-creator@example.com", "Creator Co");

        TaskCreateRequest request = new TaskCreateRequest("Follow up", "Call the customer", null, null, null, null);
        ResponseEntity<TaskResponse> response = postWithToken("/api/v1/tasks", request, token, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        TaskResponse body = response.getBody();
        assertThat(body.status()).isEqualTo(TaskStatus.TODO);
        assertThat(body.priority()).isEqualTo(TaskPriority.MEDIUM);
        assertThat(body.assignedToUserId()).isNull();
        assertThat(body.title()).isEqualTo("Follow up");
    }

    @Test
    void creatingWithABlankTitleFailsValidation() {
        String token = managerToken("task12-blanktitle@example.com", "BlankTitle Co");

        TaskCreateRequest request = new TaskCreateRequest("   ", null, null, null, null, null);
        ResponseEntity<ApiError> response = postWithToken("/api/v1/tasks", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void clientSuppliedProtectedFieldsAreIgnored() {
        String token = managerToken("task12-massassign@example.com", "MassAssign Co");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        String bodyWithProtectedFields = "{"
                + "\"title\":\"Task title\","
                + "\"organizationId\":\"" + UUID.randomUUID() + "\","
                + "\"id\":\"" + UUID.randomUUID() + "\","
                + "\"status\":\"COMPLETED\","
                + "\"assignedToUserId\":\"" + UUID.randomUUID() + "\""
                + "}";

        ResponseEntity<TaskResponse> response = restTemplate.exchange(
                url("/api/v1/tasks"), HttpMethod.POST,
                new HttpEntity<>(bodyWithProtectedFields, headers), TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getBody().assignedToUserId()).isNull();
    }

    @Test
    void gettingANonexistentTaskReturns404() {
        String token = managerToken("task12-notfound@example.com", "NotFound Co");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/tasks/" + UUID.randomUUID(), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("TASK_NOT_FOUND");
    }

    @Test
    void updatingATaskAppliesSuppliedFields() {
        String token = managerToken("task12-update@example.com", "Update Co");
        TaskResponse created = createTask(token, "Original title");

        TaskUpdateRequest patch = new TaskUpdateRequest(
                "Updated title", null, TaskPriority.HIGH, null, false, null, false, null, false, null, null);
        ResponseEntity<TaskResponse> response = patchWithToken(
                "/api/v1/tasks/" + created.id(), patch, token, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().title()).isEqualTo("Updated title");
        assertThat(response.getBody().priority()).isEqualTo(TaskPriority.HIGH);
    }

    @Test
    void updatingWithStatusCancelledIsRejectedInFavorOfTheDedicatedCancelEndpoint() {
        String token = managerToken("task12-canceldirect@example.com", "CancelDirect Co");
        TaskResponse created = createTask(token, "Task");

        TaskUpdateRequest patch = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, false, TaskStatus.CANCELLED, null);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/tasks/" + created.id(), patch, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_TASK_DATA");
    }

    @Test
    void completingAndThenReopeningATaskSucceeds() {
        String token = managerToken("task12-reopen@example.com", "Reopen Co");
        TaskResponse created = createTask(token, "Task");

        TaskUpdateRequest complete = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, false, TaskStatus.COMPLETED, null);
        ResponseEntity<TaskResponse> completedResponse = patchWithToken(
                "/api/v1/tasks/" + created.id(), complete, token, TaskResponse.class);
        assertThat(completedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(completedResponse.getBody().status()).isEqualTo(TaskStatus.COMPLETED);

        TaskUpdateRequest reopen = new TaskUpdateRequest(
                null, null, null, null, false, null, false, null, false, TaskStatus.TODO, null);
        ResponseEntity<TaskResponse> reopenedResponse = patchWithToken(
                "/api/v1/tasks/" + created.id(), reopen, token, TaskResponse.class);

        assertThat(reopenedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(reopenedResponse.getBody().status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void assigningAndUnassigningATaskSucceeds() {
        String token = managerToken("task12-assign@example.com", "Assign Co");
        TaskResponse created = createTask(token, "Task");
        UUID assigneeUserId = userRepository.findByEmailIgnoreCase("task12-assign@example.com").orElseThrow().getId();

        ResponseEntity<TaskResponse> assignResponse = postWithToken(
                "/api/v1/tasks/" + created.id() + "/assign", new TaskAssignRequest(assigneeUserId), token,
                TaskResponse.class);
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignResponse.getBody().assignedToUserId()).isEqualTo(assigneeUserId);

        ResponseEntity<TaskResponse> unassignResponse = postWithToken(
                "/api/v1/tasks/" + created.id() + "/assign", new TaskAssignRequest(null), token, TaskResponse.class);
        assertThat(unassignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(unassignResponse.getBody().assignedToUserId()).isNull();
    }

    @Test
    void assigningToACrossOrgUserIsRejected() {
        String token = managerToken("task12-assignisoA@example.com", "Assign Iso A");
        String otherOrgToken = managerToken("task12-assignisoB@example.com", "Assign Iso B");
        TaskResponse created = createTask(token, "Task");
        UUID otherOrgUserId = userRepository.findByEmailIgnoreCase("task12-assignisoB@example.com").orElseThrow().getId();

        ResponseEntity<ApiError> response = postWithToken(
                "/api/v1/tasks/" + created.id() + "/assign", new TaskAssignRequest(otherOrgUserId), token,
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ASSIGNEE");
    }

    @Test
    void cancellingTransitionsToCancelledAndIsIdempotent() {
        String token = managerToken("task12-cancel@example.com", "Cancel Co");
        TaskResponse created = createTask(token, "Task");

        assertThat(deleteWithToken("/api/v1/tasks/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleteWithToken("/api/v1/tasks/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<TaskResponse> getResponse = getWithToken(
                "/api/v1/tasks/" + created.id(), token, TaskResponse.class);
        assertThat(getResponse.getBody().status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test
    void aCancelledTaskCanStillBeEditedAndReopened() {
        String token = managerToken("task12-editcancelled@example.com", "EditCancelled Co");
        TaskResponse created = createTask(token, "Task");
        deleteWithToken("/api/v1/tasks/" + created.id(), token);

        TaskUpdateRequest patch = new TaskUpdateRequest(
                "Reopened title", null, null, null, false, null, false, null, false, TaskStatus.TODO, null);
        ResponseEntity<TaskResponse> response = patchWithToken(
                "/api/v1/tasks/" + created.id(), patch, token, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(TaskStatus.TODO);
        assertThat(response.getBody().title()).isEqualTo("Reopened title");
    }

    @Test
    void creatingWithACustomerAndLeadReferenceSucceeds() {
        String token = managerToken("task12-refs@example.com", "Refs Co");
        UUID customerId = createCustomer(token, "Jane Customer").id();
        UUID leadId = createLead(token, "Jane Lead").id();

        TaskCreateRequest request = new TaskCreateRequest("Task with refs", null, null, null, customerId, leadId);
        ResponseEntity<TaskResponse> response = postWithToken("/api/v1/tasks", request, token, TaskResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().customerId()).isEqualTo(customerId);
        assertThat(response.getBody().leadId()).isEqualTo(leadId);
    }

    @Test
    void creatingWithANonexistentCustomerFailsValidation() {
        String token = managerToken("task12-badcustomer@example.com", "BadCustomer Co");

        TaskCreateRequest request = new TaskCreateRequest("Task", null, null, null, UUID.randomUUID(), null);
        ResponseEntity<ApiError> response = postWithToken("/api/v1/tasks", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_REFERENCE");
    }

    @Test
    void listingSupportsFilteringByStatusAndAssignee() {
        String token = managerToken("task12-filters@example.com", "Filters Co");
        TaskResponse taskA = createTask(token, "Task A");
        createTask(token, "Task B");
        UUID assigneeUserId = userRepository.findByEmailIgnoreCase("task12-filters@example.com").orElseThrow().getId();
        postWithToken("/api/v1/tasks/" + taskA.id() + "/assign", new TaskAssignRequest(assigneeUserId), token,
                TaskResponse.class);

        JsonNode byAssignee = listTasks(token, "assignedToUserId=" + assigneeUserId);
        assertThat(byAssignee.get("content")).hasSize(1);
        assertThat(byAssignee.get("content").get(0).get("id").asText()).isEqualTo(taskA.id().toString());

        JsonNode byStatus = listTasks(token, "status=TODO");
        assertThat(byStatus.get("totalElements").asInt()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void listingSupportsFreeTextSearchByTitle() {
        String token = managerToken("task12-search@example.com", "Search Co");
        createTask(token, "Prepare quarterly report");
        createTask(token, "Unrelated title");

        JsonNode results = listTasks(token, "q=quarterly");
        assertThat(results.get("content")).hasSize(1);
    }

    @Test
    void listingIsPaginatedAtTheDatabaseLevel() {
        String token = managerToken("task12-paginate@example.com", "Paginate Co");
        for (int i = 0; i < 5; i++) {
            createTask(token, "Task " + i);
        }

        JsonNode firstPage = listTasks(token, "page=0&size=2");

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isGreaterThanOrEqualTo(5);
    }

    // ---- Helpers -----------------------------------------------------------------

    private TaskResponse createTask(String token, String title) {
        TaskCreateRequest request = new TaskCreateRequest(title, null, null, null, null, null);
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

    private JsonNode listTasks(String token, String queryString) {
        ResponseEntity<String> response = getWithToken("/api/v1/tasks?" + queryString, token, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    private ResponseEntity<Void> deleteWithToken(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return restTemplate.exchange(url(path), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
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
