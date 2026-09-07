package com.bizpilot.ai.exception;

/**
 * Wraps any failure from Spring AI / the underlying model provider (network
 * error, authentication failure, rate limit, malformed response, ...) behind
 * a single, generic BizPilot-owned exception. Mirrors
 * {@code documents.exception.DocumentStorageException}'s exact shape and
 * rationale: the message is always a static, hand-authored string (never the
 * raw provider exception's message, which can reference prompt content, API
 * key hints, or provider infrastructure details); the original exception is
 * preserved as {@code cause} for server-side log correlation only.
 *
 * <p>There is no REST API in Phase 14, so nothing currently serializes this
 * exception to a client — {@code GlobalExceptionHandler} is deliberately not
 * touched this phase (see the Phase 14 implementation report). A future
 * phase introducing an AI-facing endpoint must add a handler here that maps
 * this exception to a generic client-safe response, exactly like every
 * other module's {@code *StorageException}/{@code *DataException} handler.
 */
public class AiProviderException extends RuntimeException {

    public AiProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
