package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.payment;

import java.util.UUID;

public record PaymentResponseDto(
    UUID bookingId,
    String status,
    String message
) {}
