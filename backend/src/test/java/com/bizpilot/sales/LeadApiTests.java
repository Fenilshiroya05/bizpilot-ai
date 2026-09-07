package com.bizpilot.sales;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
import com.bizpilot.sales.dto.LeadActivityResponse;
import com.bizpilot.sales.dto.LeadAssignRequest;
import com.bizpilot.sales.dto.LeadCreateRequest;
import com.bizpilot.sales.dto.LeadNoteRequest;
import com.bizpilot.sales.dto.LeadResponse;
import com.bizpilot.sales.dto.LeadUpdateRequest;
import com.bizpilot.sales.entity.LeadActivityType;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.entity.LeadStatus;
import com.bizpilot.security.dto.AuthResponse;
import com.bizpilot.security.dto.LoginRequest;
import com.bizpilot.security.dto.RegisterRequest;
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
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end CRUD/validation/search coverage for the Phase 8 Lead API,
 * against a real PostgreSQL instance. Every test operates as a MANAGER (full
 * LEAD CRUD per the Phase 6 seeded mapping) within a single organization —
 * RBAC/tenant-isolation-specific scenarios live in
 * {@link LeadAuthorizationTests} and {@link LeadTenantIsolationTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class LeadApiTests {

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

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Test
    void creatingALeadReturns201WithLocationAndPersistsTheSuppliedFields() {
        String token = managerToken("creator@example.com", "Creator Co");
        LeadCreateRequest request = new LeadCreateRequest(
                "Jane Doe", "Acme Ltd", "jane@example.com", "+1 555 0100", LeadSource.WEBSITE, LeadPriority.HIGH, null);

        ResponseEntity<LeadResponse> response = postWithToken("/api/v1/leads", request, token, LeadResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        LeadResponse body = response.getBody();
        assertThat(body.name()).isEqualTo("Jane Doe");
        assertThat(body.status()).isEqualTo(LeadStatus.NEW);
        assertThat(body.source()).isEqualTo(LeadSource.WEBSITE);
        assertThat(body.priority()).isEqualTo(LeadPriority.HIGH);
        assertThat(body.assignedToUserId()).isNull();
        assertThat(body.archivedAt()).isNull();
    }

    @Test
    void creatingWithoutANameFailsValidation() {
        String token = managerToken("novalidname@example.com", "NoName Co");
        LeadCreateRequest request = new LeadCreateRequest("", null, null, null, LeadSource.OTHER, null, null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/leads", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void creatingWithoutASourceFailsValidation() {
        String token = managerToken("nosource@example.com", "NoSource Co");
        LeadCreateRequest request = new LeadCreateRequest("Someone", null, null, null, null, null, null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/leads", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingWithAnInvalidEmailFailsValidation() {
        String token = managerToken("bademail@example.com", "BadEmail Co");
        LeadCreateRequest request = new LeadCreateRequest(
                "Someone", null, "not-an-email", null, LeadSource.OTHER, null, null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/leads", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingWithoutAnExplicitPriorityDefaultsToMedium() {
        String token = managerToken("defaultpriority@example.com", "DefaultPriority Co");
        LeadCreateRequest request = new LeadCreateRequest("Someone", null, null, null, LeadSource.OTHER, null, null);

        ResponseEntity<LeadResponse> response = postWithToken("/api/v1/leads", request, token, LeadResponse.class);

        assertThat(response.getBody().priority()).isEqualTo(LeadPriority.MEDIUM);
    }

    @Test
    void malformedStatusValueInARequestBodyReturns400NotAServerError() {
        String token = managerToken("malformed@example.com", "Malformed Co");
        String leadId = createLead(token, "Someone Else").id().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String malformedBody = "{\"status\":\"NOT_A_REAL_STATUS\"}";

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/leads/" + leadId), HttpMethod.PATCH,
                new HttpEntity<>(malformedBody, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void gettingANonexistentLeadReturns404() {
        String token = managerToken("notfound@example.com", "NotFound Co");

        ResponseEntity<ApiError> response = getWithToken("/api/v1/leads/" + UUID.randomUUID(), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("LEAD_NOT_FOUND");
    }

    @Test
    void updatingAppliesOnlyTheSuppliedFieldsLeavingOthersUnchanged() {
        String token = managerToken("partial@example.com", "Partial Co");
        LeadResponse created = createLead(token, "Original Name");

        LeadUpdateRequest patch = new LeadUpdateRequest(
                "Updated Name", null, null, null, null, null, null, null, false);
        ResponseEntity<LeadResponse> response = patchWithToken(
                "/api/v1/leads/" + created.id(), patch, token, LeadResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Updated Name");
        assertThat(response.getBody().source()).isEqualTo(created.source());
    }

    @Test
    void updatingStatusRecordsAStatusChangedActivityVisibleInHistory() {
        String token = managerToken("statuschange@example.com", "StatusChange Co");
        LeadResponse created = createLead(token, "Status Target");

        LeadUpdateRequest patch = new LeadUpdateRequest(
                null, null, null, null, LeadStatus.QUALIFIED, null, null, null, false);
        ResponseEntity<LeadResponse> response = patchWithToken(
                "/api/v1/leads/" + created.id(), patch, token, LeadResponse.class);

        assertThat(response.getBody().status()).isEqualTo(LeadStatus.QUALIFIED);
        JsonNode history = listSubResource(created.id().toString(), "history", token);
        assertThat(contentTypes(history)).contains("STATUS_CHANGED");
    }

    @Test
    void clearingTheFollowUpDateRequiresTheExplicitFlag() {
        String token = managerToken("clearfollowup@example.com", "ClearFollowup Co");
        LeadCreateRequest createRequest = new LeadCreateRequest(
                "Someone", null, null, null, LeadSource.OTHER, null, LocalDate.now().plusDays(5));
        LeadResponse created = postWithToken("/api/v1/leads", createRequest, token, LeadResponse.class).getBody();
        assertThat(created.followUpDate()).isNotNull();

        LeadUpdateRequest clear = new LeadUpdateRequest(
                null, null, null, null, null, null, null, null, true);
        ResponseEntity<LeadResponse> response = patchWithToken(
                "/api/v1/leads/" + created.id(), clear, token, LeadResponse.class);

        assertThat(response.getBody().followUpDate()).isNull();
    }

    @Test
    void archivingReturns204AndExcludesTheLeadFromDefaultListing() {
        String token = managerToken("archiveflow@example.com", "ArchiveFlow Co");
        LeadResponse created = createLead(token, "To Be Archived");

        ResponseEntity<Void> archiveResponse = deleteWithToken("/api/v1/leads/" + created.id(), token);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode defaultListing = listLeads(token, "q=" + encode("To Be Archived"));
        assertThat(namesIn(defaultListing)).doesNotContain("To Be Archived");

        JsonNode archivedListing = listLeads(token, "archived=true&q=" + encode("To Be Archived"));
        assertThat(namesIn(archivedListing)).contains("To Be Archived");
    }

    @Test
    void archivingIsIdempotent() {
        String token = managerToken("archivetwice@example.com", "ArchiveTwice Co");
        LeadResponse created = createLead(token, "Archive Me Twice");

        assertThat(deleteWithToken("/api/v1/leads/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleteWithToken("/api/v1/leads/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void updatingAnArchivedLeadFails409() {
        String token = managerToken("archivedupdate@example.com", "ArchivedUpdate Co");
        LeadResponse created = createLead(token, "Archived Then Updated");
        deleteWithToken("/api/v1/leads/" + created.id(), token);

        LeadUpdateRequest patch = new LeadUpdateRequest(
                "New Name", null, null, null, null, null, null, null, false);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/leads/" + created.id(), patch, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("LEAD_ARCHIVED");
    }

    @Test
    void assigningToAValidUserInTheSameOrganizationSucceedsAndRecordsAnActivity() {
        String token = managerToken("assign@example.com", "Assign Co");
        LeadResponse created = createLead(token, "Assignable Lead");

        // Use the manager's own account id as a same-organization assignee,
        // since it is guaranteed to exist and belong to the correct org.
        UUID selfId = getSelf(token).id();
        ResponseEntity<LeadResponse> response = postWithToken(
                "/api/v1/leads/" + created.id() + "/assign", new LeadAssignRequest(selfId), token, LeadResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().assignedToUserId()).isEqualTo(selfId);

        JsonNode activities = listSubResource(created.id().toString(), "activities", token);
        assertThat(contentTypes(activities)).contains("ASSIGNED");
    }

    @Test
    void unassigningSetsAssignedToUserIdToNull() {
        String token = managerToken("unassign@example.com", "Unassign Co");
        LeadResponse created = createLead(token, "Unassign Target");
        UUID selfId = getSelf(token).id();
        postWithToken("/api/v1/leads/" + created.id() + "/assign", new LeadAssignRequest(selfId), token,
                LeadResponse.class);

        ResponseEntity<LeadResponse> response = postWithToken(
                "/api/v1/leads/" + created.id() + "/assign", new LeadAssignRequest(null), token, LeadResponse.class);

        assertThat(response.getBody().assignedToUserId()).isNull();
    }

    @Test
    void assigningToANonexistentUserFails400() {
        String token = managerToken("badassign@example.com", "BadAssign Co");
        LeadResponse created = createLead(token, "Bad Assign Target");

        ResponseEntity<ApiError> response = postWithToken(
                "/api/v1/leads/" + created.id() + "/assign", new LeadAssignRequest(UUID.randomUUID()), token,
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_ASSIGNEE");
    }

    @Test
    void listingSupportsFreeTextSearchAcrossNameCompanyAndEmail() {
        String token = managerToken("search@example.com", "Search Co");
        createLead(token, "Zebra Corp Contact");
        createLead(token, "Alpha Contact");

        JsonNode results = listLeads(token, "q=zebra");

        assertThat(namesIn(results)).containsExactly("Zebra Corp Contact");
    }

    @Test
    void listingSupportsFilteringByStatusSourceAndPriority() {
        String token = managerToken("filters@example.com", "Filters Co");
        LeadResponse highPriorityReferral = postWithToken("/api/v1/leads",
                new LeadCreateRequest("Filter Target", null, null, null, LeadSource.REFERRAL, LeadPriority.HIGH, null),
                token, LeadResponse.class).getBody();
        createLead(token, "Other Lead");

        JsonNode bySource = listLeads(token, "source=REFERRAL");
        assertThat(namesIn(bySource)).containsExactly("Filter Target");

        JsonNode byPriority = listLeads(token, "priority=HIGH");
        assertThat(namesIn(byPriority)).containsExactly("Filter Target");

        JsonNode byStatus = listLeads(token, "status=NEW");
        assertThat(namesIn(byStatus)).contains("Filter Target", "Other Lead");
    }

    @Test
    void listingIsPaginatedAtTheDatabaseLevel() {
        String token = managerToken("paginate@example.com", "Paginate Co");
        for (int i = 0; i < 5; i++) {
            createLead(token, "Paginated Lead " + i);
        }

        JsonNode firstPage = listLeads(token, "q=" + encode("Paginated Lead") + "&page=0&size=2");
        JsonNode secondPage = listLeads(token, "q=" + encode("Paginated Lead") + "&page=1&size=2");

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isEqualTo(5);
        assertThat(namesIn(firstPage)).doesNotContainAnyElementsOf(namesIn(secondPage));
    }

    @Test
    void addingANoteAppearsInNotesListAndInHistoryButNotInActivities() {
        String token = managerToken("notes@example.com", "Notes Co");
        LeadResponse created = createLead(token, "Note Target");

        ResponseEntity<LeadActivityResponse> noteResponse = postWithToken(
                "/api/v1/leads/" + created.id() + "/notes",
                new LeadNoteRequest("Called lead, follow up next week"), token, LeadActivityResponse.class);
        assertThat(noteResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(noteResponse.getBody().type()).isEqualTo(LeadActivityType.NOTE);

        JsonNode notes = listSubResource(created.id().toString(), "notes", token);
        JsonNode activities = listSubResource(created.id().toString(), "activities", token);
        JsonNode history = listSubResource(created.id().toString(), "history", token);

        assertThat(contentTypes(notes)).containsExactly("NOTE");
        assertThat(contentTypes(activities)).containsExactly("CREATED");
        assertThat(contentTypes(history)).contains("NOTE", "CREATED");
    }

    @Test
    void creatingALeadAutomaticallyRecordsACreatedActivityVisibleInHistory() {
        String token = managerToken("autohistory@example.com", "AutoHistory Co");
        LeadResponse created = createLead(token, "Auto History Target");

        JsonNode history = listSubResource(created.id().toString(), "history", token);

        assertThat(contentTypes(history)).contains("CREATED");
    }

    // ---- Helpers -----------------------------------------------------------------

    private LeadResponse createLead(String token, String name) {
        ResponseEntity<LeadResponse> response = postWithToken("/api/v1/leads",
                new LeadCreateRequest(name, null, null, null, LeadSource.OTHER, null, null), token, LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private com.bizpilot.identity.dto.UserResponse getSelf(String token) {
        ResponseEntity<com.bizpilot.identity.dto.UserResponse> response = getWithToken(
                "/api/v1/auth/me", token, com.bizpilot.identity.dto.UserResponse.class);
        return response.getBody();
    }

    private JsonNode listLeads(String token, String queryString) {
        ResponseEntity<String> response = getWithToken("/api/v1/leads?" + queryString, token, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode listSubResource(String leadId, String subResource, String token) {
        ResponseEntity<String> response = getWithToken(
                "/api/v1/leads/" + leadId + "/" + subResource, token, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> namesIn(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("content").forEach(node -> names.add(node.get("name").asText()));
        return names;
    }

    private List<String> contentTypes(JsonNode page) {
        List<String> types = new ArrayList<>();
        page.get("content").forEach(node -> types.add(node.get("type").asText()));
        return types;
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
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
