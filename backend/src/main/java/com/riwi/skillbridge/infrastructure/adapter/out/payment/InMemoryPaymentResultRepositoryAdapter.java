package com.riwi.skillbridge.infrastructure.adapter.out.payment;

import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.model.PaymentResult;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class InMemoryPaymentResultRepositoryAdapter implements PaymentResultRepositoryPort {

    private final ConcurrentHashMap<String, PaymentResult> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<PaymentResult> findByIdempotencyKey(String key) {
        if (key == null) return Optional.empty();
        return Optional.ofNullable(cache.get(key));
    }

    @Override
    public void save(String key, PaymentResult result) {
        if (key != null) {
            cache.put(key, result);
        }
    }
}
