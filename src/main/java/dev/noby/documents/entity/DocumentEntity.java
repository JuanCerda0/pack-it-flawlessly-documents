package dev.noby.documents.entity;

import dev.noby.documents.dto.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "documents")
public class DocumentEntity {

    @Id
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "package_id", nullable = false)
    private Long packageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 20)
    private DocumentType documentType;

    @Column(name = "document_number", nullable = false, unique = true, length = 40)
    private String documentNumber;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "package_snapshot", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> packageSnapshot;

    @Column(name = "pdf_data", nullable = false, columnDefinition = "bytea")
    private byte[] pdfData;

    protected DocumentEntity() {
    }

    public DocumentEntity(Long id, UUID eventId, Long packageId, DocumentType documentType,
                          String documentNumber, Instant occurredAt, Instant generatedAt,
                          Map<String, Object> packageSnapshot, byte[] pdfData) {
        this.id = id;
        this.eventId = eventId;
        this.packageId = packageId;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.occurredAt = occurredAt;
        this.generatedAt = generatedAt;
        this.packageSnapshot = packageSnapshot;
        this.pdfData = pdfData;
    }

    public Long getId() { return id; }
    public UUID getEventId() { return eventId; }
    public Long getPackageId() { return packageId; }
    public DocumentType getDocumentType() { return documentType; }
    public String getDocumentNumber() { return documentNumber; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Map<String, Object> getPackageSnapshot() { return packageSnapshot; }
    public byte[] getPdfData() { return pdfData; }
}
