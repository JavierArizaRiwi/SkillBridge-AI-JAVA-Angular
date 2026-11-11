package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;

import java.time.Instant;
import java.util.UUID;

public record Booking(
        UUID id,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        BookingStatus status,
        long version
) {
    // Modificado para permitir cancelar tanto reservas CREATED como CONFIRMED\n
    public Booking cancel() {
        if (status == BookingStatus.CANCELLED) {
            return this;
        }
        if (status == BookingStatus.COMPLETED) {
            throw new BusinessRuleException("No se pueden cancelar reservas completadas");
        }
        return new Booking(id, offeringId, customerId, scheduledAt, BookingStatus.CANCELLED, version);
    }
}
