package com.bizpilot.ai.chat;

import com.bizpilot.ai.exception.AiProviderException;
import com.bizpilot.ai.service.AiChatService;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * A zero-network {@link AiChatService} test double (project instructions
 * §64/§65) that records every call it's given — including, for the 3-arg
 * tool-aware overload, the exact tool bean list — and can optionally
 * simulate a model choosing to call one specific tool by name.
 *
 * <p>{@link #simulateToolCall} deliberately reuses the exact same {@link
 * MethodToolCallbackProvider} mechanism {@code ToolSecurityEnforcementTest}
 * already verified Spring AI's own tool-calling machinery uses — so a test
 * enabling it exercises the REAL tool bean (real {@code @PreAuthorize}, a
 * real domain-service call, a real database query against Testcontainers)
 * with only the model's own "decide to call a tool" step faked, never the
 * actual execution path.
 */
public class RecordingFakeAiChatService implements AiChatService {

    public record Invocation(String systemPrompt, String userMessage, List<Object> tools) {
    }

    /** Phase 18 — records a {@link #chatForStructuredOutput} call. */
    public record StructuredInvocation(String systemPrompt, String userMessage, Class<?> responseType) {
    }

    private final List<Invocation> invocations = new ArrayList<>();
    private final List<StructuredInvocation> structuredInvocations = new ArrayList<>();
    private String cannedAnswer = "This is a deterministic test answer.";
    private String simulatedToolName;
    private String simulatedToolArgumentsJson;
    private Object structuredOutputToReturn;
    private AiProviderException structuredOutputFailure;

    @Override
    public String chat(String prompt) {
        invocations.add(new Invocation(null, prompt, List.of()));
        return cannedAnswer;
    }

    @Override
    public String chat(String systemPrompt, String userMessage) {
        invocations.add(new Invocation(systemPrompt, userMessage, List.of()));
        return cannedAnswer;
    }

    @Override
    public String chat(String systemPrompt, String userMessage, List<Object> tools) {
        invocations.add(new Invocation(systemPrompt, userMessage, tools));
        if (simulatedToolName != null) {
            String toolResult = invokeNamedTool(tools, simulatedToolName, simulatedToolArgumentsJson);
            return cannedAnswer + " [tool result: " + toolResult + "]";
        }
        return cannedAnswer;
    }

    /**
     * Phase 18 — the structured-output overload. Faithfully mirrors {@code
     * DefaultAiChatService}'s documented contract: a configured failure is
     * always an {@link AiProviderException} (never a raw/unwrapped
     * exception), exactly as a real implementation would throw.
     */
    @Override
    public <T> T chatForStructuredOutput(String systemPrompt, String userMessage, Class<T> responseType) {
        structuredInvocations.add(new StructuredInvocation(systemPrompt, userMessage, responseType));
        if (structuredOutputFailure != null) {
            throw structuredOutputFailure;
        }
        if (structuredOutputToReturn == null) {
            throw new IllegalStateException(
                    "No structured output configured — call setStructuredOutputToReturn(...) first");
        }
        return responseType.cast(structuredOutputToReturn);
    }

    public List<Invocation> invocations() {
        return invocations;
    }

    public List<StructuredInvocation> structuredInvocations() {
        return structuredInvocations;
    }

    public void reset() {
        invocations.clear();
        structuredInvocations.clear();
        simulatedToolName = null;
        simulatedToolArgumentsJson = null;
        structuredOutputToReturn = null;
        structuredOutputFailure = null;
    }

    public void setStructuredOutputToReturn(Object value) {
        this.structuredOutputToReturn = value;
    }

    public void setStructuredOutputFailure(AiProviderException failure) {
        this.structuredOutputFailure = failure;
    }

    public void setCannedAnswer(String cannedAnswer) {
        this.cannedAnswer = cannedAnswer;
    }

    /** Simulates the model deciding to call {@code toolName} with {@code argumentsJson}. */
    public void simulateToolCall(String toolName, String argumentsJson) {
        this.simulatedToolName = toolName;
        this.simulatedToolArgumentsJson = argumentsJson;
    }

    private static String invokeNamedTool(List<Object> tools, String toolName, String argumentsJson) {
        for (Object toolBean : tools) {
            ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(toolBean).build()
                    .getToolCallbacks();
            for (ToolCallback callback : callbacks) {
                if (callback.getToolDefinition().name().equals(toolName)) {
                    return callback.call(argumentsJson);
                }
            }
        }
        throw new IllegalStateException("Simulated tool call requested an unknown tool: " + toolName);
    }
}
