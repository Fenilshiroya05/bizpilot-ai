package com.bizpilot.crm.mapper;

import com.bizpilot.crm.dto.CustomerResponse;
import com.bizpilot.crm.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getCompany(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getAddress(),
                customer.getGstin(),
                customer.getStatus(),
                customer.getNotes(),
                customer.getOrganization().getId(),
                customer.getCreatedAt(),
                customer.getUpdatedAt()
        );
    }
}
