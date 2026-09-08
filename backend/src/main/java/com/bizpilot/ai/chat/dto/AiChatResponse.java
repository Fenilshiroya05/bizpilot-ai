package com.bizpilot.ai.chat.dto;

import java.util.List;

/**
 * {@code sources} is one entry per retrieved chunk actually used to build
 * the answer's context — not deduplicated by document. Two chunks from the
 * same document (different {@code chunkIndex}) are two genuinely different
 * pieces of content the model was given, so both are listed; this is a
 * deliberate decision (project instructions §25), not an oversight.
 */
public record AiChatResponse(String answer, List<AiChatSource> sources) {
}
