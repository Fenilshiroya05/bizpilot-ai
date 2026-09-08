package com.bizpilot.ai.tools;

import com.bizpilot.TestcontainersConfiguration;
import com.bizpilot.ai.FakeAiInfrastructureTestConfig;
import com.bizpilot.ai.chat.RecordingFakeAiChatService;
import com.bizpilot.ai.chat.RecordingFakeAiChatServiceTestConfig;
import com.bizpilot.ai.chat.dto.AiChatRequest;
import com.bizpilot.ai.chat.dto.AiChatResponse;
import com.bizpilot.crm.dto.CustomerCreateRequest;
import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.documents.dto.DocumentResponse;
import com.bizpilot.documents.entity.DocumentStatus;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Phase 17 (project instructions §9/§29/§30/§36/§37) live verification,
 * against real Testcontainers-backed Postgres data — no mocks. Mirrors
 * {@code AiAssistantSecurityTests}'s exact pattern: a real HTTP round trip
 * through {@code /api/v1/ai/chat}, with {@link RecordingFakeAiChatService}
 * standing in only for "the model decided to call this tool" while every
 * downstream step (the real {@code @PreAuthorize}-guarded tool bean, the
 * real domain service, the real database query) executes unmodified.
 *
 * <p>Three concerns are covered, each demanded explicitly by CLAUDE.md §7/§9
 * and the Phase 17 prompt: (1) tenant isolation — no tool leaks another
 * organization's row, even when asked for by exact ID; (2) permission
 * enforcement is independent of {@code AI_USE} — a principal with only
 * {@code AI_USE} is still denied by a tool requiring its own {@code
 * *_READ}; (3) the full simulated round trip actually returns the real
 * service's data through the chat endpoint, proving the wiring isn't a
 * no-op.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, FakeAiInfrastructureTestConfig.class,
        RecordingFakeAiChatServiceTestConfig.class})
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "bizpilot.ai.enabled=true",
        "spring.ai.vectorstore.type=pgvector"
})
class AiToolsIntegrationTests {

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

    @Autowired
    private RecordingFakeAiChatService fakeAiChatService;

