package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCommand(
    String idempotencyKey, // Clave para evitar cobros dobles en Stripe
    UUID bookingId,        // Identificador de la reserva asociada
    BigDecimal amount      // Monto a cobrar
) {}
