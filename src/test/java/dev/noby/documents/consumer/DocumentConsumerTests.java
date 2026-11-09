package dev.noby.documents.consumer;

import com.rabbitmq.client.Channel;
import dev.noby.documents.dto.DocumentJob;
import dev.noby.documents.dto.DocumentType;
import dev.noby.documents.dto.PackageSnapshot;
import dev.noby.documents.exception.InvalidDocumentJobException;
import dev.noby.documents.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentConsumerTests {

    private final DocumentService service = mock(DocumentService.class);
    private final DocumentConsumer consumer = new DocumentConsumer(service);
    private final Channel channel = mock(Channel.class);

    @Test
    void acknowledgesIntakeAfterPersistence() throws Exception {
        Message message = delivery();
        DocumentJob job = job(DocumentType.INTAKE);
        when(service.parse(message.getBody(), DocumentType.INTAKE)).thenReturn(job);

        consumer.onIntake(message, channel);

        InOrder order = inOrder(service, channel);
        order.verify(service).record(job);
        order.verify(channel).basicAck(17L, false);
    }

    @Test
    void rejectsInvalidDispatchWithoutRetry() throws Exception {
        Message message = delivery();
        when(service.parse(message.getBody(), DocumentType.DISPATCH))
                .thenThrow(new InvalidDocumentJobException("versión inválida"));

        consumer.onDispatch(message, channel);

        verify(service, times(1)).parse(message.getBody(), DocumentType.DISPATCH);
        verify(channel).basicReject(17L, false);
    }

    @Test
    void sendsDatabaseFailureToDlqAfterThreeAttempts() throws Exception {
        Message message = delivery();
        DocumentJob job = job(DocumentType.INTAKE);
        when(service.parse(message.getBody(), DocumentType.INTAKE)).thenReturn(job);
        doThrow(new DataAccessResourceFailureException("postgres unavailable")).when(service).record(job);

        consumer.onIntake(message, channel);

        verify(service, times(3)).record(job);
        verify(channel).basicReject(17L, false);
    }

    private Message delivery() {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(17L);
        return new Message("{}".getBytes(), properties);
    }

    private DocumentJob job(DocumentType type) {
        return new DocumentJob(UUID.randomUUID(), 1, Instant.now(), 42L, type,
                new PackageSnapshot("Paquete", null, type.expectedStatus(), List.of()));
    }
}
