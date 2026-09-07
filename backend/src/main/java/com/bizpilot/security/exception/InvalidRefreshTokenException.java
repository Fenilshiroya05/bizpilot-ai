package com.bizpilot.security.exception;

/** Thrown for a missing, unknown, expired, or revoked/reused refresh token. */
public class InvalidRefreshTokenException extends RuntimeException {

    public InvalidRefreshTokenException() {
        super("Invalid refresh token");
    }
}