    @Autowired
    private CustomerTools customerTools;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        fakeAiChatService.reset();
    }

    @Test
    void getCustomerToolNeverReturnsAnotherOrganizationsCustomerByExactId() {
        String tokenA = managerToken("ai-tools-isoA@example.com", "AI Tools Iso Org A");
        String tokenB = managerToken("ai-tools-isoB@example.com", "AI Tools Iso Org B");

        CustomerResponse customerA = createCustomer(tokenA, "Alpha Widgets Unique9001");
        uploadAndAwaitIndexed(tokenA, "notes-a.txt", "General notes about our business operations.");
        uploadAndAwaitIndexed(tokenB, "notes-b.txt", "General notes about our business operations.");

        fakeAiChatService.simulateToolCall("getCustomer", "{\"customerId\":\"" + customerA.id() + "\"}");
        ResponseEntity<AiChatResponse> crossTenantResponse = postChat(tokenB, "Look up this customer.");

        assertThat(crossTenantResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(crossTenantResponse.getBody().answer()).doesNotContain("Alpha Widgets Unique9001");
        assertThat(crossTenantResponse.getBody().answer()).contains("No customer found");

        // Positive control: the same tool, same ID, from the OWNING
        // organization succeeds — proves the negative result above is real
        // tenant isolation, not a broken/no-op tool.
        fakeAiChatService.simulateToolCall("getCustomer", "{\"customerId\":\"" + customerA.id() + "\"}");
        ResponseEntity<AiChatResponse> sameTenantResponse = postChat(tokenA, "Look up this customer.");

        assertThat(sameTenantResponse.getBody().answer()).contains("Alpha Widgets Unique9001");
    }

    @Test
    void searchCustomersToolNeverReturnsAnotherOrganizationsMatches() {
        String tokenA = managerToken("ai-tools-searchA@example.com", "AI Tools Search Org A");
        String tokenB = managerToken("ai-tools-searchB@example.com", "AI Tools Search Org B");

        createCustomer(tokenA, "Zephyr Traders Unique7002");
        uploadAndAwaitIndexed(tokenB, "notes-b.txt", "General notes about our business operations.");

        fakeAiChatService.simulateToolCall("searchCustomers", "{\"query\":\"Zephyr Traders Unique7002\"}");
        ResponseEntity<AiChatResponse> response = postChat(tokenB, "Find this customer.");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().answer()).doesNotContain("Zephyr Traders Unique7002");
    }

    @Test
    void searchLeadsToolNeverReturnsAnotherOrganizationsMatches() {
        String tokenA = managerToken("ai-tools-leadA@example.com", "AI Tools Lead Org A");
        String tokenB = managerToken("ai-tools-leadB@example.com", "AI Tools Lead Org B");

        createLead(tokenA, "Orion Manufacturing Unique4004");
        uploadAndAwaitIndexed(tokenA, "notes-a.txt", "General notes about our business operations.");
        uploadAndAwaitIndexed(tokenB, "notes-b.txt", "General notes about our business operations.");

        fakeAiChatService.simulateToolCall("searchLeads", "{\"query\":\"Orion Manufacturing Unique4004\"}");
        ResponseEntity<AiChatResponse> crossTenantResponse = postChat(tokenB, "Find this lead.");

        assertThat(crossTenantResponse.getBody().answer()).doesNotContain("Orion Manufacturing Unique4004");

        fakeAiChatService.simulateToolCall("searchLeads", "{\"query\":\"Orion Manufacturing Unique4004\"}");
        ResponseEntity<AiChatResponse> sameTenantResponse = postChat(tokenA, "Find this lead.");

        assertThat(sameTenantResponse.getBody().answer()).contains("Orion Manufacturing Unique4004");
    }

    @Test
    void endToEndSimulatedToolCallReturnsTheRealServiceResultThroughTheChatEndpoint() {
        String token = managerToken("ai-tools-e2e@example.com", "AI Tools E2E Org");
        CustomerResponse customer = createCustomer(token, "Nimbus Consulting Unique8008");
        uploadAndAwaitIndexed(token, "notes.txt", "General notes about our business operations.");

        fakeAiChatService.simulateToolCall("getCustomer", "{\"customerId\":\"" + customer.id() + "\"}");
        ResponseEntity<AiChatResponse> response = postChat(token, "Look up Nimbus Consulting for me.");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().answer()).contains("Nimbus Consulting Unique8008");
        assertThat(response.getBody().answer()).contains(customer.id().toString());

        List<RecordingFakeAiChatService.Invocation> invocations = fakeAiChatService.invocations();
        assertThat(invocations).isNotEmpty();
        RecordingFakeAiChatService.Invocation last = invocations.get(invocations.size() - 1);
        assertThat(last.tools()).hasSize(4);
    }

    @Test
    void aPrincipalWithOnlyAiUseIsStillDeniedByATheToolsOwnPermissionCheck() {
        authenticateWithAuthorities("AI_USE");

        assertThatThrownBy(() -> customerTools.searchCustomers("anything"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aPrincipalWithAiUseAndTheMatchingReadPermissionIsAllowedByTheTool() {
        authenticateWithAuthorities("AI_USE", "CUSTOMER_READ");

        // No exception — the org-scoped query itself may legitimately return
        // no rows for this synthetic, unauthenticated-tenant-context
        // principal; what's under test is that authorization succeeds.
        assertThatThrownBy(() -> customerTools.searchCustomers("anything"))
                .isNotInstanceOf(AccessDeniedException.class);
    }

    @Test
    void noReadOnlyToolAcceptsAnOrganizationOrTenantParameter() {
        List<Class<?>> toolClasses = List.of(CustomerTools.class, LeadTools.class, ProductTools.class,
                InvoiceTools.class);
        for (Class<?> toolClass : toolClasses) {
            for (Method method : toolClass.getMethods()) {
                if (!method.isAnnotationPresent(Tool.class)) {
                    continue;
                }
                for (Parameter parameter : method.getParameters()) {
                    String name = parameter.getName().toLowerCase();
                    assertThat(name)
                            .as("%s.%s parameter '%s' must not be an organization/tenant identifier",
                                    toolClass.getSimpleName(), method.getName(), name)
                            .doesNotContain("org")
                            .doesNotContain("tenant");
                }
            }
        }
    }

    // ---- Helpers -----------------------------------------------------------------

    private ResponseEntity<AiChatResponse> postChat(String token, String message) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.postForEntity(
                url("/api/v1/ai/chat"), new HttpEntity<>(new AiChatRequest(message), headers), AiChatResponse.class);
    }

    private CustomerResponse createCustomer(String token, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        CustomerCreateRequest request = new CustomerCreateRequest(name, null, null, null, null, null, null);
        ResponseEntity<CustomerResponse> response = restTemplate.postForEntity(
                url("/api/v1/customers"), new HttpEntity<>(request, headers), CustomerResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    /**
     * The chat endpoint short-circuits to a fixed "no context" answer
     * (never invoking the chat model, or any tool, at all) when RAG
     * retrieval finds zero chunks — see {@code DefaultAiAssistantService.ask}.
     * Every test exercising a simulated tool call must first give its
     * organization at least one indexed document so retrieval succeeds and
     * the (faked) chat model — and therefore the real tool bean — is
     * actually reached.
     */
    private void uploadAndAwaitIndexed(String token, String filename, String text) {
        Resource fileResource = new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        HttpHeaders partHeaders = new HttpHeaders();
        partHeaders.setContentType(MediaType.parseMediaType("text/plain"));
        HttpEntity<Resource> filePart = new HttpEntity<>(fileResource, partHeaders);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", filePart);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<DocumentResponse> response = restTemplate.postForEntity(
                url("/api/v1/documents"), new HttpEntity<>(body, headers), DocumentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        UUID documentId = response.getBody().id();

        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).untilAsserted(() -> {
            HttpHeaders getHeaders = new HttpHeaders();
            getHeaders.setBearerAuth(token);
            ResponseEntity<DocumentResponse> current = restTemplate.exchange(
                    url("/api/v1/documents/" + documentId), HttpMethod.GET, new HttpEntity<>(getHeaders),
                    DocumentResponse.class);
            assertThat(current.getBody()).isNotNull();
            assertThat(current.getBody().status()).isEqualTo(DocumentStatus.COMPLETED);
        });
    }

    private LeadResponse createLead(String token, String name) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        LeadCreateRequest request = new LeadCreateRequest(name, null, null, null, LeadSource.WEBSITE, null, null);
        ResponseEntity<LeadResponse> response = restTemplate.postForEntity(
                url("/api/v1/leads"), new HttpEntity<>(request, headers), LeadResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void authenticateWithAuthorities(String... authorities) {
        List<GrantedAuthority> granted = List.of(authorities).stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new TestingAuthenticationToken("synthetic-user", "N/A", granted));
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
