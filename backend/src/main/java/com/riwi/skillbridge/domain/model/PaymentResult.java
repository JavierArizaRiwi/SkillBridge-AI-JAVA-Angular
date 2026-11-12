package com.riwi.skillbridge.domain.model;

import java.util.UUID;

public record PaymentResult(
    UUID paymentId,        // Identificador ficticio de pago generado
    UUID bookingId,
    PaymentStatus status,
    String message         // Ej: "Aprobado", "Fondos insuficientes", etc.
) {}
