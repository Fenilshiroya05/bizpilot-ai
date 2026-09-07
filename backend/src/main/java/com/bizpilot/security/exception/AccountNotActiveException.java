package com.bizpilot.security.exception;

import com.bizpilot.identity.entity.UserStatus;

/**
 * Thrown when a login's password check succeeds but the account is not
 * {@code ACTIVE}. Only ever raised *after* password verification, so it
 * never reveals account status to someone who doesn't know the password.
 */
public class AccountNotActiveException extends RuntimeException {

    private final UserStatus status;

    public AccountNotActiveException(UserStatus status) {
        super("Account is not active: " + status);
        this.status = status;
    }

    public UserStatus getStatus() {
        return status;
    }
}
