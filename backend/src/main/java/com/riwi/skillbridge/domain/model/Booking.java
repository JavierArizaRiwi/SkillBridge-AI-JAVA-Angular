package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;

import java.time.Instant;
import java.util.UUID;

public record Booking(
        UUID id,
        UUID offeringId,
        UUID customerId,
        Instant scheduledAt,
        BookingStatus status
) {
    public Booking cancel() {
        if (status == BookingStatus.CANCELLED) {
            return this;
        }
        if (status != BookingStatus.CREATED) {
            throw new BusinessRuleException("Solo se pueden cancelar reservas en estado CREATED");
        }
        return new Booking(id, offeringId, customerId, scheduledAt, BookingStatus.CANCELLED);
    }
}
