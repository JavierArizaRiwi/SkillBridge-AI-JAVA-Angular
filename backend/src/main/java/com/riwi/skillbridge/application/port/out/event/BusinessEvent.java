package com.riwi.skillbridge.application.port.out.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record BusinessEvent<T>(
    UUID eventId,
    String eventType,
    String aggregateId,
    String aggregateType,
    Instant occurredAt,
    String correlationId,
    int version,
    
    @JsonIgnore
    T payload,
    
    String actorUserId,
    String actorUsername,
    String actorRole,
    String action,
    String resource,
    String resourceId
) {
    public BusinessEvent {
        if (eventId == null) throw new IllegalArgumentException("eventId cannot be null");
        if (eventType == null) throw new IllegalArgumentException("eventType cannot be null");
        if (aggregateId == null) throw new IllegalArgumentException("aggregateId cannot be null");
        if (aggregateType == null) throw new IllegalArgumentException("aggregateType cannot be null");
        if (occurredAt == null) throw new IllegalArgumentException("occurredAt cannot be null");
    }

    @JsonProperty("payload")
    public Map<String, Object> getPayloadForAudit() {
        Map<String, Object> map = new HashMap<>();
        if (payload != null) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            try {
                Map<String, Object> payloadMap = mapper.convertValue(payload, Map.class);
                if (payloadMap != null) {
                    map.putAll(payloadMap);
                }
            } catch (Exception e) {
                // If it can't be converted to map, just put it as a field
                map.put("data", payload);
            }
        }
        if (actorUserId != null) map.put("actorUserId", actorUserId);
        if (actorUsername != null) map.put("actorUsername", actorUsername);
        if (actorRole != null) map.put("actorRole", actorRole);
        if (action != null) map.put("action", action);
        if (resource != null) map.put("resource", resource);
        if (resourceId != null) map.put("resourceId", resourceId);
        return map;
    }
}
