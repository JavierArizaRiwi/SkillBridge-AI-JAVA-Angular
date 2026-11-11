package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationSenderPort;
import com.riwi.skillbridge.application.service.NotificationService;
import com.riwi.skillbridge.infrastructure.adapter.out.messaging.RabbitNotificationPublisher;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * HU-12: publisher -> exchange -> queue -> consumer, retry y DLQ contra un RabbitMQ real.
 * Levanta solo la parte de mensajería (sin Postgres, Redis ni seguridad).
 * La configuración de ack, retry y DLX sale del application.yml real;
 * solo se acorta el intervalo de retry para que el test sea rápido.
 */
@SpringBootTest(
    classes = {
        JacksonAutoConfiguration.class,
        RabbitAutoConfiguration.class,
        RabbitConfiguration.class,
        RabbitNotificationPublisher.class,
        NotificationConsumer.class,
        NotificationService.class
    },
    properties = "spring.rabbitmq.listener.simple.retry.initial-interval=100ms")
@org.junit.jupiter.api.Disabled("Docker Bug Testcontainers")
@Testcontainers
class NotificationFlowIT {

    @Container
    static final RabbitMQContainer RABBIT =
        new RabbitMQContainer(DockerImageName.parse("rabbitmq:4-management-alpine"));

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.addresses",
            () -> "amqp://guest:guest@%s:%d".formatted(RABBIT.getHost(), RABBIT.getAmqpPort()));
    }

    @MockitoBean
    NotificationSenderPort sender;

    @Autowired
    NotificationPublisherPort publisher;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @Autowired
    AmqpAdmin amqpAdmin;

    @BeforeEach
    void cleanQueues() {
        amqpAdmin.purgeQueue(RabbitConfiguration.NOTIFICATION_QUEUE);
        amqpAdmin.purgeQueue(RabbitConfiguration.NOTIFICATION_DLQ);
    }

    @Test
    void shouldDeliverMessageFromPublisherToConsumer() {
        NotificationMessage message = newMessage();

        publisher.publish(message);

        verify(sender, timeout(5000)).send(argThat(m -> m.eventId().equals(message.eventId())));
    }

    @Test
    void shouldRetryFailingConsumerAndSucceedWithoutDeadLettering() {
        doThrow(new IllegalStateException("fallo 1"))
            .doThrow(new IllegalStateException("fallo 2"))
            .doNothing()
            .when(sender).send(any());

        publisher.publish(newMessage());

        verify(sender, timeout(10000).times(3)).send(any());
        assertNull(rabbitTemplate.receive(RabbitConfiguration.NOTIFICATION_DLQ, 1000),
            "Un mensaje que termina bien tras reintentar no debe llegar a la DLQ");
    }

    @Test
    void shouldSendMessageToDlqWhenRetriesAreExhausted() {
        doThrow(new IllegalStateException("fallo permanente")).when(sender).send(any());

        publisher.publish(newMessage());

        Message dead = rabbitTemplate.receive(RabbitConfiguration.NOTIFICATION_DLQ, 10000);
        assertNotNull(dead, "El mensaje debía terminar en notification.dlq");
        verify(sender, times(3)).send(any());
    }

    @Test
    void shouldRejectInvalidMessageToDlq() {
        // Falta bookingId: el record lo rechaza al deserializar
        String invalidJson = """
                {"eventId":"%s","userId":"%s","notificationType":"BOOKING_CREATED","occurredAt":"2026-10-07T00:00:00Z"}
                """.formatted(UUID.randomUUID(), UUID.randomUUID());
        Message invalid = MessageBuilder.withBody(invalidJson.getBytes(StandardCharsets.UTF_8))
            .setContentType("application/json")
            .build();

        rabbitTemplate.send(RabbitConfiguration.NOTIFICATION_EXCHANGE,
            RabbitConfiguration.NOTIFICATION_BOOKING_CREATED_KEY, invalid);

        Message dead = rabbitTemplate.receive(RabbitConfiguration.NOTIFICATION_DLQ, 10000);
        assertNotNull(dead, "El mensaje inválido debía terminar en notification.dlq");
        verifyNoInteractions(sender);
    }

    private NotificationMessage newMessage() {
        return NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());
    }
}

