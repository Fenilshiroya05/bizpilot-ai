package com.bizpilot.crm;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.common.response.ApiError;
import com.bizpilot.crm.dto.CustomerActivityResponse;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerNoteRequest;
import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.crm.dto.CustomerUpdateRequest;
import com.bizpilot.crm.entity.CustomerActivityType;
import com.bizpilot.crm.entity.CustomerStatus;
import com.bizpilot.identity.entity.User;
import com.bizpilot.identity.entity.UserRole;
import com.bizpilot.identity.repository.RoleRepository;
import com.bizpilot.identity.repository.UserRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end CRUD/validation/search coverage for the Phase 7 Customer API,
 * against a real PostgreSQL instance. Every test operates as a MANAGER (full
 * CUSTOMER CRUD per the Phase 6 seeded mapping) within a single organization
 * — RBAC/tenant-isolation-specific scenarios live in
 * {@link CustomerAuthorizationTests} and {@link CustomerTenantIsolationTests}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CustomerApiTests {

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
    void creatingACustomerReturns201WithLocationAndPersistsTheSuppliedFields() {
        String token = managerToken("creator@example.com", "Creator Co");
        CustomerCreateRequest request = new CustomerCreateRequest(
                "Jane Doe", "Acme Ltd", "jane@example.com", "+1 555 0100", "1 Main St", null, "VIP customer");

        ResponseEntity<CustomerResponse> response = postWithToken("/api/v1/customers", request, token, CustomerResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).isNotNull();
        CustomerResponse body = response.getBody();
        assertThat(body.name()).isEqualTo("Jane Doe");
        assertThat(body.status()).isEqualTo(CustomerStatus.ACTIVE);
        assertThat(body.email()).isEqualTo("jane@example.com");
    }

    @Test
    void creatingWithoutANameFailsValidation() {
        String token = managerToken("novalidname@example.com", "NoName Co");
        CustomerCreateRequest request = new CustomerCreateRequest(
                "", null, null, null, null, null, null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/customers", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void creatingWithAnInvalidEmailFailsValidation() {
        String token = managerToken("bademail@example.com", "BadEmail Co");
        CustomerCreateRequest request = new CustomerCreateRequest(
                "Someone", null, "not-an-email", null, null, null, null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/customers", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingWithAnInvalidGstinFailsValidation() {
        String token = managerToken("badgstin@example.com", "BadGstin Co");
        CustomerCreateRequest request = new CustomerCreateRequest(
                "Someone", null, null, null, null, "NOT-A-GSTIN", null);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/customers", request, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void creatingAValidGstinSucceeds() {
        String token = managerToken("goodgstin@example.com", "GoodGstin Co");
        CustomerCreateRequest request = new CustomerCreateRequest(
                "Someone", null, null, null, null, "27AAPFU0939F1ZV", null);

        ResponseEntity<CustomerResponse> response = postWithToken("/api/v1/customers", request, token, CustomerResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().gstin()).isEqualTo("27AAPFU0939F1ZV");
    }

    @Test
    void aPreviouslySetGstinCanBeClearedViaPatchWithAnEmptyString() {
        String token = managerToken("cleargstin@example.com", "ClearGstin Co");
        ResponseEntity<CustomerResponse> created = postWithToken("/api/v1/customers",
                new CustomerCreateRequest("Someone", null, null, null, null, "27AAPFU0939F1ZV", null),
                token, CustomerResponse.class);

        CustomerUpdateRequest clearGstin = new CustomerUpdateRequest(
                null, null, null, null, null, "", null, null);
        ResponseEntity<CustomerResponse> response = patchWithToken(
                "/api/v1/customers/" + created.getBody().id(), clearGstin, token, CustomerResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().gstin()).isNull();
    }

    @Test
    void malformedStatusValueInARequestBodyReturns400NotAServerError() {
        String token = managerToken("malformed@example.com", "Malformed Co");
        String customerId = createCustomer(token, "Someone Else").id().toString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        String malformedBody = "{\"status\":\"NOT_A_REAL_STATUS\"}";

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/v1/customers/" + customerId), HttpMethod.PATCH,
                new HttpEntity<>(malformedBody, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void creatingADuplicateEmailWithinTheSameOrganizationFails409() {
        String token = managerToken("dup@example.com", "Dup Co");
        postWithToken("/api/v1/customers", new CustomerCreateRequest(
                "First", null, "shared@example.com", null, null, null, null), token, CustomerResponse.class);

        ResponseEntity<ApiError> response = postWithToken("/api/v1/customers", new CustomerCreateRequest(
                "Second", null, "shared@example.com", null, null, null, null), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_CUSTOMER");
    }

    @Test
    void theSameEmailIsAllowedAcrossTwoDifferentOrganizations() {
        String tokenA = managerToken("orgA-dup@example.com", "Org A Dup Co");
        String tokenB = managerToken("orgB-dup@example.com", "Org B Dup Co");

        ResponseEntity<CustomerResponse> responseA = postWithToken("/api/v1/customers", new CustomerCreateRequest(
                "Contact", null, "shared-across-orgs@example.com", null, null, null, null), tokenA, CustomerResponse.class);
        ResponseEntity<CustomerResponse> responseB = postWithToken("/api/v1/customers", new CustomerCreateRequest(
                "Contact", null, "shared-across-orgs@example.com", null, null, null, null), tokenB, CustomerResponse.class);

        assertThat(responseA.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(responseB.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void gettingANonexistentCustomerReturns404() {
        String token = managerToken("notfound@example.com", "NotFound Co");

        ResponseEntity<ApiError> response = getWithToken(
                "/api/v1/customers/" + UUID.randomUUID(), token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo("CUSTOMER_NOT_FOUND");
    }

    @Test
    void updatingAppliesOnlyTheSuppliedFieldsLeavingOthersUnchanged() {
        String token = managerToken("partial@example.com", "Partial Co");
        CustomerResponse created = createCustomer(token, "Original Name");

        CustomerUpdateRequest patch = new CustomerUpdateRequest(
                "Updated Name", null, null, null, null, null, null, null);
        ResponseEntity<CustomerResponse> response = patchWithToken(
                "/api/v1/customers/" + created.id(), patch, token, CustomerResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Updated Name");
        assertThat(response.getBody().company()).isEqualTo(created.company());
    }

    @Test
    void updatingWithStatusArchivedIsRejectedInFavorOfTheDedicatedArchiveEndpoint() {
        String token = managerToken("archivedirect@example.com", "ArchiveDirect Co");
        CustomerResponse created = createCustomer(token, "Someone");

        CustomerUpdateRequest patch = new CustomerUpdateRequest(
                null, null, null, null, null, null, null, CustomerStatus.ARCHIVED);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/customers/" + created.id(), patch, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo("INVALID_CUSTOMER_DATA");
    }

    @Test
    void archivingReturns204AndExcludesTheCustomerFromDefaultListing() {
        String token = managerToken("archiveflow@example.com", "ArchiveFlow Co");
        CustomerResponse created = createCustomer(token, "To Be Archived");

        ResponseEntity<Void> archiveResponse = deleteWithToken("/api/v1/customers/" + created.id(), token);
        assertThat(archiveResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        JsonNode defaultListing = listCustomers(token, null, null);
        assertThat(namesIn(defaultListing)).doesNotContain("To Be Archived");

        JsonNode archivedListing = listCustomers(token, "ARCHIVED", null);
        assertThat(namesIn(archivedListing)).contains("To Be Archived");
    }

    @Test
    void archivingIsIdempotent() {
        String token = managerToken("archivetwice@example.com", "ArchiveTwice Co");
        CustomerResponse created = createCustomer(token, "Archive Me Twice");

        assertThat(deleteWithToken("/api/v1/customers/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(deleteWithToken("/api/v1/customers/" + created.id(), token).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void updatingAnArchivedCustomerFails409() {
        String token = managerToken("archivedupdate@example.com", "ArchivedUpdate Co");
        CustomerResponse created = createCustomer(token, "Archived Then Updated");
        deleteWithToken("/api/v1/customers/" + created.id(), token);

        CustomerUpdateRequest patch = new CustomerUpdateRequest(
                "New Name", null, null, null, null, null, null, null);
        ResponseEntity<ApiError> response = patchWithToken(
                "/api/v1/customers/" + created.id(), patch, token, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("CUSTOMER_ARCHIVED");
    }

    @Test
    void listingSupportsFreeTextSearchAcrossNameCompanyAndEmail() {
        String token = managerToken("search@example.com", "Search Co");
        createCustomer(token, "Zebra Corp Contact");
        createCustomer(token, "Alpha Contact");

        JsonNode results = listCustomers(token, null, "zebra");

        assertThat(namesIn(results)).containsExactly("Zebra Corp Contact");
    }

    @Test
    void listingIsPaginatedAtTheDatabaseLevel() {
        String token = managerToken("paginate@example.com", "Paginate Co");
        for (int i = 0; i < 5; i++) {
            createCustomer(token, "Paginated Customer " + i);
        }

        JsonNode firstPage = listCustomers(token, null, "Paginated Customer", 0, 2);
        JsonNode secondPage = listCustomers(token, null, "Paginated Customer", 1, 2);

        assertThat(firstPage.get("content")).hasSize(2);
        assertThat(firstPage.get("totalElements").asInt()).isEqualTo(5);
        assertThat(secondPage.get("content")).hasSize(2);
        assertThat(namesIn(firstPage)).doesNotContainAnyElementsOf(namesIn(secondPage));
    }

    @Test
    void addingANoteAppearsInNotesListAndInHistoryButNotInActivities() {
        String token = managerToken("notes@example.com", "Notes Co");
        CustomerResponse created = createCustomer(token, "Note Target");

        ResponseEntity<CustomerActivityResponse> noteResponse = postWithToken(
                "/api/v1/customers/" + created.id() + "/notes",
                new CustomerNoteRequest("Called customer, follow up next week"), token, CustomerActivityResponse.class);
        assertThat(noteResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(noteResponse.getBody().type()).isEqualTo(CustomerActivityType.NOTE);

        JsonNode notes = listSubResource(created.id().toString(), "notes", token);
        JsonNode activities = listSubResource(created.id().toString(), "activities", token);
        JsonNode history = listSubResource(created.id().toString(), "history", token);

        assertThat(contentTypes(notes)).containsExactly("NOTE");
        assertThat(contentTypes(activities)).containsExactly("CREATED");
        assertThat(contentTypes(history)).contains("NOTE", "CREATED");
    }

    @Test
    void creatingACustomerAutomaticallyRecordsACreatedActivityVisibleInHistory() {
        String token = managerToken("autohistory@example.com", "AutoHistory Co");
        CustomerResponse created = createCustomer(token, "Auto History Target");

        JsonNode history = listSubResource(created.id().toString(), "history", token);

        assertThat(contentTypes(history)).contains("CREATED");
    }

    @Test
    void archivingRecordsAnArchivedActivityVisibleInActivitiesAndHistory() {
        String token = managerToken("archiveactivity@example.com", "ArchiveActivity Co");
        CustomerResponse created = createCustomer(token, "Archive Activity Target");
        deleteWithToken("/api/v1/customers/" + created.id(), token);

        JsonNode activities = listSubResource(created.id().toString(), "activities", token);

        assertThat(contentTypes(activities)).contains("ARCHIVED");
    }

    // ---- Helpers -----------------------------------------------------------------

    private CustomerResponse createCustomer(String token, String name) {
        ResponseEntity<CustomerResponse> response = postWithToken("/api/v1/customers",
                new CustomerCreateRequest(name, null, null, null, null, null, null), token, CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private JsonNode listCustomers(String token, String status, String q) {
        return listCustomers(token, status, q, null, null);
    }

    private JsonNode listCustomers(String token, String status, String q, Integer page, Integer size) {
        StringBuilder path = new StringBuilder("/api/v1/customers?");
        if (status != null) {
            path.append("status=").append(status).append("&");
        }
        if (q != null) {
            path.append("q=").append(q).append("&");
        }
        if (page != null) {
            path.append("page=").append(page).append("&");
        }
        if (size != null) {
            path.append("size=").append(size).append("&");
        }
        ResponseEntity<String> response = getWithToken(path.toString(), token, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        try {
            return objectMapper.readTree(response.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode listSubResource(String customerId, String subResource, String token) {
        ResponseEntity<String> response = getWithToken(
                "/api/v1/customers/" + customerId + "/" + subResource, token, String.class);
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
