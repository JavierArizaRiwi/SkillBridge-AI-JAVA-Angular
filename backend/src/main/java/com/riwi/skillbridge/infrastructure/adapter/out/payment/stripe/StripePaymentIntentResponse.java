package com.riwi.skillbridge.infrastructure.adapter.out.payment.stripe;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Representa la respuesta de Stripe al crear o consultar un PaymentIntent.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StripePaymentIntentResponse(
    String id,
    String object,
    Long amount,
    String currency,
    String status,
    @JsonProperty("client_secret") String clientSecret,
    @JsonProperty("last_payment_error") StripeError lastPaymentError,
    Map<String, String> metadata
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record StripeError(
        String code,
        String message,
        String type
    ) {}
}
