package com.bizpilot.ai.scoring;

import com.bizpilot.ai.exception.AiDisabledException;
import com.bizpilot.ai.exception.AiProviderException;
import com.bizpilot.ai.exception.LeadScoringValidationException;
import com.bizpilot.ai.scoring.dto.LeadScoreAiOutput;
import com.bizpilot.ai.scoring.dto.LeadScoreResponse;
import com.bizpilot.ai.service.AiChatService;
import com.bizpilot.organization.entity.Organization;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadActivity;
import com.bizpilot.sales.entity.LeadActivityType;
import com.bizpilot.sales.entity.LeadPriority;
import com.bizpilot.sales.entity.LeadSource;
import com.bizpilot.sales.exception.LeadNotFoundException;
import com.bizpilot.sales.service.LeadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure Mockito unit tests — no Spring context, no database, no real AI call.
 * Live tenant isolation/permission/mutation-safety verification is in
 * {@code LeadScoringIntegrationTests}; this class only proves correct
 * delegation, context construction, and output validation.
 */
class LeadScoringServiceTest {

    private final LeadService leadService = mock(LeadService.class);
    private final AiChatService aiChatService = mock(AiChatService.class);
    private final LeadScoringPromptService promptService = mock(LeadScoringPromptService.class);
    private final LeadScoringContextBuilder contextBuilder = new LeadScoringContextBuilder();

    private LeadScoringService service() {
        return new LeadScoringService(leadService, objectProviderOf(aiChatService), promptService, contextBuilder);
    }

    private LeadScoringService serviceWithAiDisabled() {
        return new LeadScoringService(leadService, objectProviderOf(null), promptService, contextBuilder);
    }

    @Test
    void throwsAiDisabledExceptionWhenTheAiCollaboratorIsUnavailable() {
        assertThatThrownBy(() -> serviceWithAiDisabled().score(UUID.randomUUID()))
                .isInstanceOf(AiDisabledException.class);

        // Checked before any lead/history fetch — project instructions §8.
        verify(leadService, never()).getById(any());
        verify(leadService, never()).getHistory(any(), any());
    }

