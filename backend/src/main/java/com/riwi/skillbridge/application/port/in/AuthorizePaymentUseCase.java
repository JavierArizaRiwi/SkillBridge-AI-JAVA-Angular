package com.riwi.skillbridge.application.port.in;

import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;

public interface AuthorizePaymentUseCase {
    PaymentResult authorize(PaymentCommand command);
}
