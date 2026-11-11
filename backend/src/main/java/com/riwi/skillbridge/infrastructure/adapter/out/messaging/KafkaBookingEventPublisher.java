package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.BookingEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaBookingEventPublisher implements BookingEventPublisherPort {

    private final KafkaTemplate<String, BusinessEvent<?>> kafkaTemplate;

    @Value("${app.kafka.topic.booking-events}")
    private String topic;

    @Override
    public void publish(BusinessEvent<?> event) {
        CompletableFuture.runAsync(() -> {
            log.info("Publicando evento de reserva en Kafka - eventId: {}, eventType: {}, aggregateId: {}, correlationId: {}",
                    event.eventId(), event.eventType(), event.aggregateId(), event.correlationId());
            try {
                kafkaTemplate.send(topic, event.aggregateId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            log.info("Evento de reserva publicado exitosamente en el topic {}: {}", topic, event.eventId());
                        } else {
                            log.error("Error publicando evento de reserva en el topic {}: {}", topic, event.eventId(), ex);
                        }
                    });
            } catch (Exception e) {
                log.error("Kafka inaccesible para reservas: {}", e.getMessage());
            }
        });
    }
}
