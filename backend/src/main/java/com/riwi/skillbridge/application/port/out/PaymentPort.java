package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;

public interface PaymentPort {
    PaymentResult process(PaymentCommand command);
}
