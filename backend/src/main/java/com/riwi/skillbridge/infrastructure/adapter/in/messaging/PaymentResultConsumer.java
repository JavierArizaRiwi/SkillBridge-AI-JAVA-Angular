package com.riwi.skillbridge.infrastructure.adapter.in.messaging;

import com.riwi.skillbridge.application.port.in.PaymentResultUseCase;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultConsumer {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentResultConsumer.class);
    private final PaymentResultUseCase paymentResultUseCase;

    public PaymentResultConsumer(PaymentResultUseCase paymentResultUseCase) {
        this.paymentResultUseCase = paymentResultUseCase;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "booking.payment.approved.queue", durable = "true"),
            exchange = @Exchange(value = "payment.events", type = "topic"),
            key = "payment.approved"
    ))
    public void onPaymentApproved(PaymentApproved event) {
        log.info("Received PaymentApproved event for bookingId={}", event.bookingId());
        try {
            paymentResultUseCase.processPaymentApproved(event.bookingId());
        } catch (Exception e) {
            log.error("Failed to process payment approval for bookingId={}: {}", event.bookingId(), e.getMessage());
        }
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "booking.payment.rejected.queue", durable = "true"),
            exchange = @Exchange(value = "payment.events", type = "topic"),
            key = "payment.rejected"
    ))
    public void onPaymentRejected(PaymentRejected event) {
        log.info("Received PaymentRejected event for bookingId={}, reason={}", event.bookingId(), event.reason());
        try {
            paymentResultUseCase.processPaymentRejected(event.bookingId(), event.reason());
        } catch (Exception e) {
            log.error("Failed to process payment rejection for bookingId={}: {}", event.bookingId(), e.getMessage());
        }
    }
}
