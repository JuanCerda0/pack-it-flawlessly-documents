package dev.noby.documents.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableRabbit
public class RabbitDocumentsConfig {

    public static final String INTAKE_QUEUE = "documents.intake";
    public static final String DISPATCH_QUEUE = "documents.dispatch";

    @Bean
    Declarables documentsTopology() {
        DirectExchange commands = new DirectExchange("packit.document.commands", true, false);
        DirectExchange deadLetters = new DirectExchange("packit.dead.letters", true, false);
        List<Declarable> definitions = new ArrayList<>(List.of(commands, deadLetters));
        addRoute(definitions, commands, deadLetters, INTAKE_QUEUE, "document.intake");
        addRoute(definitions, commands, deadLetters, DISPATCH_QUEUE, "document.dispatch");
        return new Declarables(definitions);
    }

    private void addRoute(List<Declarable> definitions, DirectExchange commands, DirectExchange deadLetters,
                          String queueName, String routingKey) {
        Queue work = QueueBuilder.durable(queueName)
                .withArgument("x-queue-type", "classic")
                .deadLetterExchange(deadLetters.getName())
                .deadLetterRoutingKey(queueName + ".dlq")
                .build();
        Queue dlq = QueueBuilder.durable(queueName + ".dlq")
                .withArgument("x-queue-type", "classic")
                .build();
        definitions.addAll(List.of(work, dlq,
                BindingBuilder.bind(work).to(commands).with(routingKey),
                BindingBuilder.bind(dlq).to(deadLetters).with(dlq.getName())));
    }
}
