package com.riwi.skillbridge.application.port.out;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationMessageTest {

    @Test
    void shouldBuildValidBookingCreatedMessage() {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationMessage message = NotificationMessage.bookingCreated(bookingId, userId);

        assertNotNull(message.eventId());
        assertEquals(bookingId, message.bookingId());
        assertEquals(userId, message.userId());
        assertEquals(NotificationType.BOOKING_CREATED, message.notificationType());
        assertNotNull(message.occurredAt());
    }

    @Test
    void shouldGenerateDifferentEventIdForEachMessage() {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationMessage first = NotificationMessage.bookingCreated(bookingId, userId);
        NotificationMessage second = NotificationMessage.bookingCreated(bookingId, userId);

        assertNotEquals(first.eventId(), second.eventId());
    }

    @Test
    void shouldBuildValidBookingCancelledMessage() {
        UUID bookingId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        NotificationMessage message = NotificationMessage.bookingCancelled(bookingId, userId);

        assertNotNull(message.eventId());
        assertEquals(bookingId, message.bookingId());
        assertEquals(userId, message.userId());
        assertEquals(NotificationType.BOOKING_CANCELLED, message.notificationType());
        assertNotNull(message.occurredAt());
    }

    @Test
    void shouldRejectMissingFields() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () ->
            new NotificationMessage(null, id, id, NotificationType.BOOKING_CREATED, now));
        assertThrows(NullPointerException.class, () ->
            new NotificationMessage(id, null, id, NotificationType.BOOKING_CREATED, now));
        assertThrows(NullPointerException.class, () ->
            new NotificationMessage(id, id, null, NotificationType.BOOKING_CREATED, now));
        assertThrows(NullPointerException.class, () ->
            new NotificationMessage(id, id, id, null, now));
        assertThrows(NullPointerException.class, () ->
            new NotificationMessage(id, id, id, NotificationType.BOOKING_CREATED, null));
    }
}
