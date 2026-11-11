package com.riwi.skillbridge.infrastructure.adapter.out.messaging;

import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitPaymentEventPublisher implements PaymentEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;
    public static final String PAYMENT_EXCHANGE = "payment.events";
    public static final String PAYMENT_APPROVED_KEY = "payment.approved";
    public static final String PAYMENT_REJECTED_KEY = "payment.rejected";

    public RabbitPaymentEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publishApproved(PaymentApproved event) {
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_APPROVED_KEY, event);
    }

    @Override
    public void publishRejected(PaymentRejected event) {
        rabbitTemplate.convertAndSend(PAYMENT_EXCHANGE, PAYMENT_REJECTED_KEY, event);
    }
}
