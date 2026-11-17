package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.PaymentResult;
import java.util.Optional;

public interface PaymentResultRepositoryPort {
    Optional<PaymentResult> findByIdempotencyKey(String key);
    void save(String key, PaymentResult result);
}
