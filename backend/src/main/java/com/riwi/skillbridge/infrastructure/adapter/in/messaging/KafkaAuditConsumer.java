package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaAuditConsumer {

    @KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(BusinessEvent<?> event) {
        CorrelationIdHolder.set(event.correlationId());
        MDC.put("correlationId", event.correlationId());
        try {
            log.info("Evento de negocio recibido: eventId={}, eventType={}, aggregateId={}, payload={}",
                event.eventId(), event.eventType(), event.aggregateId(), event.payload());
        } finally {
            CorrelationIdHolder.clear();
            MDC.remove("correlationId");
        }
    }
}
