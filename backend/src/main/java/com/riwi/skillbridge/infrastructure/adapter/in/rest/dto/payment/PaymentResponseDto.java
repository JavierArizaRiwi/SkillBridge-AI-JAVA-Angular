package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.payment;

import java.util.UUID;

public record PaymentResponseDto(
    UUID bookingId,
    String status,
    String message,
    String clientSecret
) {
    // Constructor de sobrecarga para mantener compatibilidad
    public PaymentResponseDto(UUID bookingId, String status, String message) {
        this(bookingId, status, message, null);
    }
}
