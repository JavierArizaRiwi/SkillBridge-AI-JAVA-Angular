package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.riwi.skillbridge.application.port.out.RecommendationAnalyticsRepositoryPort;
import com.riwi.skillbridge.application.port.out.event.BookingCancelledPayload;
import com.riwi.skillbridge.application.port.out.event.BookingCreatedPayload;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.port.out.event.RecommendationGeneratedPayload;
import com.riwi.skillbridge.domain.model.RecommendationInputType;
import com.riwi.skillbridge.infrastructure.adapter.out.persistence.repository.JpaRecommendationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KafkaAnalyticsConsumer.
 *
 * All external dependencies (repository, JPA) are mocked — no Kafka broker needed.
 * Each test validates one specific behavior of the consumer.
 */
class KafkaAnalyticsConsumerTest {

    private KafkaAnalyticsConsumer consumer;
    private RecommendationAnalyticsRepositoryPort analyticsRepository;
    private JpaRecommendationEventRepository eventRepository;

    @BeforeEach
    void setUp() {
        analyticsRepository = mock(RecommendationAnalyticsRepositoryPort.class);
        eventRepository     = mock(JpaRecommendationEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new KafkaAnalyticsConsumer(analyticsRepository, eventRepository, objectMapper);
    }

    // ── RecommendationGenerated ───────────────────────────────────────────────

    @Test
    void shouldPersistEventAndIncrementRecommendationsOnGenerated() {
        // What it validates: when a valid RecommendationGenerated event arrives,
        // the consumer persists the raw event AND increments the recommendations counter.
        // Why it matters: this is the primary data ingestion path for analytics.
        UUID eventId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();

        when(eventRepository.existsById(eventId)).thenReturn(false);

        consumer.consumeRecommendationEvent(recommendationGeneratedEvent(eventId, userId));

        verify(eventRepository).save(any());
        verify(analyticsRepository).incrementRecommendations(eq(userId), any(LocalDate.class));
    }

    @Test
    void shouldSkipDuplicateRecommendationEvent() {
        // What it validates: if the same eventId is received twice (Kafka at-least-once delivery),
        // the second message is ignored — no double counting.
        // Why it matters: idempotency is critical; duplicate events would inflate metrics.
        UUID eventId = UUID.randomUUID();

        when(eventRepository.existsById(eventId)).thenReturn(true);

        consumer.consumeRecommendationEvent(recommendationGeneratedEvent(eventId, UUID.randomUUID()));

        verify(eventRepository, never()).save(any());
        verify(analyticsRepository, never()).incrementRecommendations(any(), any());
    }

    @Test
    void shouldIgnoreRecommendationRequestedEvents() {
        // What it validates: RecommendationRequested events are intentionally ignored.
        // We only count RecommendationGenerated (confirmed AI responses, not just intents).
        // Why it matters: counting requests would inflate metrics with abandoned calls.
        UUID eventId = UUID.randomUUID();
        BusinessEvent<RecommendationGeneratedPayload> event = buildEvent(
                eventId, "RecommendationRequested",
                new RecommendationGeneratedPayload(
                        UUID.randomUUID(), UUID.randomUUID(), "goal",
                        RecommendationInputType.TEXT, "gemini", 200L)
        );

        consumer.consumeRecommendationEvent(event);

        verifyNoInteractions(eventRepository);
        verifyNoInteractions(analyticsRepository);
    }

    // ── BookingCreated ────────────────────────────────────────────────────────

    @Test
    void shouldIncrementBookingsAndRegisterUserOnBookingCreated() {
        // What it validates: a BookingCreated event increments bookings AND registers the user
        // as a unique visitor for that offering on that day.
        // Why it matters: both counters feed into conversionRate and uniqueUsers metrics.
        UUID offeringId  = UUID.randomUUID();
        UUID customerId  = UUID.randomUUID();

        consumer.consumeBookingEvent(bookingCreatedEvent(offeringId, customerId));

        verify(analyticsRepository).incrementBookings(eq(offeringId), any(LocalDate.class));
        verify(analyticsRepository).registerUniqueUser(eq(offeringId), eq(customerId), any(LocalDate.class));
    }

    @Test
    void shouldNotTouchRecommendationRepositoryOnBookingCreated() {
        // What it validates: booking events do not touch the recommendation event table.
        // Why it matters: clean separation — bookings update metrics only, not raw event log.
        consumer.consumeBookingEvent(bookingCreatedEvent(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(eventRepository);
    }

    // ── BookingCancelled ──────────────────────────────────────────────────────

    @Test
    void shouldIncrementCancellationsOnBookingCancelled() {
        // What it validates: a BookingCancelled event increments the cancellations counter
        // for the correct offering on the correct day.
        // Why it matters: cancellations reduce effectiveBookings and therefore conversionRate.
        UUID offeringId = UUID.randomUUID();

        consumer.consumeBookingEvent(bookingCancelledEvent(offeringId));

        verify(analyticsRepository).incrementCancellations(eq(offeringId), any(LocalDate.class));
    }

    @Test
    void shouldNotIncrementBookingsOnCancellation() {
        // What it validates: a cancellation does NOT also increment the bookings counter.
        // Why it matters: double-counting would make cancellations invisible in the metrics.
        consumer.consumeBookingEvent(bookingCancelledEvent(UUID.randomUUID()));

        verify(analyticsRepository, never()).incrementBookings(any(), any());
        verify(analyticsRepository, never()).registerUniqueUser(any(), any(), any());
    }

    @Test
    void shouldIgnoreUnknownBookingEventTypes() {
        // What it validates: unknown event types (e.g. BookingConfirmed) are silently ignored.
        // Why it matters: the consumer must be resilient to new event types being added.
        BusinessEvent<BookingCreatedPayload> unknown = buildEvent(
                UUID.randomUUID(), "BookingConfirmed",
                new BookingCreatedPayload(UUID.randomUUID(), UUID.randomUUID(),
                        UUID.randomUUID(), Instant.now(), "CONFIRMED")
        );

        consumer.consumeBookingEvent(unknown);

        verifyNoInteractions(analyticsRepository);
    }

    // ── test data builders ────────────────────────────────────────────────────

    private BusinessEvent<RecommendationGeneratedPayload> recommendationGeneratedEvent(
            UUID eventId, UUID userId) {
        return buildEvent(eventId, "RecommendationGenerated",
                new RecommendationGeneratedPayload(
                        UUID.randomUUID(), userId, "quiero aprender Java",
                        RecommendationInputType.TEXT, "gemini-3.8-flash", 450L));
    }

    private BusinessEvent<BookingCreatedPayload> bookingCreatedEvent(UUID offeringId, UUID customerId) {
        return buildEvent(UUID.randomUUID(), "BookingCreated",
                new BookingCreatedPayload(UUID.randomUUID(), offeringId, customerId,
                        Instant.now(), "CREATED"));
    }

    private BusinessEvent<BookingCancelledPayload> bookingCancelledEvent(UUID offeringId) {
        return buildEvent(UUID.randomUUID(), "BookingCancelled",
                new BookingCancelledPayload(UUID.randomUUID(), offeringId,
                        UUID.randomUUID(), "CANCELLED"));
    }

    private <T> BusinessEvent<T> buildEvent(UUID eventId, String eventType, T payload) {
        return new BusinessEvent<>(
                eventId, eventType,
                UUID.randomUUID().toString(), "Aggregate",
                Instant.now(), UUID.randomUUID().toString(),
                1, payload,
                UUID.randomUUID().toString(), "user@test.com",
                "CUSTOMER", "ACTION", "RESOURCE",
                UUID.randomUUID().toString()
        );
    }
}
