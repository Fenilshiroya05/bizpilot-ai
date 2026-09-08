package com.bizpilot.ai.tools.dto;

import java.time.Instant;

/**
 * One entry of a customer's activity/note/history timeline, mapped from
 * {@code crm.entity.CustomerActivity} — {@code type} is the enum's {@code
 * name()} (e.g. {@code NOTE}, {@code STATUS_CHANGED}); {@code
 * createdByUserId} is deliberately omitted (a bare UUID with no
 * conversational value on its own).
 */
public record CustomerActivityToolResult(String type, String content, Instant createdAt) {
}
