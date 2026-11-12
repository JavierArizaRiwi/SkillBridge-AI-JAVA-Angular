package com.riwi.audit.consumer;

import com.riwi.audit.model.BusinessEvent;
import com.riwi.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAuditConsumer {

    private final AuditEventRepository auditEventRepository;

    @KafkaListener(topics = "${app.kafka.topic.booking-events}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(BusinessEvent event) {
        log.info("[AUDIT SERVICE] Event received - eventId={}, eventType={}, aggregateId={}, correlationId={}",
                event.getEventId(), event.getEventType(), event.getAggregateId(), event.getCorrelationId());
        
        auditEventRepository.save(event);
    }
}
