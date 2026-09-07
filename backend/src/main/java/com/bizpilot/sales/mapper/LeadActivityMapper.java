package com.bizpilot.sales.mapper;

import com.bizpilot.sales.dto.LeadActivityResponse;
import com.bizpilot.sales.entity.LeadActivity;
import org.springframework.stereotype.Component;

@Component
public class LeadActivityMapper {

    public LeadActivityResponse toResponse(LeadActivity activity) {
        return new LeadActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getContent(),
                activity.getCreatedByUserId(),
                activity.getCreatedAt()
        );
    }
}
