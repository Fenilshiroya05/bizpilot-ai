package com.bizpilot.documents.mapper;

import com.bizpilot.documents.dto.DocumentResponse;
import com.bizpilot.documents.entity.Document;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getOriginalFilename(),
                document.getContentType(),
                document.getFileSize(),
                document.getStatus(),
                document.getUploadedByUserId(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
