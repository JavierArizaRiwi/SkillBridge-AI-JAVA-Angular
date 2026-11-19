package com.riwi.audit.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessEvent {
    private UUID eventId;
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    private Instant occurredAt;
    private String correlationId;
    private int version;
    private Map<String, Object> payload; // using Map to dynamically read any payload
}
