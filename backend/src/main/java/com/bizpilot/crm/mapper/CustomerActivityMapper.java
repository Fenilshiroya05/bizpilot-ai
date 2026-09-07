package com.bizpilot.crm.mapper;

import com.bizpilot.crm.dto.CustomerActivityResponse;
import com.bizpilot.crm.entity.CustomerActivity;
import org.springframework.stereotype.Component;

@Component
public class CustomerActivityMapper {

    public CustomerActivityResponse toResponse(CustomerActivity activity) {
        return new CustomerActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getContent(),
                activity.getCreatedByUserId(),
                activity.getCreatedAt()
        );
    }
}
