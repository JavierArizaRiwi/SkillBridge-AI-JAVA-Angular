package com.riwi.skillbridge.infrastructure.adapter.in.rest;

import com.riwi.skillbridge.application.port.in.AuthorizePaymentUseCase;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.payment.PaymentRequestDto;
import com.riwi.skillbridge.infrastructure.adapter.in.rest.dto.payment.PaymentResponseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final AuthorizePaymentUseCase authorizePaymentUseCase;

    public PaymentController(AuthorizePaymentUseCase authorizePaymentUseCase) {
        this.authorizePaymentUseCase = authorizePaymentUseCase;
    }

    @PostMapping("/authorize")
    public ResponseEntity<PaymentResponseDto> authorizePayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequestDto request) {
            
        PaymentCommand command = new PaymentCommand(
                idempotencyKey,
                request.bookingId(),
                request.amount(),
                request.cardNumber(),
                request.expiryDate(),
                request.cvv()
        );

        PaymentResult result = authorizePaymentUseCase.authorize(command);
        
        PaymentResponseDto response = new PaymentResponseDto(
                result.bookingId(),
                result.status().name(),
                result.message()
        );

        return ResponseEntity.ok(response);
    }
}
