package com.riwi.skillbridge.domain.event;

import java.util.UUID;

public record PaymentRejected(
    UUID bookingId,
    String reason
) {}
