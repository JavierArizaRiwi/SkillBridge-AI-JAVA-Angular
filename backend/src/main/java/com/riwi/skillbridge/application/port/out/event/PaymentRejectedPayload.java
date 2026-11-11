package com.riwi.skillbridge.application.port.out.event;

import java.util.UUID;

public record PaymentRejectedPayload(
    UUID paymentId,
    UUID bookingId,
    String reason
) {}
