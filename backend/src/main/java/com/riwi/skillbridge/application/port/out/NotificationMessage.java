package com.riwi.skillbridge.application.port.out;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationMessage(
    UUID eventId,
    UUID bookingId,
    UUID userId,
    NotificationType notificationType,
    Instant occurredAt,
    Instant paymentDeadline
) {
    public NotificationMessage {
        Objects.requireNonNull(eventId, "eventId es obligatorio");
        Objects.requireNonNull(bookingId, "bookingId es obligatorio");
        Objects.requireNonNull(userId, "userId es obligatorio");
        Objects.requireNonNull(notificationType, "notificationType es obligatorio");
        Objects.requireNonNull(occurredAt, "occurredAt es obligatorio");
    }

    public NotificationMessage(UUID eventId, UUID bookingId, UUID userId, NotificationType notificationType, Instant occurredAt) {
        this(eventId, bookingId, userId, notificationType, occurredAt, null);
    }

    public static NotificationMessage bookingCreated(UUID bookingId, UUID userId) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.BOOKING_CREATED, Instant.now(), null);
    }

    public static NotificationMessage bookingCancelled(UUID bookingId, UUID userId) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.BOOKING_CANCELLED, Instant.now(), null);
    }

    public static NotificationMessage paymentReminder(UUID bookingId, UUID userId) {
        return paymentReminder(bookingId, userId, null);
    }

    public static NotificationMessage paymentReminder(UUID bookingId, UUID userId, Instant paymentDeadline) {
        return new NotificationMessage(
            UUID.randomUUID(), bookingId, userId,
            NotificationType.PAYMENT_REMINDER, Instant.now(), paymentDeadline);
    }
}
