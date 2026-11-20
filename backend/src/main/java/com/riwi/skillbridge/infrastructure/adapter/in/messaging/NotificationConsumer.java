package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.ProcessNotificationUseCase;
import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.PermanentNotificationException;
import com.riwi.skillbridge.infrastructure.config.RabbitConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    private final ProcessNotificationUseCase processNotification;

    public NotificationConsumer(ProcessNotificationUseCase processNotification) {
        this.processNotification = processNotification;
    }

    @RabbitListener(queues = RabbitConfiguration.NOTIFICATION_QUEUE)
    public void onNotification(NotificationMessage message) {
        log.info("Consumed eventId={} bookingId={}", message.eventId(), message.bookingId());
        try {
            processNotification.process(message);
        } catch (PermanentNotificationException e) {
            log.error("Permanent failure processing eventId={}: {}. Rejecting directly to DLQ.",
                message.eventId(), e.getMessage());
            // Indica a RabbitMQ/Spring AMQP que NO reintente y envíe directo a la DLQ
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
