package com.riwi.skillbridge.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCommand(
    String idempotencyKey, // Clave para evitar cobros dobles
    UUID bookingId,        // A qué reserva pertenece el pago
    BigDecimal amount,     // Monto a cobrar
    String cardNumber,     // Simulado: ej. "1111"
    String expiryDate,     // Simulado: ej. "12/25"
    String cvv             // Simulado: ej. "123"
) {}
