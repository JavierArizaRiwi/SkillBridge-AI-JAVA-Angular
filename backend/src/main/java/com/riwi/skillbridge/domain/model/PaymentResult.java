package com.riwi.skillbridge.domain.model;

import java.util.UUID;

public record PaymentResult(
    UUID paymentId,        // Identificador de pago generado
    UUID bookingId,
    PaymentStatus status,
    String message,        // Ej: "Aprobado", "Fondos insuficientes", etc.
    String clientSecret    // Secreto de cliente provisto por Stripe para montaje de Elements
) {}
