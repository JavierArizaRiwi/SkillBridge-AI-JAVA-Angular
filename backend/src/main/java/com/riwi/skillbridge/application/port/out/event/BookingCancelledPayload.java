package com.riwi.skillbridge.application.port.out.event;

import java.util.UUID;

public record BookingCancelledPayload(
    UUID bookingId,
    UUID customerId,
    String status
) {}
