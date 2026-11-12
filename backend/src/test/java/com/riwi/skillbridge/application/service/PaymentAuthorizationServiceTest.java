package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentAuthorizationServiceTest {

    @Mock
    private PaymentPort paymentPort;

    @Mock
    private PaymentResultRepositoryPort repositoryPort;

    @Mock
    private PaymentEventPublisherPort eventPublisherPort;

    @InjectMocks
    private PaymentAuthorizationService service;

    private PaymentCommand command;
    private UUID bookingId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        idempotencyKey = "test-key-123";
        bookingId = UUID.randomUUID();
        command = new PaymentCommand(idempotencyKey, bookingId, new BigDecimal("50.00"), "1111", "12/25", "123");
    }

    @Test
    void testIdempotencyRepeat() {
        // Prueba de Idempotencia: Si la llave ya existe, retorna el resultado previo sin volver a procesar
        PaymentResult existingResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.APPROVED, "Already approved");
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingResult));

        PaymentResult result = service.authorize(command);

        assertEquals(existingResult, result);
        verify(paymentPort, never()).process(any()); // Verifica que NO se llamo a la pasarela
        verify(eventPublisherPort, never()).publishApproved(any()); // Verifica que NO se emitio otro evento
    }

    @Test
    void testPaymentApproved() {
        // Prueba flujo feliz: Pago aprobado, guarda el resultado y emite el evento correspondiente
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        PaymentResult newResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.APPROVED, "Payment approved");
        when(paymentPort.process(command)).thenReturn(newResult);

        PaymentResult result = service.authorize(command);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult); // Verifica guardado
        verify(eventPublisherPort).publishApproved(any(PaymentApproved.class)); // Verifica evento emitido
    }

    @Test
    void testPaymentDeclined() {
        // Prueba flujo de rechazo: Pago rechazado, guarda el resultado y emite el evento correspondiente
        when(repositoryPort.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        PaymentResult newResult = new PaymentResult(UUID.randomUUID(), bookingId, PaymentStatus.DECLINED, "Card declined");
        when(paymentPort.process(command)).thenReturn(newResult);

        PaymentResult result = service.authorize(command);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult); // Verifica guardado
        verify(eventPublisherPort).publishRejected(any(PaymentRejected.class)); // Verifica evento emitido
    }
}
