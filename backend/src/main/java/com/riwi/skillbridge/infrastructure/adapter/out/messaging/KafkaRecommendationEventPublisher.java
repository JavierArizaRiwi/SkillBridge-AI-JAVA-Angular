package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.RecommendationEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka adapter implementing RecommendationEventPublisherPort.
 * Publishes recommendation events to a dedicated topic so they can be
 * consumed independently from booking and audit events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaRecommendationEventPublisher implements RecommendationEventPublisherPort {

    private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate;

    @Value("${app.kafka.topic.recommendation-events}")
    private String topic;

    @Override
    public void publish(BusinessEvent<?> event) {
        log.info("Publishing recommendation event: eventId={}, type={}, aggregateId={}",
                event.eventId(), event.eventType(), event.aggregateId());

        kafkaTemplate.send(topic, event.aggregateId(), event)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Recommendation event published successfully: topic={}, eventId={}",
                                topic, event.eventId());
                    } else {
                        log.error("Failed to publish recommendation event: topic={}, eventId={}",
                                topic, event.eventId(), ex);
                    }
                });
    }
}
