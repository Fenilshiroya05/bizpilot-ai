package com.bizpilot.documents.processing;

import com.bizpilot.documents.exception.DocumentProcessingException;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps Spring AI's {@link TokenTextSplitter} with the locked Phase 15
 * configuration (CLAUDE.md §17, project instructions §20) — token-based,
 * matching the {@code cl100k_base} tokenizer OpenAI's own models use, not a
 * custom chunking algorithm. No chunk overlap: verified against Spring AI's
 * own issue tracker that {@code TokenTextSplitter} has no configurable
 * overlap as of 1.1.8 (targeted for a future 2.x line this project does not
 * adopt) — a known, accepted Phase 15 trade-off (see docs/security.md),
 * not an oversight.
 */
@Component
public class DocumentChunkingService {

    private static final int CHUNK_SIZE = 800;
    private static final int MIN_CHUNK_SIZE_CHARS = 350;
    private static final int MIN_CHUNK_LENGTH_TO_EMBED = 5;
    private static final int MAX_NUM_CHUNKS = 10000;
    private static final boolean KEEP_SEPARATOR = true;

    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(CHUNK_SIZE)
            .withMinChunkSizeChars(MIN_CHUNK_SIZE_CHARS)
            .withMinChunkLengthToEmbed(MIN_CHUNK_LENGTH_TO_EMBED)
            .withMaxNumChunks(MAX_NUM_CHUNKS)
            .withKeepSeparator(KEEP_SEPARATOR)
            .build();

    /**
     * @throws DocumentProcessingException if splitting produces no
     *                                      embeddable chunks at all (e.g.
     *                                      text shorter than {@code
     *                                      minChunkLengthToEmbed})
     */
    public List<TextChunk> split(String text) {
        List<org.springframework.ai.document.Document> splitDocuments =
                splitter.split(new org.springframework.ai.document.Document(text));

        List<TextChunk> chunks = new ArrayList<>(splitDocuments.size());
        for (int i = 0; i < splitDocuments.size(); i++) {
            chunks.add(new TextChunk(i, splitDocuments.get(i).getText()));
        }
        if (chunks.isEmpty()) {
            throw new DocumentProcessingException("No embeddable chunks were produced from the extracted text");
        }
        return chunks;
    }
}
