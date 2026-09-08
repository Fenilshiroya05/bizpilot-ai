package com.bizpilot.ai.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mandatory Phase 17 pre-implementation spike (project instructions §4):
 * proves — empirically, not by inspecting annotations — whether {@code
 * @PreAuthorize} is enforced when a tool is invoked the exact way Spring
 * AI's own tool-calling machinery invokes it: via {@link
 * MethodToolCallbackProvider}, wrapping the real Spring-managed (proxied)
 * bean instance obtained from the application context, then calling {@link
 * ToolCallback#call(String)} — never by calling the method directly in Java.
 *
 * <p>This is deliberately a lightweight {@code @SpringJUnitConfig} context
 * (no Testcontainers/database) — only {@code @EnableMethodSecurity}'s AOP
 * infrastructure and a single test-only {@code @Component} tool bean are
 * needed to answer the question this spike exists to answer.
 *
 * <p><b>Result, recorded here so it never needs re-deriving</b>: {@code
 * @PreAuthorize} IS enforced. {@link MethodToolCallbackProvider} wraps the
 * exact bean instance passed to it (the real Spring-managed/CGLIB-proxied
 * instance when obtained from the {@code ApplicationContext}, as it always
 * is via constructor injection); invoking the resulting {@link
 * ToolCallback#call(String)} calls the method through that same proxy, so
 * Spring Security's method-security interceptor fires exactly as it does
 * for any other proxied bean method call (identical to how {@code
 * @Transactional} already works on every existing {@code *Service} class in
 * this project). See {@code docs/security.md} for the full account.
 */
@SpringJUnitConfig(ToolSecurityEnforcementTest.TestConfig.class)
class ToolSecurityEnforcementTest {

    @Configuration
    @EnableMethodSecurity
    public static class TestConfig {

        @Bean
        public SpikeTestTool spikeTestTool() {
            return new SpikeTestTool();
        }
    }

    /**
     * A minimal stand-in for the real {@code CustomerTools} shape — a
     * plain, no-interface {@code @Component} (so Spring uses a CGLIB
     * proxy, exactly like every existing {@code *Service} in this
     * project), one {@code @Tool} + {@code @PreAuthorize}-annotated method.
     *
     * <p>Must be {@code public} (not just its method) — Spring AI's
     * reflective invocation requires the declaring class itself to be
     * accessible across the package boundary, not merely the method.
     */
    public static class SpikeTestTool {

        @Tool(description = "Returns a fixed test value; requires SPIKE_TEST_READ.")
        @PreAuthorize("hasAuthority('SPIKE_TEST_READ')")
        public String readSpikeValue() {
            return "spike-value";
        }
    }

    @Autowired
    private SpikeTestTool spikeTestTool;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void toolCallSucceedsWhenTheAuthenticatedPrincipalHasTheRequiredAuthority() {
        authenticateWithAuthorities("SPIKE_TEST_READ");

        ToolCallback callback = onlyToolCallbackFrom(spikeTestTool);
        String result = callback.call("{}");

        assertThat(result).contains("spike-value");
    }

    @Test
    void toolCallIsDeniedWhenTheAuthenticatedPrincipalLacksTheRequiredAuthority() {
        authenticateWithAuthorities("SOME_OTHER_PERMISSION");

        ToolCallback callback = onlyToolCallbackFrom(spikeTestTool);

        // Spring AI's own MethodToolCallback.callMethod wraps whatever the
        // invoked method (or, here, the @PreAuthorize interceptor guarding
        // it) throws into a ToolExecutionException — verified empirically,
        // not assumed; this exact wrapping shape is what
        // ToolExecutionExceptionProcessor (see AiToolExecutionConfig) is
        // written against.
        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(org.springframework.ai.tool.execution.ToolExecutionException.class)
                .cause().isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void toolCallIsDeniedWhenThereIsNoAuthenticationAtAll() {
        SecurityContextHolder.clearContext();

        ToolCallback callback = onlyToolCallbackFrom(spikeTestTool);

        // A missing Authentication manifests as AuthenticationCredentialsNotFoundException
        // (a *different* hierarchy from AccessDeniedException — verified,
        // not assumed) — still fails closed either way. In the real
        // application this specific case is unreachable for
        // /api/v1/ai/chat (Spring Security's filter chain already requires
        // a valid, authenticated request before the controller — and
        // therefore any tool call — is ever reached); AiToolExecutionConfig
        // still treats both hierarchies identically, defensively.
        assertThatThrownBy(() -> callback.call("{}"))
                .isInstanceOf(org.springframework.ai.tool.execution.ToolExecutionException.class)
                .cause().isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    private static ToolCallback onlyToolCallbackFrom(Object toolBean) {
        // The exact mechanism Spring AI's ChatClient.tools(Object...) uses
        // internally to turn an annotated POJO into invocable ToolCallbacks.
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(toolBean).build()
                .getToolCallbacks();
        assertThat(callbacks).hasSize(1);
        return callbacks[0];
    }

    private static void authenticateWithAuthorities(String... authorities) {
        List<GrantedAuthority> granted = List.of(authorities).stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("spike-user", "n/a", granted));
    }
}
