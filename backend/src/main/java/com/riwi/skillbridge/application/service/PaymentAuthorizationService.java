package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AuthorizePaymentUseCase;
import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class PaymentAuthorizationService implements AuthorizePaymentUseCase {

    private final PaymentPort paymentPort;
    private final PaymentResultRepositoryPort repositoryPort;
    private final PaymentEventPublisherPort eventPublisherPort;

    public PaymentAuthorizationService(
            PaymentPort paymentPort,
            PaymentResultRepositoryPort repositoryPort,
            PaymentEventPublisherPort eventPublisherPort) {
        this.paymentPort = paymentPort;
        this.repositoryPort = repositoryPort;
        this.eventPublisherPort = eventPublisherPort;
    }

    @Override
    public PaymentResult authorize(PaymentCommand command) {
        Optional<PaymentResult> existing = repositoryPort.findByIdempotencyKey(command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        PaymentResult result = paymentPort.process(command);
        repositoryPort.save(command.idempotencyKey(), result);

        if (result.status() == PaymentStatus.APPROVED) {
            eventPublisherPort.publishApproved(new PaymentApproved(result.bookingId()));
        } else {
            eventPublisherPort.publishRejected(new PaymentRejected(result.bookingId(), result.message()));
        }

        return result;
    }
}
