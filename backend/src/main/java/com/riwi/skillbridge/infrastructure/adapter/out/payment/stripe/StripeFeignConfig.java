package com.riwi.skillbridge.infrastructure.adapter.out.payment.stripe;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Configuración específica para el cliente OpenFeign de Stripe.
 * Inyecta dinámicamente el header Authorization con la Secret Key configurada.
 */
public class StripeFeignConfig {

    @Value("${stripe.secret-key:}")
    private String stripeSecretKey;

    @Bean
    public RequestInterceptor stripeAuthRequestInterceptor() {
        return requestTemplate -> {
            if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
                requestTemplate.header("Authorization", "Bearer " + stripeSecretKey);
            }
        };
    }
}
