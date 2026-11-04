package dev.noby.documents.consumer;

import com.rabbitmq.client.Channel;
import dev.noby.documents.config.RabbitDocumentsConfig;
import dev.noby.documents.dto.DocumentJob;
import dev.noby.documents.dto.DocumentType;
import dev.noby.documents.exception.InvalidDocumentJobException;
import dev.noby.documents.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.io.IOException;

@Component
public class DocumentConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentConsumer.class);

    private final DocumentService service;

    public DocumentConsumer(DocumentService service) { this.service = service; }

    @RabbitListener(queues = RabbitDocumentsConfig.INTAKE_QUEUE, ackMode = "MANUAL")
    public void onIntake(Message message, Channel channel) throws IOException {
        process(message, channel, DocumentType.INTAKE);
    }

    @RabbitListener(queues = RabbitDocumentsConfig.DISPATCH_QUEUE, ackMode = "MANUAL")
    public void onDispatch(Message message, Channel channel) throws IOException {
        process(message, channel, DocumentType.DISPATCH);
    }

    private void process(Message message, Channel channel, DocumentType expectedType) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        DocumentJob job;
        try {
            job = service.parse(message.getBody(), expectedType);
        } catch (InvalidDocumentJobException error) {
            log.warn("Invalid {} document job sent to DLQ: {}", expectedType, error.getMessage());
            channel.basicReject(tag, false);
            return;
        }

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                service.record(job);
                channel.basicAck(tag, false);
                return;
            } catch (DataAccessException | TransactionException error) {
                log.warn("Document job {} failed on attempt {}: {}", job.eventId(), attempt,
                        error.getMessage());
                if (attempt == 3) {
                    channel.basicReject(tag, false);
                    return;
                }
                try {
                    Thread.sleep(250L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    channel.basicNack(tag, false, true);
                    return;
                }
            } catch (RuntimeException error) {
                log.error("Unexpected failure for document job {}; sending to DLQ", job.eventId(), error);
                channel.basicReject(tag, false);
                return;
            }
        }
    }
}
