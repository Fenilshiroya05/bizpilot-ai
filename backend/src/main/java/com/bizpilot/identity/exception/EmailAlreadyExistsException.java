package com.bizpilot.identity.exception;

/** Thrown when registration is attempted with an email that's already in use. */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
