package com.riwi.skillbridge.domain.model;

import java.util.UUID;

public record PaymentResult(
    UUID paymentId,        // Identificador de pago generado
    UUID bookingId,
    PaymentStatus status,
    String message,        // Ej: "Aprobado", "Fondos insuficientes", etc.
    String clientSecret    // Secreto de cliente provisto por pasarelas modernas (ej. Stripe)
) {
    // Constructor de compatibilidad para flujos previos y tests existentes
    public PaymentResult(UUID paymentId, UUID bookingId, PaymentStatus status, String message) {
        this(paymentId, bookingId, status, message, null);
    }
}
