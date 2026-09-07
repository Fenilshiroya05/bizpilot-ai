package com.bizpilot.sales.mapper;

import com.bizpilot.sales.dto.LeadResponse;
import com.bizpilot.sales.entity.Lead;
import org.springframework.stereotype.Component;

@Component
public class LeadMapper {

    public LeadResponse toResponse(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getName(),
                lead.getCompany(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getStatus(),
                lead.getSource(),
                lead.getPriority(),
                lead.getFollowUpDate(),
                lead.getAssignedToUserId(),
                lead.getArchivedAt(),
                lead.getOrganization().getId(),
                lead.getCreatedAt(),
                lead.getUpdatedAt()
        );
    }
}
