package com.bizpilot.ai.chat;

import com.bizpilot.ai.service.AiChatService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Replaces the real {@code DefaultAiChatService} (which would otherwise
 * also exist once {@code bizpilot.ai.enabled=true}, backed by the mocked
 * {@code ChatClient.Builder} from {@code FakeAiInfrastructureTestConfig})
 * with an inspectable test double — {@code @Primary} so {@code
 * DefaultAiAssistantService}'s {@code ObjectProvider<AiChatService>}
 * resolves to this one, not the real implementation.
 */
@TestConfiguration
public class RecordingFakeAiChatServiceTestConfig {

    @Bean
    @Primary
    public AiChatService aiChatService() {
        return new RecordingFakeAiChatService();
    }
}
