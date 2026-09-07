package com.bizpilot.sales.exception;

import java.util.UUID;

/**
 * Thrown when the requested assignee does not exist, belongs to a different
 * organization than the lead being assigned (a user from Organization B must
 * never be assignable to a lead in Organization A — project instructions
 * §7/§16), or is not an {@link com.bizpilot.identity.entity.UserStatus#ACTIVE}
 * account (assigning a lead to a disabled/locked user would leave it
 * silently unworked with no signal to the assigner — caught in security
 * review, fixed before completion).
 */
public class InvalidAssigneeException extends RuntimeException {

    public InvalidAssigneeException(UUID userId) {
        super("No such user in this organization: " + userId);
    }
}
