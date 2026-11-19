package com.riwi.skillbridge.application.port.out.event;

import java.util.UUID;

public record PaymentApprovedPayload(
    UUID paymentId,
    UUID bookingId,
    double amount,
    String currency
) {}
