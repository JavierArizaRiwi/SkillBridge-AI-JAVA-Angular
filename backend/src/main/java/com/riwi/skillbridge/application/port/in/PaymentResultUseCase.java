package com.riwi.skillbridge.application.port.in;

import java.util.UUID;

public interface PaymentResultUseCase {
    void processPaymentApproved(UUID bookingId);
    void processPaymentRejected(UUID bookingId, String reason);
}