    @Test
    void fetchesTheLeadAndItsRecentHistoryThroughLeadServiceOnly() {
        UUID leadId = UUID.randomUUID();
        Lead lead = lead("Acme Renovations");
        when(leadService.getById(leadId)).thenReturn(lead);
        when(leadService.getHistory(eq(leadId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(promptService.systemPrompt()).thenReturn("SYSTEM PROMPT");
        stubValidAiOutput(78, "HIGH", "Strong engagement.", "Call within 48 hours.");

        LeadScoreResponse response = service().score(leadId);

        assertThat(response.leadId()).isEqualTo(leadId);
        verify(leadService).getById(leadId);
        verify(leadService).getHistory(eq(leadId), any(Pageable.class));
    }

    @Test
    void usesAFixedPageSizeOfFiveMostRecentHistoryItemsSortedNewestFirst() {
        UUID leadId = UUID.randomUUID();
        when(leadService.getById(leadId)).thenReturn(lead("Beta Corp"));
        when(leadService.getHistory(eq(leadId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(promptService.systemPrompt()).thenReturn("SYSTEM");
        stubValidAiOutput(50, "MEDIUM", "Reasoning.", "Action.");

        service().score(leadId);

        verify(leadService).getHistory(eq(leadId), argThat((Pageable pageable) ->
                pageable.getPageSize() == 5 && pageable.getPageNumber() == 0));
    }

    @Test
    void passesTheLoadedSystemPromptAndABuiltUserContextToTheAiCall() {
        UUID leadId = UUID.randomUUID();
        Lead lead = lead("Gamma LLC");
        LeadActivity activity = new LeadActivity(lead, LeadActivityType.NOTE, "Called and interested", UUID.randomUUID());
        when(leadService.getById(leadId)).thenReturn(lead);
        when(leadService.getHistory(eq(leadId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(activity)));
        when(promptService.systemPrompt()).thenReturn("FIXED_SYSTEM_PROMPT");
        stubValidAiOutput(60, "MEDIUM", "Reasoning text.", "Follow up soon.");

        service().score(leadId);

        verify(aiChatService).chatForStructuredOutput(eq("FIXED_SYSTEM_PROMPT"),
                argThat((String userMessage) -> userMessage.contains("Gamma LLC")
                        && userMessage.contains("Called and interested")),
                eq(LeadScoreAiOutput.class));
    }

    @Test
    void mapsAValidAiOutputToAClientResponse() {
        UUID leadId = UUID.randomUUID();
        when(leadService.getById(leadId)).thenReturn(lead("Delta Inc"));
        when(leadService.getHistory(eq(leadId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(promptService.systemPrompt()).thenReturn("SYSTEM");
        stubValidAiOutput(78, "HIGH", "Great fit.", "Call today.");

        LeadScoreResponse response = service().score(leadId);

        assertThat(response.score()).isEqualTo(78);
        assertThat(response.priority()).isEqualTo(LeadPriority.HIGH);
        assertThat(response.reasoning()).isEqualTo("Great fit.");
        assertThat(response.recommendedAction()).isEqualTo("Call today.");
        assertThat(response.generatedAt()).isNotNull();
    }

    @Test
    void scoreZeroIsAccepted() {
        assertThat(scoreWith(0, "LOW", "Reasoning.", "Action.").score()).isZero();
    }

    @Test
    void scoreOneHundredIsAccepted() {
        assertThat(scoreWith(100, "HIGH", "Reasoning.", "Action.").score()).isEqualTo(100);
    }

    @Test
    void scoreBelowZeroIsRejected() {
        assertThatThrownBy(() -> attemptScore(-1, "LOW", "Reasoning.", "Action."))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void scoreAboveOneHundredIsRejected() {
        assertThatThrownBy(() -> attemptScore(101, "HIGH", "Reasoning.", "Action."))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void nullScoreIsRejected() {
        assertThatThrownBy(() -> attemptScore(null, "MEDIUM", "Reasoning.", "Action."))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void invalidPriorityIsRejected() {
        assertThatThrownBy(() -> attemptScore(80, "URGENT", "Reasoning.", "Action."))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void missingReasoningIsRejected() {
        assertThatThrownBy(() -> attemptScore(80, "HIGH", null, "Action."))
                .isInstanceOf(LeadScoringValidationException.class);
        assertThatThrownBy(() -> attemptScore(80, "HIGH", "   ", "Action."))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void missingRecommendedActionIsRejected() {
        assertThatThrownBy(() -> attemptScore(80, "HIGH", "Reasoning.", null))
                .isInstanceOf(LeadScoringValidationException.class);
        assertThatThrownBy(() -> attemptScore(80, "HIGH", "Reasoning.", "  "))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void oversizedReasoningIsRejected() {
        String tooLong = "a".repeat(LeadScoringService.MAX_REASONING_LENGTH + 1);
        assertThatThrownBy(() -> attemptScore(80, "HIGH", tooLong, "Action."))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void oversizedRecommendedActionIsRejected() {
        String tooLong = "a".repeat(LeadScoringService.MAX_RECOMMENDED_ACTION_LENGTH + 1);
        assertThatThrownBy(() -> attemptScore(80, "HIGH", "Reasoning.", tooLong))
                .isInstanceOf(LeadScoringValidationException.class);
    }

    @Test
    void aLeadNotFoundExceptionPropagatesUnmodified() {
        UUID leadId = UUID.randomUUID();
        when(leadService.getById(leadId)).thenThrow(new LeadNotFoundException(leadId));

        assertThatThrownBy(() -> service().score(leadId)).isInstanceOf(LeadNotFoundException.class);
    }

    @Test
    void aProviderExceptionPropagatesUnmodified() {
        UUID leadId = UUID.randomUUID();
        when(leadService.getById(leadId)).thenReturn(lead("Epsilon Co"));
        when(leadService.getHistory(eq(leadId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(promptService.systemPrompt()).thenReturn("SYSTEM");
        when(aiChatService.chatForStructuredOutput(anyString(), anyString(), eq(LeadScoreAiOutput.class)))
                .thenThrow(new AiProviderException("AI structured output request failed", new RuntimeException("boom")));

        assertThatThrownBy(() -> service().score(leadId)).isInstanceOf(AiProviderException.class);
    }

    // ---- Helpers -----------------------------------------------------------------

    private LeadScoreResponse scoreWith(Integer score, String priority, String reasoning, String action) {
        return attemptScore(score, priority, reasoning, action);
    }

    private LeadScoreResponse attemptScore(Integer score, String priority, String reasoning, String action) {
        UUID leadId = UUID.randomUUID();
        when(leadService.getById(leadId)).thenReturn(lead("Zeta Ltd"));
        when(leadService.getHistory(eq(leadId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        when(promptService.systemPrompt()).thenReturn("SYSTEM");
        stubValidAiOutput(score, priority, reasoning, action);
        return service().score(leadId);
    }

    private void stubValidAiOutput(Integer score, String priority, String reasoning, String action) {
        when(aiChatService.chatForStructuredOutput(anyString(), anyString(), eq(LeadScoreAiOutput.class)))
                .thenReturn(new LeadScoreAiOutput(score, priority, reasoning, action));
    }

    private static Lead lead(String name) {
        Organization organization = new Organization("Test Org");
        return new Lead(organization, name, "Company", "lead@example.com", "1234567890",
                LeadSource.WEBSITE, LeadPriority.MEDIUM, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> objectProviderOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }
}
