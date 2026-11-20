package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationPublisherPort;
import com.riwi.skillbridge.application.port.out.NotificationType;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitNotificationPublisher implements NotificationPublisherPort {

    private static final Logger log = LoggerFactory.getLogger(RabbitNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitNotificationPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(NotificationMessage message) {
        try {
            String routingKey = routingKeyFor(message.notificationType());
            rabbitTemplate.convertAndSend(
                RabbitConfiguration.NOTIFICATION_EXCHANGE,
                routingKey,
                message,
                new CorrelationData(message.eventId().toString()));
            log.info("Published eventId={} bookingId={} exchange={} routingKey={}",
                message.eventId(), message.bookingId(),
                RabbitConfiguration.NOTIFICATION_EXCHANGE,
                routingKey);
        } catch (AmqpException e) {
            // La reserva ya fue guardada: un fallo del broker no debe romperla
            log.error("Could not publish notification eventId={} bookingId={}",
                message.eventId(), message.bookingId(), e);
        }
    }

    private String routingKeyFor(NotificationType notificationType) {
        return switch (notificationType) {
            case BOOKING_CREATED -> RabbitConfiguration.NOTIFICATION_BOOKING_CREATED_KEY;
            case BOOKING_CANCELLED -> RabbitConfiguration.NOTIFICATION_BOOKING_CANCELLED_KEY;
        };
    }
}
