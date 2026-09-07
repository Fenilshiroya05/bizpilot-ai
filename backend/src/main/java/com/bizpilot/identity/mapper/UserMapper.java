package com.bizpilot.identity.mapper;

import com.bizpilot.identity.dto.UserResponse;
import com.bizpilot.identity.entity.User;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.getStatus(),
                user.getCreatedAt()
        );
    }
}
