package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitNotificationPublisherTest {

    @Test
    void shouldPublishToCorrectExchangeAndRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitNotificationPublisher publisher = new RabbitNotificationPublisher(rabbitTemplate);
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());

        publisher.publish(message);

        ArgumentCaptor<CorrelationData> correlation = ArgumentCaptor.forClass(CorrelationData.class);
        verify(rabbitTemplate).convertAndSend(
            eq(RabbitConfiguration.NOTIFICATION_EXCHANGE),
            eq(RabbitConfiguration.NOTIFICATION_BOOKING_CREATED_KEY),
            eq(message),
            correlation.capture());
        assertEquals(message.eventId().toString(), correlation.getValue().getId());
    }

    @Test
    void shouldPublishCancellationToCancellationRoutingKey() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitNotificationPublisher publisher = new RabbitNotificationPublisher(rabbitTemplate);
        NotificationMessage message = NotificationMessage.bookingCancelled(UUID.randomUUID(), UUID.randomUUID());

        publisher.publish(message);

        verify(rabbitTemplate).convertAndSend(
            eq(RabbitConfiguration.NOTIFICATION_EXCHANGE),
            eq(RabbitConfiguration.NOTIFICATION_BOOKING_CANCELLED_KEY),
            eq(message),
            any(CorrelationData.class));
    }

    @Test
    void shouldNotPropagateBrokerFailure() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        RabbitNotificationPublisher publisher = new RabbitNotificationPublisher(rabbitTemplate);
        NotificationMessage message = NotificationMessage.bookingCreated(UUID.randomUUID(), UUID.randomUUID());
        doThrow(new AmqpConnectException(new RuntimeException("broker down")))
            .when(rabbitTemplate)
            .convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertDoesNotThrow(() -> publisher.publish(message));
    }
}
