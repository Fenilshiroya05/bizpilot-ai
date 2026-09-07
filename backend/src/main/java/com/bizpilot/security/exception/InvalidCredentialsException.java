package com.bizpilot.security.exception;

/**
 * Thrown for any login failure where the client should only ever see a
 * generic "invalid credentials" response — whether the email doesn't exist
 * or the password is wrong. Never distinguish between the two in the API
 * response (avoids revealing which emails are registered).
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
