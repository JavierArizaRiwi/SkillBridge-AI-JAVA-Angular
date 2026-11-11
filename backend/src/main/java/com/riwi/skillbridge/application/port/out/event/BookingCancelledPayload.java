package com.riwi.skillbridge.application.port.out.event;

import java.util.UUID;

/**
 * Payload for the BookingCancelled event.
 * offeringId is included so analytics consumers can update per-offering cancellation metrics
 * without needing an additional database lookup.
 */
public record BookingCancelledPayload(
    UUID bookingId,
    UUID offeringId,
    UUID customerId,
    String status
) {}
