package com.riwi.skillbridge.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record BookingStatusHistory(
        UUID id,
        UUID bookingId,
        BookingStatus previousStatus,
        BookingStatus newStatus,
        UUID changedBy,
        Instant changedAt
) {
    public BookingStatusHistory {
        Objects.requireNonNull(id, "El id del historial es obligatorio");
        Objects.requireNonNull(bookingId, "El id de la reserva es obligatorio");
        Objects.requireNonNull(previousStatus, "El estado anterior es obligatorio");
        Objects.requireNonNull(newStatus, "El nuevo estado es obligatorio");
        Objects.requireNonNull(changedBy, "El usuario que realiza el cambio es obligatorio");
        Objects.requireNonNull(changedAt, "La fecha del cambio es obligatoria");
    }

    public static BookingStatusHistory forTransition(
            Booking booking,
            BookingStatus newStatus,
            UUID changedBy,
            Instant changedAt) {
        return new BookingStatusHistory(
                UUID.randomUUID(),
                booking.id(),
                booking.status(),
                newStatus,
                changedBy,
                changedAt);
    }
}
