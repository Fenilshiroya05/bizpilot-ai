package com.bizpilot.ai.chat;

import com.bizpilot.ai.retrieval.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pure transformation, {@code List<RetrievedChunk> -> String} (project
 * instructions §21/§52) — no {@code TenantContext}, no repository, no
 * vector store, no LLM call. Retrieved text is treated strictly as data:
 * this class only ever wraps it in a fixed, literal delimiter format, never
 * interprets or executes anything inside it. Source numbering is 1-based
 * and follows retrieval order exactly (Spring AI's own similarity ranking,
 * unmodified) — deterministic given the same input.
 */
@Component
public class AssistantContextBuilder {

    public String build(List<RetrievedChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("<document_context>\n\n");
        int sourceNumber = 1;
        for (RetrievedChunk chunk : chunks) {
            sb.append("[Source ").append(sourceNumber).append("]\n");
            sb.append("Document: ").append(chunk.originalFilename()).append('\n');
            sb.append("Chunk: ").append(chunk.chunkIndex()).append('\n');
            sb.append("Content:\n");
            sb.append(chunk.content()).append("\n\n");
            sourceNumber++;
        }
        sb.append("</document_context>");
        return sb.toString();
    }
}
