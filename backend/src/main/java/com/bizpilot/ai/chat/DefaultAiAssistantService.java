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
 * <p><b>Only {@link AiChatService} is a hard requirement for {@link
 * #ask(String)}</b> (local-chat-only scope) — {@link DocumentRetrievalService}
 * is best-effort: when it's unavailable (e.g. no vector store configured,
 * as in a local Ollama-chat-only environment with no embedding model) the
 * assistant answers directly, without document context, instead of treating
 * "no RAG" as equivalent to "AI disabled." This never changes what document
 * context IS used when retrieval IS available — see below.
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
        // Only the chat collaborator is a hard requirement — checked first,
        // before any retrieval/provider work — project instructions §27/§55.
        AiChatService chat = aiChatService.getIfAvailable();
        if (chat == null) {
            throw new AiDisabledException();
        }

        Instant start = Instant.now();
        // Retrieval is best-effort (local-chat-only scope): absent when no
        // vector store is configured (e.g. local Ollama chat with no
        // embedding model), in which case this is treated exactly like a
        // zero-chunk result below — never as "AI disabled." When retrieval
        // IS available, it still fails closed unmodified: DocumentRetrievalService
        // itself throws IllegalStateException with no authenticated tenant
        // context, before touching the vector store — project instructions
        // §16/§17.
        DocumentRetrievalService retrieval = documentRetrievalService.getIfAvailable();
        List<RetrievedChunk> chunks = retrieval != null ? retrieval.search(message, TOP_K) : List.of();

        // No document context to ground the answer in — still answer
        // directly with the chat model rather than a canned "not enough
        // information" response, so local/no-document environments (and any
        // question unrelated to uploaded documents) still get a real answer.
        String userMessage = chunks.isEmpty() ? message : message + "\n\n" + contextBuilder.build(chunks);
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
