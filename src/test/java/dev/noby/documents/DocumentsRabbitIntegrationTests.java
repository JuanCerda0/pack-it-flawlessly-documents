package dev.noby.documents;

import dev.noby.documents.entity.DocumentEntity;
import dev.noby.documents.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfSystemProperty(named = "documents.rabbit.integration", matches = "true")
class DocumentsRabbitIntegrationTests {

    @Autowired RabbitTemplate rabbitTemplate;
    @Autowired DocumentRepository repository;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void consumesBothQueuesAndDeduplicatesRedelivery() throws InterruptedException {
        UUID intakeId = UUID.randomUUID();
        UUID dispatchId = UUID.randomUUID();
        try {
            send("document.intake", payload(intakeId, "INTAKE", "PREPARING"));
            send("document.intake", payload(intakeId, "INTAKE", "PREPARING"));
            send("document.dispatch", payload(dispatchId, "DISPATCH", "READY"));

            for (int attempt = 0; attempt < 100
                    && (!repository.existsByEventId(intakeId) || !repository.existsByEventId(dispatchId)); attempt++) {
                Thread.sleep(100);
            }
            assertThat(repository.existsByEventId(intakeId)).isTrue();
            assertThat(repository.existsByEventId(dispatchId)).isTrue();
            DocumentEntity intake = find(intakeId);
            DocumentEntity dispatch = find(dispatchId);
            assertThat(intake.getDocumentNumber()).startsWith("ING-");
            assertThat(dispatch.getDocumentNumber()).startsWith("DES-");
            assertThat(intake.getPdfData()).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
            assertThat(dispatch.getPdfData()).startsWith("%PDF-".getBytes(StandardCharsets.US_ASCII));
            for (int attempt = 0; attempt < 50 && pendingIntake() != 0; attempt++) {
                Thread.sleep(100);
            }
            assertThat(repository.findByPackageIdOrderByGeneratedAtAscIdAsc(987654321L).stream()
                    .filter(document -> document.getEventId().equals(intakeId))).hasSize(1);
        } finally {
            repository.findAll().stream()
                    .filter(document -> document.getEventId().equals(intakeId)
                            || document.getEventId().equals(dispatchId))
                    .forEach(document -> repository.deleteById(document.getId()));
        }
    }

    @Test
    void routesUnsupportedSchemaVersionToIntakeDlq() throws InterruptedException {
        int before = queueCount("documents.intake.dlq");
        String invalid = payload(UUID.randomUUID(), "INTAKE", "PREPARING")
                .replace("\"schemaVersion\":1", "\"schemaVersion\":2");

        send("document.intake", invalid);

        for (int attempt = 0; attempt < 50 && queueCount("documents.intake.dlq") == before; attempt++) {
            Thread.sleep(100);
        }
        assertThat(queueCount("documents.intake.dlq")).isEqualTo(before + 1);
    }

    private DocumentEntity find(UUID eventId) {
        return repository.findAll().stream()
                .filter(document -> document.getEventId().equals(eventId))
                .findFirst().orElseThrow();
    }

    private int pendingIntake() {
        return queueCount("documents.intake");
    }

    private int queueCount(String name) {
        return rabbitTemplate.execute(channel -> channel.queueDeclarePassive(name).getMessageCount());
    }

    private void send(String routingKey, String json) {
        rabbitTemplate.send("packit.document.commands", routingKey,
                new Message(json.getBytes(StandardCharsets.UTF_8), new MessageProperties()));
    }

    private String payload(UUID eventId, String type, String status) {
        return """
                {"eventId":"%s","schemaVersion":1,"occurredAt":"%s","packageId":987654321,
                 "documentType":"%s","package":{"name":"Prueba RabbitMQ","reference":"TEST-987",
                 "status":"%s","items":[]}}
                """.formatted(eventId, Instant.now(), type, status);
    }
}
