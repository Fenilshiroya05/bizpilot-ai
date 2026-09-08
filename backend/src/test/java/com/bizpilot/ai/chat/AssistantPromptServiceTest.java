package com.bizpilot.ai.chat;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantPromptServiceTest {

    @Test
    void loadsTheVersionedSystemPromptResourceOnce() {
        AssistantPromptService service = new AssistantPromptService(
                new ClassPathResource("prompts/assistant-system-v1.txt"));

        String prompt = service.systemPrompt();

        assertThat(prompt).isNotBlank();
        assertThat(prompt).contains("BizPilot AI");
        assertThat(prompt).containsIgnoringCase("do not");
    }

    @Test
    void theSamePromptInstanceIsReturnedOnEveryCall() {
        AssistantPromptService service = new AssistantPromptService(
                new ClassPathResource("prompts/assistant-system-v1.txt"));

        assertThat(service.systemPrompt()).isSameAs(service.systemPrompt());
    }
}
