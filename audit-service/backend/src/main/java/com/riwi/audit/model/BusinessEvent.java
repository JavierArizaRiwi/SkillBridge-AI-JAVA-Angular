package com.riwi.audit.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Documento de auditoría persistido en MongoDB (colección {@code audit_events}).
 * Mismo contrato de campos que antes (cuando vivía solo en memoria), para no
 * romper el JSON que ya consume {@code audit-frontend}.
 *
 * <p>Es deliberadamente tolerante (ADR-0006 §6, docs/contracts/mentorships/events.md): cualquier
 * {@code eventType} es válido, los campos desconocidos se ignoran y los campos de actor
 * ({@code actorUserId}, {@code actorUsername}, {@code actorRole}, {@code action}, {@code resource},
 * {@code resourceId}) que lleguen en el primer nivel del envelope se pliegan dentro de {@code payload}
 * sin pisar los que ya vengan allí (el monolito los duplica en ambos sitios; otros productores, no).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document(collection = "audit_events")
public class BusinessEvent {
    @Id
    private UUID eventId;
    @Indexed
    private String eventType;
    private String aggregateId;
    private String aggregateType;
    @Indexed(name = "occurredAt_idx")
    private Instant occurredAt;
    private String correlationId;
    private int version;
    private Map<String, Object> payload; // using Map to dynamically read any payload

    /** Fusiona sin pisar: los valores ya presentes en el payload original mandan. */
    public void setPayload(Map<String, Object> incoming) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (incoming != null) merged.putAll(incoming);
        if (this.payload != null) this.payload.forEach(merged::putIfAbsent);
        this.payload = merged.isEmpty() && incoming == null ? null : merged;
    }

    @JsonProperty("actorUserId")
    public void setActorUserId(String value) { fold("actorUserId", value); }

    @JsonProperty("actorUsername")
    public void setActorUsername(String value) { fold("actorUsername", value); }

    @JsonProperty("actorRole")
    public void setActorRole(String value) { fold("actorRole", value); }

    @JsonProperty("action")
    public void setAction(String value) { fold("action", value); }

    @JsonProperty("resource")
    public void setResource(String value) { fold("resource", value); }

    @JsonProperty("resourceId")
    public void setResourceId(String value) { fold("resourceId", value); }

    private void fold(String key, String value) {
        if (value == null) return;
        if (payload == null) payload = new LinkedHashMap<>();
        payload.putIfAbsent(key, value);
    }
}
