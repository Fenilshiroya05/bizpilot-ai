package com.bizpilot.identity.mapper;

import com.bizpilot.identity.dto.UserResponse;
import com.bizpilot.identity.entity.Role;
import com.bizpilot.identity.entity.User;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()),
                user.getStatus(),
                user.getOrganization().getId(),
                user.getCreatedAt()
        );
    }
}
