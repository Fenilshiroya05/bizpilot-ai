package com.bizpilot.ai;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A deterministic, zero-network {@link EmbeddingModel} test double —
 * PGVector integration tests (Phase 15) need a real {@code EmbeddingModel}
 * bean in the Spring context (both {@code DefaultAiEmbeddingService} and
 * Spring AI's own {@code PgVectorStore} — for the retrieval query's
 * internal embedding — require one), but project instructions §44 forbid
 * any real OpenAI call in tests. The SAME text always produces the SAME
 * 1536-dimension vector (seeded by {@code text.hashCode()}); different
 * text produces a different vector. Sufficient to prove tenant filtering,
 * insert/delete/retrieval wiring, and idempotency — this is not a
 * relevance/ranking-quality test.
 */
public class DeterministicFakeEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 1536;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<Embedding> embeddings = new ArrayList<>();
        int index = 0;
        for (String text : request.getInstructions()) {
            embeddings.add(new Embedding(vectorFor(text), index++));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return vectorFor(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    private static float[] vectorFor(String text) {
        Random random = new Random(text == null ? 0 : text.hashCode());
        float[] vector = new float[DIMENSIONS];
        for (int i = 0; i < DIMENSIONS; i++) {
            vector[i] = random.nextFloat();
        }
        return vector;
    }
}
