package com.riwi.skillbridge.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationMessage(
    UUID eventId,
    UUID bookingId,
    UUID userId,
    NotificationType notificationType,
    Instant occurredAt
) {
    public NotificationMessage {
        Objects.requireNonNull(eventId, "eventId es obligatorio");
        Objects.requireNonNull(bookingId, "bookingId es obligatorio");
        Objects.requireNonNull(userId, "userId es obligatorio");
        Objects.requireNonNull(notificationType, "notificationType es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }

    public static NotificationMessage bookingCreated(UUID bookingId, UUID userId) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.BOOKING_CREATED, Instant.now());
    }

    public static NotificationMessage bookingCancelled(UUID bookingId, UUID userId) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.BOOKING_CANCELLED, Instant.now());
    }
}
