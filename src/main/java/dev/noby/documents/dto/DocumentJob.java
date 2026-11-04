package dev.noby.documents.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;

public record DocumentJob(UUID eventId, int schemaVersion, Instant occurredAt, Long packageId,
                          DocumentType documentType, @JsonProperty("package") PackageSnapshot packageSnapshot) {
}
