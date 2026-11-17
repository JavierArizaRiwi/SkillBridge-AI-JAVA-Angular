package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.application.port.out.event.BusinessEvent;

/**
 * Output port for publishing recommendation-related business events to the message broker.
 * Decouples the application layer from the specific messaging infrastructure (Kafka).
 */
public interface RecommendationEventPublisherPort {
    void publish(BusinessEvent<?> event);
}
