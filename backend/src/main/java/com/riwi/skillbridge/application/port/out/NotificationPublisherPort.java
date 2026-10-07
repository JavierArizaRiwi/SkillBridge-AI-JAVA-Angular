package com.riwi.skillbridge.application.port.out;

public interface NotificationPublisherPort {
    // Publica una notificación de forma asíncrona
    void publish(NotificationMessage message);
}
