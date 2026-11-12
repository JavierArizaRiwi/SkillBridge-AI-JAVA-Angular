package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.Booking;
import java.time.Instant;
import java.util.UUID;

public interface CreateBookingUseCase {
    Booking create(UUID offeringId, Instant scheduledAt, String customerEmail);
    Booking create(UUID offeringId, Instant scheduledAt, String customerEmail, String idempotencyKey);
}
