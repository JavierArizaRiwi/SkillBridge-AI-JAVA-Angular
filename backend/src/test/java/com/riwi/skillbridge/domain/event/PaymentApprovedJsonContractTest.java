package com.riwi.skillbridge.domain.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fija el JSON de docs/contracts/mentorships/events.md §2 (lo consume el servicio NestJS). */
class PaymentApprovedJsonContractTest {

    // Misma configuración relevante que el ObjectMapper de Spring Boot.
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void el_json_v2_contiene_exactamente_los_campos_del_contrato() throws Exception {
        PaymentApproved e = new PaymentApproved(2, UUID.randomUUID(), Instant.parse("2026-11-05T14:00:00Z"), "corr-1",
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "ana@test.com",
            UUID.randomUUID(), "Java", UUID.randomUUID(), Instant.parse("2026-11-05T15:00:00Z"), 2, MentorshipMode.LIVE);

        JsonNode json = mapper.readTree(mapper.writeValueAsString(e));

        List<String> expected = List.of("version", "eventId", "occurredAt", "correlationId", "bookingId", "paymentId",
            "customerId", "customerEmail", "offeringId", "offeringTitle", "providerId", "scheduledAt",
            "durationHours", "mode");
        for (String field : expected) assertTrue(json.has(field), "falta el campo " + field);
        assertEquals(expected.size(), json.size(), "campos no previstos por el contrato: " + json.fieldNames());
        assertFalse(json.has("enriched"), "hasFullMentorshipState() no debe filtrarse al JSON");
        assertEquals(2, json.get("version").asInt());
        assertEquals("LIVE", json.get("mode").asText());
        assertEquals("2026-11-05T15:00:00Z", json.get("scheduledAt").asText());
        assertEquals(2, json.get("durationHours").asInt());
    }

    @Test
    void un_mensaje_v1_solo_con_booking_id_sigue_deserializandose_en_el_consumidor_del_monolito() throws Exception {
        UUID bookingId = UUID.randomUUID();

        PaymentApproved e = mapper.readValue("{\"bookingId\":\"" + bookingId + "\"}", PaymentApproved.class);

        assertEquals(bookingId, e.bookingId());
        assertFalse(e.hasFullMentorshipState());
    }

    @Test
    void ida_y_vuelta_v2_conserva_los_datos() throws Exception {
        PaymentApproved e = new PaymentApproved(2, UUID.randomUUID(), Instant.parse("2026-11-05T14:00:00Z"), "c",
            UUID.randomUUID(), null, UUID.randomUUID(), "a@b.co", UUID.randomUUID(), "T", UUID.randomUUID(),
            Instant.parse("2026-11-05T15:00:00Z"), 3, MentorshipMode.ASYNCHRONOUS);

        PaymentApproved back = mapper.readValue(mapper.writeValueAsString(e), PaymentApproved.class);

        assertEquals(e, back);
        assertTrue(back.hasFullMentorshipState());
    }
}
