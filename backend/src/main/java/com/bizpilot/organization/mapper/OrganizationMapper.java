package com.bizpilot.organization.mapper;

import com.bizpilot.organization.dto.OrganizationResponse;
import com.bizpilot.organization.entity.Organization;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapper {

    public OrganizationResponse toResponse(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getName(),
                organization.getCreatedAt()
        );
    }
}
