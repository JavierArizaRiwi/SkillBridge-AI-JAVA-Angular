package com.riwi.skillbridge.infrastructure.adapter.out.payment.stripe;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Cliente HTTP declarativo OpenFeign para consumir la API REST de Stripe.
 */
@FeignClient(
    name = "stripeClient",
    url = "${stripe.api-url:https://api.stripe.com}",
    configuration = StripeFeignConfig.class
)
public interface StripeFeignClient {

    @PostMapping(
        value = "/v1/payment_intents",
        consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    StripePaymentIntentResponse createPaymentIntent(
        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody MultiValueMap<String, String> formParams
    );
}
