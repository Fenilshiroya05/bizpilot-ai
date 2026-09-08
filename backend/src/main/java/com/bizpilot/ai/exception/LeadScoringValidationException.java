package com.bizpilot.ai.exception;

/**
 * Thrown by {@code ai.scoring.LeadScoringService} when the AI's structured
 * lead-scoring output was successfully parsed (see {@link AiProviderException}
 * for the "couldn't even be parsed" case) but fails business validation —
 * an out-of-range/missing score, an unrecognized priority string, or a
 * missing/oversized reasoning or recommended-action field (Phase 18, project
 * instructions §13/§15).
 *
 * <p>AI output is untrusted input: this exception exists specifically so a
 * semantically-invalid-but-well-formed response is never silently repaired
 * (clamped, defaulted, or coerced) into something that merely looks valid —
 * it fails the request instead. Mapped by {@code GlobalExceptionHandler} to
 * {@code 502}/{@code AI_SCORING_FAILED}; the message here may be detailed
 * for server-side log correlation, but is never sent to the client verbatim
 * (mirrors {@code AiProviderException}'s exact handling).
 */
public class LeadScoringValidationException extends RuntimeException {

    public LeadScoringValidationException(String message) {
        super(message);
    }
}
