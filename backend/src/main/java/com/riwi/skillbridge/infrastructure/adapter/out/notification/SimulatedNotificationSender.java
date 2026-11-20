package com.riwi.skillbridge.infrastructure.adapter.out.notification;

import com.riwi.skillbridge.application.port.out.NotificationMessage;
import com.riwi.skillbridge.application.port.out.NotificationSenderPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notifications.sender", havingValue = "simulated", matchIfMissing = true)
public class SimulatedNotificationSender implements NotificationSenderPort {

    private static final Logger log = LoggerFactory.getLogger(SimulatedNotificationSender.class);

    @Override
    public void send(NotificationMessage message) {
        // Punto de extensión: email, WhatsApp, push, etc.
        log.info("[SIMULATED NOTIFICATION] type={} userId={} bookingId={} eventId={}",
            message.notificationType(), message.userId(),
            message.bookingId(), message.eventId());
    }
}
