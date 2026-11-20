package com.riwi.skillbridge.application.port.out;

import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;

public interface PaymentEventPublisherPort {
    void publishApproved(PaymentApproved event);
    void publishRejected(PaymentRejected event);
}
