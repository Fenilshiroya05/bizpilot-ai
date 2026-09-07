package com.bizpilot.tasks.mapper;

import com.bizpilot.tasks.dto.TaskResponse;
import com.bizpilot.tasks.entity.Task;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getAssignedToUserId(),
                task.getCustomerId(),
                task.getLeadId(),
                task.getDueDate(),
                task.getNotes(),
                task.getOrganization().getId(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
