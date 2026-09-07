package com.bizpilot.ai.retrieval;

import com.bizpilot.ai.exception.AiProviderException;

import java.util.List;

/**
 * The only surface that performs semantic vector retrieval (Phase 15,
 * CLAUDE.md §17) — {@code PgVectorStore} is never exposed to controllers or
 * any other module directly (project instructions §24). Not a chatbot and
 * not exposed through any REST API in this phase; the future AI assistant
 * (Phase 16+) calls this internal service directly.
 *
 * <p><b>Tenant filtering is mandatory and internal.</b> Every call resolves
 * the current organization from {@code TenantContext} and constructs the
 * filter itself — there is no method here, and must never be one added
 * later, that accepts a caller-supplied {@code organizationId} or a raw
 * Spring AI filter expression (project instructions §25).
 */
public interface DocumentRetrievalService {

    /**
     * Returns up to {@code topK} chunks most similar to {@code queryText},
     * scoped to the caller's own organization only.
     *
     * @throws IllegalStateException if no authenticated tenant context is
     *                                available — fails closed; never falls
     *                                back to searching across all
     *                                organizations (project instructions §23)
     * @throws AiProviderException   if the underlying similarity search fails
     */
    List<RetrievedChunk> search(String queryText, int topK);
}
