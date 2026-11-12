package com.riwi.skillbridge.infrastructure.adapter.out.payment;

import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Component;

@Component
public class SimulatedPaymentAdapter implements PaymentPort {

    @Override
    public PaymentResult process(PaymentCommand command) {
        // Timeout simulado - just sleep a bit or return an error if specific criteria met
        if ("TIMEOUT".equals(command.cvv())) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new PaymentResult(java.util.UUID.randomUUID(), command.bookingId(), PaymentStatus.DECLINED, "Simulated timeout");
        }

        // Rechazo determinista
        if (command.cardNumber() != null && command.cardNumber().endsWith("0000")) {
            return new PaymentResult(java.util.UUID.randomUUID(), command.bookingId(), PaymentStatus.DECLINED, "Card declined deterministically");
        }
        
        // Error determinista
        if ("ERROR".equals(command.cvv())) {
            throw new RuntimeException("Simulated payment system error");
        }

        // Aprobacion determinista
        return new PaymentResult(java.util.UUID.randomUUID(), command.bookingId(), PaymentStatus.APPROVED, "Payment approved");
    }
}

