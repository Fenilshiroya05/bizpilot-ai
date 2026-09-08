package com.bizpilot.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;

/**
 * Overrides Spring AI's own auto-configured {@link ToolExecutionExceptionProcessor}
 * (verified: {@code ToolCallingAutoConfiguration} auto-configures a default
 * one, {@code @ConditionalOnMissingBean} — this bean simply wins) with a
 * uniform, safe mapping — project instructions §19/§20:
 *
 * <ul>
 *   <li><b>Authorization/authentication denial</b> ({@link AccessDeniedException}
 *       — thrown by {@code @PreAuthorize} on a tool method — or {@link
 *       AuthenticationException}): never rethrown, never exposes the raw
 *       exception type or message to the model; a fixed, generic string is
 *       returned as the tool's result so the assistant can safely say "I
 *       don't have enough information" rather than crash the whole chat
 *       request. Empirically verified (project instructions §4; see {@code
 *       ToolSecurityEnforcementTest}) that Spring AI's {@code
 *       MethodToolCallback} wraps whatever a tool throws — including a
 *       {@code @PreAuthorize} denial — in a {@link ToolExecutionException},
 *       which is exactly what this processor receives.</li>
 *   <li><b>Invalid tool input</b> ({@link IllegalArgumentException}, thrown
 *       by the tool classes' own validation): safe to return verbatim — the
 *       message is always a short, hand-authored, content-free string
 *       (e.g. "query must not be blank."), never derived from database
 *       content.</li>
 *   <li><b>Anything else</b> (a genuine, unexpected system/database
 *       failure): rethrown, unmodified — propagates through {@code
 *       ChatClient.call()} to {@code DefaultAiChatService}'s existing
 *       catch block, wrapped in the existing {@code AiProviderException},
 *       and surfaces as the already-established {@code 502}/{@code
 *       AI_PROVIDER_ERROR} response (Phase 16) — reused, not duplicated.
 *       This deliberately avoids a blanket "swallow everything as a safe
 *       string" handler, which would mask genuine bugs (project
 *       instructions §20).</li>
 * </ul>
 *
 * <p>"Not found" (e.g. {@code getCustomer} for a nonexistent/cross-tenant
 * id) never reaches this processor at all — every tool method catches its
 * own {@code *NotFoundException} internally and returns a normal,
 * successful "not found" result object (see {@code ai.tools.dto.*LookupResult}),
 * never an exception.
 */
@Configuration
public class AiToolExecutionConfig {

    private static final Logger log = LoggerFactory.getLogger(AiToolExecutionConfig.class);

    private static final String ACCESS_DENIED_MESSAGE = "This information is not available.";

    @Bean
    public ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        return exception -> {
            Throwable cause = exception.getCause();
            String toolName = exception.getToolDefinition().name();

            if (cause instanceof AccessDeniedException || cause instanceof AuthenticationException) {
                log.warn("AI tool call denied [tool={}, errorType={}]", toolName, cause.getClass().getSimpleName());
                return ACCESS_DENIED_MESSAGE;
            }
            if (cause instanceof IllegalArgumentException) {
                log.warn("AI tool call rejected invalid input [tool={}]", toolName);
                return cause.getMessage();
            }

            log.error("AI tool call failed [tool={}, errorType={}]", toolName,
                    cause != null ? cause.getClass().getSimpleName() : "unknown", exception);
            throw exception;
        };
    }
}
