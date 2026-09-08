package com.bizpilot.ai.chat;

import com.bizpilot.ai.chat.dto.AiChatResponse;
import com.bizpilot.ai.chat.dto.AiChatSource;
import com.bizpilot.ai.exception.AiDisabledException;
import com.bizpilot.ai.retrieval.DocumentRetrievalService;
import com.bizpilot.ai.retrieval.RetrievedChunk;
import com.bizpilot.ai.service.AiChatService;
import com.bizpilot.ai.tools.CustomerTools;
import com.bizpilot.ai.tools.InvoiceTools;
import com.bizpilot.ai.tools.LeadTools;
import com.bizpilot.ai.tools.ProductTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Default {@link AiAssistantService}. Always exists as a bean, regardless
 * of {@code bizpilot.ai.enabled} (project instructions §26/§28) — its
 * AI-gated collaborators ({@link DocumentRetrievalService}, {@link
 * AiChatService}) are held via {@link ObjectProvider}, exactly mirroring
 * {@code documents.service.DocumentService}'s existing pattern for {@code
 * DocumentVectorStoreService} — so the controller/RBAC graph this class
 * sits behind works identically whether or not AI is enabled, and enabling
 * AI never requires touching this class.
 *
 * <p><b>Never bypasses the Phase 15 retrieval boundary.</b> The only
 * retrieval call is {@code documentRetrievalService.search(message, TOP_K)}
 * — no {@code VectorStore}, no {@code DocumentVectorStoreService}, no raw
 * SQL. Tenant filtering, and its fail-closed behavior when no authenticated
 * context exists, remain entirely {@code DocumentRetrievalService}'s
 * responsibility; this class does not catch, wrap, or reinterpret the
 * {@link IllegalStateException} it throws for that case (see the class-level
 * decision recorded in {@code GlobalExceptionHandler} for why no new
 * exception/mapping was introduced for it).
 */
@Service
public class DefaultAiAssistantService implements AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(DefaultAiAssistantService.class);

    /** Locked (project instructions §4/§20) — never a request parameter. */
    private static final int TOP_K = 5;

    private static final String NO_CONTEXT_ANSWER =
            "I couldn't find enough information in your organization's documents to answer that.";

    private final ObjectProvider<DocumentRetrievalService> documentRetrievalService;
    private final ObjectProvider<AiChatService> aiChatService;
    private final AssistantContextBuilder contextBuilder;
    private final AssistantPromptService promptService;

    /**
     * Phase 17 (CLAUDE.md §19) — the fixed, locked tool set, constructor-
     * injected as plain beans (never a dynamic registry/plugin marketplace,
     * project instructions §21). Each is unconditional — a {@code
     * @Component} with no AI-gated dependency of its own — so it exists
     * regardless of {@code bizpilot.ai.enabled}; only passing them to
     * {@link AiChatService#chat(String, String, List)} ever makes them
     * reachable at all, and that only happens once AI is confirmed enabled
     * below.
     */
    private final CustomerTools customerTools;
    private final LeadTools leadTools;
    private final ProductTools productTools;
    private final InvoiceTools invoiceTools;

    public DefaultAiAssistantService(ObjectProvider<DocumentRetrievalService> documentRetrievalService,
                                      ObjectProvider<AiChatService> aiChatService,
                                      AssistantContextBuilder contextBuilder,
                                      AssistantPromptService promptService,
                                      CustomerTools customerTools,
                                      LeadTools leadTools,
                                      ProductTools productTools,
                                      InvoiceTools invoiceTools) {
        this.documentRetrievalService = documentRetrievalService;
        this.aiChatService = aiChatService;
        this.contextBuilder = contextBuilder;
        this.promptService = promptService;
        this.customerTools = customerTools;
        this.leadTools = leadTools;
        this.productTools = productTools;
        this.invoiceTools = invoiceTools;
    }

    @Override
    public AiChatResponse ask(String message) {
        DocumentRetrievalService retrieval = documentRetrievalService.getIfAvailable();
        AiChatService chat = aiChatService.getIfAvailable();
        if (retrieval == null || chat == null) {
            // Checked first, before any retrieval/provider work — project
            // instructions §27/§55.
            throw new AiDisabledException();
        }

        Instant start = Instant.now();
        // Fails closed here, unmodified: DocumentRetrievalService itself
        // throws IllegalStateException with no authenticated tenant context,
        // before touching the vector store — project instructions §16/§17.
        List<RetrievedChunk> chunks = retrieval.search(message, TOP_K);

        if (chunks.isEmpty()) {
            log.info("AI assistant request answered with no retrieved context [resultCount=0]");
            return new AiChatResponse(NO_CONTEXT_ANSWER, List.of());
        }

        String context = contextBuilder.build(chunks);
        String userMessage = message + "\n\n" + context;
        List<Object> tools = List.of(customerTools, leadTools, productTools, invoiceTools);
        String answer = chat.chat(promptService.systemPrompt(), userMessage, tools);

        long durationMs = Duration.between(start, Instant.now()).toMillis();
        log.info("AI assistant request succeeded [resultCount={}, durationMs={}]", chunks.size(), durationMs);

        return new AiChatResponse(answer, toSources(chunks));
    }

    private static List<AiChatSource> toSources(List<RetrievedChunk> chunks) {
        return chunks.stream()
                .map(chunk -> new AiChatSource(chunk.documentId(), chunk.originalFilename(), chunk.chunkIndex()))
                .toList();
    }
}
