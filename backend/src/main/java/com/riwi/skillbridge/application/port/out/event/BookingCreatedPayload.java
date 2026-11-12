package com.riwi.skillbridge.application.port.out.event;

import java.time.Instant;
import java.util.UUID;

public record BookingCreatedPayload(
    UUID bookingId,
    UUID offeringId,
    UUID customerId,
    Instant scheduledAt,
    String status
) {}
