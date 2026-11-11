package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.out.RecommendationAnalyticsRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.BookingCancelledPayload;
import com.riwi.skillbridge.application.port.out.event.BookingCreatedPayload;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.RecommendationGeneratedPayload;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.entity.RecommendationEventEntity;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaRecommendationEventRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.riwi.skillbridge.domain.model.RecommendationInputType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/**
 * Kafka consumer for analytics events.
 *
 * Consumer group: skillbridge-analytics-group
 * This is a SEPARATE consumer group from the audit group, so both consumers
 * receive every message independently — analytics and audit don't compete.
 *
 * Handles three event types:
 *   - RecommendationGenerated  → persist event record + increment recommendations counter
 *   - BookingCreated           → increment bookings counter for the offering
 *   - BookingCancelled         → increment cancellations counter for the offering
 *
 * IDEMPOTENCY: before persisting a recommendation event, we check if the
 * eventId (BusinessEvent.eventId) already exists in recommendation_events.
 * Duplicate Kafka deliveries are silently skipped.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaAnalyticsConsumer {

    private final RecommendationAnalyticsRepositoryPort analyticsRepository;
    private final JpaRecommendationEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    // ── Recommendation events ────────────────────────────────────────────────

    @KafkaListener(
            topics = "${app.kafka.topic.recommendation-events}",
            groupId = "skillbridge-analytics-group"
    )
    public void consumeRecommendationEvent(BusinessEvent<?> event) {
        if (!"RecommendationGenerated".equals(event.eventType())) {
            // RecommendationRequested is ignored by analytics — we only count generated ones
            return;
        }

        UUID eventId = event.eventId();

        // Idempotency check: skip if already processed
        if (eventRepository.existsById(eventId)) {
            log.warn("Duplicate recommendation event received, skipping: eventId={}", eventId);
            return;
        }

        log.info("Processing RecommendationGenerated: eventId={}, aggregateId={}",
                eventId, event.aggregateId());

        RecommendationGeneratedPayload payload = extractPayload(event, RecommendationGeneratedPayload.class);
        if (payload == null) {
            log.error("Could not extract RecommendationGeneratedPayload from event: {}", eventId);
            return;
        }

        // Persist the raw event record
        RecommendationEventEntity entity = new RecommendationEventEntity(
                eventId,
                payload.recommendationId(),
                payload.userId(),
                null,                    // offeringId not available at recommendation time
                event.eventType(),
                payload.inputType() != null ? payload.inputType() : RecommendationInputType.TEXT,
                payload.modelUsed(),
                payload.latencyMs(),
                event.occurredAt()
        );
        eventRepository.save(entity);

        // Update daily metric counters
        LocalDate eventDate = toLocalDate(event.occurredAt());
        analyticsRepository.incrementRecommendations(payload.userId(), eventDate);

        log.info("Analytics updated for RecommendationGenerated: userId={}, date={}",
                payload.userId(), eventDate);
    }

    // ── Booking events ───────────────────────────────────────────────────────

    @KafkaListener(
            topics = "${app.kafka.topic.booking-events}",
            groupId = "skillbridge-analytics-group"
    )
    public void consumeBookingEvent(BusinessEvent<?> event) {
        switch (event.eventType()) {
            case "BookingCreated"   -> handleBookingCreated(event);
            case "BookingCancelled" -> handleBookingCancelled(event);
            default -> log.debug("Booking event type not handled by analytics: {}", event.eventType());
        }
    }

    // ── Private handlers ─────────────────────────────────────────────────────

    private void handleBookingCreated(BusinessEvent<?> event) {
        BookingCreatedPayload payload = extractPayload(event, BookingCreatedPayload.class);
        if (payload == null) {
            log.error("Could not extract BookingCreatedPayload: eventId={}", event.eventId());
            return;
        }

        LocalDate eventDate = toLocalDate(event.occurredAt());
        analyticsRepository.incrementBookings(payload.offeringId(), eventDate);
        analyticsRepository.registerUniqueUser(payload.offeringId(), payload.customerId(), eventDate);

        log.info("Analytics updated for BookingCreated: offeringId={}, date={}",
                payload.offeringId(), eventDate);
    }

    private void handleBookingCancelled(BusinessEvent<?> event) {
        BookingCancelledPayload payload = extractPayload(event, BookingCancelledPayload.class);
        if (payload == null) {
            log.error("Could not extract BookingCancelledPayload: eventId={}", event.eventId());
            return;
        }

        LocalDate eventDate = toLocalDate(event.occurredAt());
        analyticsRepository.incrementCancellations(payload.offeringId(), eventDate);

        log.info("Analytics updated for BookingCancelled: offeringId={}, date={}",
                payload.offeringId(), eventDate);
    }

    // ── Utilities ────────────────────────────────────────────────────────────

    /**
     * Extracts and converts the BusinessEvent payload to the expected type.
     * Kafka deserializes the payload as a LinkedHashMap; ObjectMapper converts it to the target class.
     */
    @SuppressWarnings("unchecked")
    private <T> T extractPayload(BusinessEvent<?> event, Class<T> targetType) {
        try {
            Object raw = event.payload();
            if (raw == null) return null;
            if (targetType.isInstance(raw)) return targetType.cast(raw);
            // Payload arrives as Map after JSON deserialization
            if (raw instanceof Map) {
                return objectMapper.convertValue(raw, targetType);
            }
            return objectMapper.convertValue(raw, targetType);
        } catch (Exception e) {
            log.error("Failed to convert payload to {}: {}", targetType.getSimpleName(), e.getMessage());
            return null;
        }
    }

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
