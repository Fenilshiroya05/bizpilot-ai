package com.bizpilot.documents.processing;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the configured {@code TokenTextSplitter} behavior (project
 * instructions §47) — not a custom chunking implementation, since there
 * isn't one.
 */
class DocumentChunkingServiceTest {

    private final DocumentChunkingService chunkingService = new DocumentChunkingService();

    @Test
    void textSmallerThanTheChunkSizeProducesExactlyOneChunk() {
        String text = "BizPilot AI helps small businesses manage customers, leads, and invoices.";

        List<TextChunk> chunks = chunkingService.split(text);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).text()).contains("BizPilot AI helps small businesses");
    }

    @Test
    void largeTextProducesMultipleChunksWithSequentialZeroBasedIndexes() {
        // Comfortably over 800 tokens' worth of prose.
        String sentence = "BizPilot AI is a business operations platform for small and medium businesses. ";
        String largeText = sentence.repeat(400);

        List<TextChunk> chunks = chunkingService.split(largeText);

        assertThat(chunks.size()).isGreaterThan(1);
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
        }
    }

    @Test
    void chunksTogetherCoverSubstantiallyTheSameContentWithNoConfiguredOverlap() {
        // Locked decision (project instructions §20/§70): no chunk overlap.
        // A reasonable proxy for "no overlap" is that the sum of chunk
        // lengths roughly matches the source length, rather than being
        // meaningfully larger (which overlapping windows would produce).
        String sentence = "Quarterly revenue increased across every region this period. ";
        String largeText = sentence.repeat(300);

        List<TextChunk> chunks = chunkingService.split(largeText);
        int totalChunkChars = chunks.stream().mapToInt(c -> c.text().length()).sum();

        assertThat(totalChunkChars).isCloseTo(largeText.length(), org.assertj.core.data.Offset.offset(largeText.length() / 10));
    }

    @Test
    void throwsWhenTextIsShorterThanTheMinimumEmbeddableLength() {
        assertThatThrownBy(() -> chunkingService.split("hi"))
                .isInstanceOf(DocumentProcessingException.class);
    }
}
