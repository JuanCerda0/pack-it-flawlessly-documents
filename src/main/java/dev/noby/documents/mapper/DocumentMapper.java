package dev.noby.documents.mapper;

import dev.noby.documents.dto.DocumentSummary;
import dev.noby.documents.entity.DocumentEntity;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {
    public DocumentSummary toSummary(DocumentEntity entity) {
        return new DocumentSummary(entity.getId(), entity.getPackageId(), entity.getDocumentType(),
                entity.getDocumentNumber(), entity.getOccurredAt(), entity.getGeneratedAt());
    }
}
