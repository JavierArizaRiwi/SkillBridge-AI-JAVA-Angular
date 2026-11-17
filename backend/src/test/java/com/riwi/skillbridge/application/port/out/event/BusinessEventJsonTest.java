package com.riwi.skillbridge.application.port.out.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessEventJsonTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // Same as Spring Boot's ObjectMapper, which the analytics consumer uses to convert the payload.
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Test
    void payloadSurvivesKafkaRoundTripAndConvertsToTypedPayload() throws Exception {
        // Why it matters: analytics consumers read events from Kafka; a null payload silently disabled trending.
        UUID offeringId = UUID.randomUUID();
        BookingCreatedPayload original = new BookingCreatedPayload(
                UUID.randomUUID(), offeringId, UUID.randomUUID(), Instant.parse("2026-12-20T15:00:00Z"), "CREATED");
        BusinessEvent<BookingCreatedPayload> event = new BusinessEvent<>(
                UUID.randomUUID(), "BookingCreated", "agg", "Booking", Instant.now(), "corr", 1, original,
                "actor", "user@x.test", "CUSTOMER", "CREATE", "BOOKING", "res");

        String json = mapper.writeValueAsString(event);
        // Serialized shape must stay unchanged for audit: actor fields merged into payload.
        assertThat(mapper.readTree(json).get("payload").get("action").asText()).isEqualTo("CREATE");
        assertThat(mapper.readTree(json).get("payload").get("offeringId").asText()).isEqualTo(offeringId.toString());
        BusinessEvent<?> read = mapper.readValue(json, BusinessEvent.class);

        assertThat(read.eventType()).isEqualTo("BookingCreated");
        assertThat(read.payload()).isNotNull();
        BookingCreatedPayload typed = mapper.convertValue(read.payload(), BookingCreatedPayload.class);
        assertThat(typed.offeringId()).isEqualTo(offeringId);
        assertThat(typed.bookingId()).isEqualTo(original.bookingId());
    }
}
