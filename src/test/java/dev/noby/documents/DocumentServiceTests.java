package dev.noby.documents;

import dev.noby.documents.dto.DocumentJob;
import dev.noby.documents.dto.DocumentSummary;
import dev.noby.documents.dto.DocumentType;
import dev.noby.documents.exception.InvalidDocumentJobException;
import dev.noby.documents.service.DocumentService;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Transactional
class DocumentServiceTests {

    @Autowired DocumentService service;
    @Autowired EntityManager entityManager;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void storesIntakePdfOnceWithSnapshot() throws IOException {
        UUID eventId = UUID.randomUUID();
        DocumentJob job = service.parse(payload(eventId, "INTAKE", "PREPARING", """
                [{"sku":"TAZA-01","productName":"Taza azul","quantity":2}]
                """).getBytes(StandardCharsets.UTF_8), DocumentType.INTAKE);

        service.record(job);
        service.record(job);
        entityManager.flush();
        entityManager.clear();

        List<DocumentSummary> documents = service.listByPackage(42L);
        assertThat(documents.stream().filter(document -> document.documentNumber().startsWith("ING-")))
                .hasSize(1);
        DocumentSummary created = documents.stream()
                .filter(document -> document.documentNumber().startsWith("ING-"))
                .findFirst().orElseThrow();
        byte[] bytes = service.getFile(created.id()).pdfData();
        assertThat(bytes).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("Comprobante de ingreso", created.documentNumber(), "Taza azul", "TAZA-01");
        }
    }

    @Test
    void acceptsEmptyDispatchAndRejectsWrongQueue() throws IOException {
        byte[] body = payload(UUID.randomUUID(), "DISPATCH", "READY", "[]")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidDocumentJobException.class, () -> service.parse(body, DocumentType.INTAKE));

        service.record(service.parse(body, DocumentType.DISPATCH));
        entityManager.flush();
        entityManager.clear();
        DocumentSummary created = service.listByPackage(42L).stream()
                .filter(document -> document.documentNumber().startsWith("DES-"))
                .findFirst().orElseThrow();
        try (PDDocument pdf = Loader.loadPDF(service.getFile(created.id()).pdfData())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Sin productos registrados");
        }
    }

    @Test
    void rejectsUnknownSchemaVersion() {
        byte[] body = payload(UUID.randomUUID(), "INTAKE", "PREPARING", "[]")
                .replace("\"schemaVersion\":1", "\"schemaVersion\":2")
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidDocumentJobException.class, () -> service.parse(body, DocumentType.INTAKE));
    }

    private String payload(UUID eventId, String type, String status, String items) {
        return """
                {"eventId":"%s","schemaVersion":1,"occurredAt":"2026-11-04T12:00:00Z",
                 "packageId":42,"documentType":"%s",
                 "package":{"name":"Paquete de prueba","reference":"PF-42","status":"%s","items":%s}}
                """.formatted(eventId, type, status, items);
    }
}
