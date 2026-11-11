package com.riwi.audit.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato de tolerancia del audit-service (docs/contracts/mentorships/events.md §1 y §3). */
class BusinessEventDeserializationTest {

    // Sin FAIL_ON_UNKNOWN_PROPERTIES desactivado: la tolerancia debe venir del propio modelo.
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final String ID = "6f1c1c3e-6f0e-4b7e-9a57-0c1b2d3e4f50";

    @Test
    void acepta_evento_de_mentoria_con_actor_en_primer_nivel_y_lo_pliega_al_payload() throws Exception {
        String json = """
            {"eventId":"%s","eventType":"MentorshipProvisioned","aggregateId":"a-1",
             "aggregateType":"Mentorship","occurredAt":"2026-11-05T14:00:00Z","correlationId":"c-1","version":1,
             "actorUserId":"SYSTEM","actorRole":"SYSTEM","action":"PROVISION","resource":"MENTORSHIP","resourceId":"a-1",
             "payload":{"assignmentId":"a-1","mode":"LIVE"}}
            """.formatted(ID);

        BusinessEvent e = mapper.readValue(json, BusinessEvent.class);

        assertThat(e.getEventType()).isEqualTo("MentorshipProvisioned");
        assertThat(e.getAggregateType()).isEqualTo("Mentorship");
        assertThat(e.getPayload())
            .containsEntry("assignmentId", "a-1")
            .containsEntry("mode", "LIVE")
            .containsEntry("actorUserId", "SYSTEM")
            .containsEntry("action", "PROVISION")
            .containsEntry("resource", "MENTORSHIP");
    }

    @Test
    void el_orden_de_los_campos_no_importa() throws Exception {
        String json = """
            {"actorRole":"PROVIDER","payload":{"x":1},"eventId":"%s","eventType":"MentorshipCancelled",
             "aggregateId":"a-1","aggregateType":"Mentorship","occurredAt":"2026-11-05T14:00:00Z","version":1}
            """.formatted(ID);

        BusinessEvent e = mapper.readValue(json, BusinessEvent.class);

        assertThat(e.getPayload()).containsEntry("x", 1).containsEntry("actorRole", "PROVIDER");
    }

    @Test
    void el_formato_del_monolito_con_actor_duplicado_no_se_altera() throws Exception {
        String json = """
            {"eventId":"%s","eventType":"BookingCreated","aggregateId":"b-1","aggregateType":"Booking",
             "occurredAt":"2026-11-05T14:00:00Z","correlationId":"c","version":1,
             "actorUserId":"top","payload":{"actorUserId":"original","bookingId":"b-1"}}
            """.formatted(ID);

        BusinessEvent e = mapper.readValue(json, BusinessEvent.class);

        assertThat(e.getPayload()).containsEntry("actorUserId", "original").containsEntry("bookingId", "b-1");
    }

    @Test
    void ignora_campos_desconocidos_y_tipos_de_evento_futuros() throws Exception {
        String json = """
            {"eventId":"%s","eventType":"SomethingNotInventedYet","aggregateId":"z","aggregateType":"Zeta",
             "occurredAt":"2026-11-05T14:00:00Z","version":7,"brandNewField":{"a":1},"payload":{}}
            """.formatted(ID);

        BusinessEvent e = mapper.readValue(json, BusinessEvent.class);

        assertThat(e.getEventType()).isEqualTo("SomethingNotInventedYet");
        assertThat(e.getVersion()).isEqualTo(7);
    }

    @Test
    void evento_sin_payload_ni_actor_conserva_payload_nulo() throws Exception {
        String json = """
            {"eventId":"%s","eventType":"Ping","aggregateId":"z","aggregateType":"Zeta","occurredAt":"2026-11-05T14:00:00Z"}
            """.formatted(ID);

        assertThat(mapper.readValue(json, BusinessEvent.class).getPayload()).isNull();
    }
}
