package dev.noby.documents.dto;

import java.time.Instant;

public record DocumentSummary(Long id, Long packageId, DocumentType documentType, String documentNumber,
                              Instant occurredAt, Instant generatedAt) {
}
