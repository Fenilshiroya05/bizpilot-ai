package com.bizpilot.ai.scoring;

import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadActivity;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds the user-role scoring context from a {@link Lead} and its bounded
 * recent activity/notes (Phase 18, project instructions §20) — a pure,
 * deterministic string-building step. No AI logic, no database access, no
 * business rules: {@code ai.scoring.LeadScoringService} is responsible for
 * fetching the lead/activities (via the existing tenant-safe {@code
 * LeadService}) and for validating the model's response; this class only
 * ever turns already-fetched data into text.
 *
 * <p><b>PII/context minimization</b>: email and phone are deliberately
 * omitted — neither is informative for a sales-priority assessment, and
 * omitting them reduces what reaches the AI provider to only what the
 * scoring task actually needs (mirrors the project's existing "do not
 * unnecessarily collect/expose personal information" discipline). {@code
 * assignedToUserId} is reduced to a plain yes/no signal — the raw internal
 * UUID has no scoring value.
 *
 * <p><b>Prompt-injection boundary</b>: everything this class produces is
 * placed in the <em>user</em> message only, clearly delimited in {@code
 * <lead>}/{@code <recent_activity>} tags (mirrors {@code
 * ai.chat.AssistantContextBuilder}'s {@code <document_context>} convention)
 * — this class never touches the system prompt, and lead/activity content
 * (which a user fully controls, e.g. via a note) is never treated as
 * anything other than delimited data by the caller.
 */
@Component
public class LeadScoringContextBuilder {

    /** Locked (project instructions §21): never client/model-configurable. */
    static final int MAX_ACTIVITY_ITEMS = 5;

    /**
     * A fixed per-item bound so a single very long note cannot make the
     * prompt size unpredictable (project instructions §21). {@code
     * lead_activities.content} is itself capped at 2000 characters at the
     * database level ({@code V6__create_leads.sql}); 300 characters is
     * comfortably enough to convey a note's gist for scoring purposes
     * without letting it dominate the prompt.
     */
    static final int MAX_ACTIVITY_CONTENT_LENGTH = 300;

    public String build(Lead lead, List<LeadActivity> recentActivity) {
        StringBuilder sb = new StringBuilder();
        sb.append("<lead>\n");
        sb.append("Name: ").append(lead.getName()).append('\n');
        sb.append("Company: ").append(lead.getCompany() != null ? lead.getCompany() : "unknown").append('\n');
        sb.append("Status: ").append(lead.getStatus()).append('\n');
        sb.append("Source: ").append(lead.getSource()).append('\n');
        sb.append("Current priority: ").append(lead.getPriority()).append('\n');
        sb.append("Follow-up date: ")
                .append(lead.getFollowUpDate() != null ? lead.getFollowUpDate().toString() : "none scheduled")
                .append('\n');
        sb.append("Assigned to a team member: ").append(lead.getAssignedToUserId() != null ? "yes" : "no")
                .append('\n');
        sb.append("</lead>\n");

        sb.append("<recent_activity>\n");
        List<LeadActivity> bounded = recentActivity.size() > MAX_ACTIVITY_ITEMS
                ? recentActivity.subList(0, MAX_ACTIVITY_ITEMS)
                : recentActivity;
        if (bounded.isEmpty()) {
            sb.append("No recorded activity or notes.\n");
        } else {
            for (LeadActivity activity : bounded) {
                sb.append("- [").append(activity.getType()).append("] ")
                        .append(truncate(activity.getContent())).append('\n');
            }
        }
        sb.append("</recent_activity>\n");
        return sb.toString();
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() > MAX_ACTIVITY_CONTENT_LENGTH ? value.substring(0, MAX_ACTIVITY_CONTENT_LENGTH) : value;
    }
}
