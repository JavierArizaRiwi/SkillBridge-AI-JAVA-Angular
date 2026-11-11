package com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentRequestDto(
    UUID bookingId,
    BigDecimal amount,
    String cardNumber,
    String expiryDate,
    String cvv
) {}
