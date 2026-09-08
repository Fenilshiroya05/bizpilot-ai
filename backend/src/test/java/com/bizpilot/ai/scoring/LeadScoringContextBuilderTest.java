package com.bizpilot.ai.scoring;

import com.bizpilot.organization.entity.Organization;
import com.bizpilot.sales.entity.Lead;
import com.bizpilot.sales.entity.LeadActivity;
import com.bizpilot.sales.entity.LeadActivityType;
import com.bizpilot.sales.entity.LeadSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LeadScoringContextBuilderTest {

    private final LeadScoringContextBuilder builder = new LeadScoringContextBuilder();

    @Test
    void includesTheLeadsIdentifyingAndBusinessFields() {
        Lead lead = lead("Nimbus Consulting");

        String context = builder.build(lead, List.of());

        assertThat(context).contains("Nimbus Consulting", "Company", "Status", "Source", "Current priority");
    }

    @Test
    void neverIncludesEmailOrPhone() {
        Lead lead = lead("Orion Manufacturing");

        String context = builder.build(lead, List.of());

        assertThat(context).doesNotContain("lead@example.com").doesNotContain("1234567890");
    }

    @Test
    void includesRecentActivityContentDelimitedFromLeadFields() {
        Lead lead = lead("Zephyr Traders");
        LeadActivity activity = new LeadActivity(lead, LeadActivityType.NOTE, "Called and very interested",
                UUID.randomUUID());

        String context = builder.build(lead, List.of(activity));

        assertThat(context).contains("<recent_activity>", "Called and very interested", "</recent_activity>");
    }

    @Test
    void truncatesAnOverlyLongActivityContentToTheFixedBound() {
        Lead lead = lead("Delta Inc");
        String longContent = "a".repeat(1000);
        LeadActivity activity = new LeadActivity(lead, LeadActivityType.NOTE, longContent, UUID.randomUUID());

        String context = builder.build(lead, List.of(activity));

        assertThat(context).contains("a".repeat(LeadScoringContextBuilder.MAX_ACTIVITY_CONTENT_LENGTH));
        assertThat(context).doesNotContain("a".repeat(LeadScoringContextBuilder.MAX_ACTIVITY_CONTENT_LENGTH + 1));
    }

    @Test
    void neverIncludesMoreThanTheFixedMaximumNumberOfActivityItems() {
        Lead lead = lead("Epsilon Co");
        List<LeadActivity> tenActivities = java.util.stream.IntStream.range(0, 10)
                .mapToObj(i -> new LeadActivity(lead, LeadActivityType.NOTE, "note-" + i, UUID.randomUUID()))
                .toList();

        String context = builder.build(lead, tenActivities);

        for (int i = 0; i < LeadScoringContextBuilder.MAX_ACTIVITY_ITEMS; i++) {
            assertThat(context).contains("note-" + i);
        }
        for (int i = LeadScoringContextBuilder.MAX_ACTIVITY_ITEMS; i < 10; i++) {
            assertThat(context).doesNotContain("note-" + i);
        }
    }

    @Test
    void statesExplicitlyWhenThereIsNoRecordedActivity() {
        Lead lead = lead("Gamma LLC");

        String context = builder.build(lead, List.of());

        assertThat(context).contains("No recorded activity or notes.");
    }

    private static Lead lead(String name) {
        Organization organization = new Organization("Test Org");
        return new Lead(organization, name, "Company", "lead@example.com", "1234567890",
                LeadSource.WEBSITE, com.bizpilot.sales.entity.LeadPriority.MEDIUM, null);
    }
}
