
package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Pruebas Unitarias - Servicio de Pagos")
class PaymentAuthorizationServiceTest {

    @Mock
    private PaymentPort paymentPort;

    @Mock
    private PaymentResultRepositoryPort repositoryPort;

    @Mock
    private PaymentEventPublisherPort eventPublisherPort;

    @Mock
    private BookingRepositoryPort bookingRepositoryPort;

    @Mock
    private PaymentApprovedAssembler approvedAssembler;

    private Clock clock;
    private PaymentAuthorizationService service;

    private PaymentCommand command;
    private UUID bookingId;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(
            Instant.parse("2026-11-10T12:00:00Z"),
            ZoneOffset.UTC
        );

        service = new PaymentAuthorizationService(
            paymentPort,
            repositoryPort,
            eventPublisherPort,
            bookingRepositoryPort,
            approvedAssembler,
            clock
        );

        idempotencyKey = "test-key-123";
        bookingId = UUID.randomUUID();

        command = new PaymentCommand(
            idempotencyKey,
            bookingId,
            new BigDecimal("50.00")
        );
    }

    @Test
    @DisplayName("Caso Borde: devuelve el cobro previo sin duplicar")
    void testIdempotencyRepeat() {
        PaymentResult existingResult = new PaymentResult(
            UUID.randomUUID(),
            bookingId,
            PaymentStatus.APPROVED,
            "Already approved",
            "pi_test_secret"
        );

        when(repositoryPort.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.of(existingResult));

        PaymentResult result = service.authorize(command);

        assertEquals(existingResult, result);
        verify(paymentPort, never()).process(any());
        verify(eventPublisherPort, never()).publishApproved(any());
    }

    @Test
    @DisplayName("Caso Feliz: pago aprobado y evento publicado")
    void testPaymentApproved() {
        when(repositoryPort.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());

        when(bookingRepositoryPort.findById(bookingId))
            .thenReturn(Optional.empty());

        PaymentResult newResult = new PaymentResult(
            UUID.randomUUID(),
            bookingId,
            PaymentStatus.APPROVED,
            "Payment approved",
            "pi_test_secret"
        );

        PaymentApproved enriched = new PaymentApproved(bookingId);

        when(paymentPort.process(command)).thenReturn(newResult);

        when(approvedAssembler.assemble(
            bookingId,
            newResult.paymentId()
        )).thenReturn(enriched);

        PaymentResult result = service.authorize(command);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult);
        verify(eventPublisherPort).publishApproved(enriched);
    }

    @Test
    @DisplayName("Caso Triste: pago rechazado y evento publicado")
    void testPaymentDeclined() {
        when(repositoryPort.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());

        when(bookingRepositoryPort.findById(bookingId))
            .thenReturn(Optional.empty());

        PaymentResult newResult = new PaymentResult(
            UUID.randomUUID(),
            bookingId,
            PaymentStatus.DECLINED,
            "Card declined",
            null
        );

        when(paymentPort.process(command)).thenReturn(newResult);

        PaymentResult result = service.authorize(command);

        assertEquals(newResult, result);
        verify(repositoryPort).save(idempotencyKey, newResult);
        verify(eventPublisherPort).publishRejected(any(PaymentRejected.class));
    }

    @Test
    @DisplayName("Caso Triste: cancela la reserva cuando faltan menos de 12 horas")
    void testPaymentDeclinedWhenPaymentWindowExpired() {
        Instant sessionIn4Hours = clock.instant().plusSeconds(4 * 3600);

        Booking expiredBooking = new Booking(
            bookingId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            sessionIn4Hours,
            BookingStatus.CREATED,
            0
        );

        when(repositoryPort.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());

        when(bookingRepositoryPort.findById(bookingId))
            .thenReturn(Optional.of(expiredBooking));

        PaymentResult result = service.authorize(command);

        assertEquals(PaymentStatus.DECLINED, result.status());

        assertEquals(
            "El plazo para pagar esta reserva ha expirado (menos de 12 horas para la sesión). La reserva ha sido cancelada.",
            result.message()
        );

        verify(bookingRepositoryPort).save(
            argThat(booking -> booking.status() == BookingStatus.CANCELLED)
        );

        verify(paymentPort, never()).process(any());

        verify(eventPublisherPort).publishRejected(
            any(PaymentRejected.class)
        );
    }

    @Test
    @DisplayName("Caso Triste: rechaza el pago si la reserva ya esta cancelada")
    void testPaymentDeclinedWhenBookingIsAlreadyCancelled() {
        Instant futureSession = clock.instant().plusSeconds(24 * 3600);

        Booking cancelledBooking = new Booking(
            bookingId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            futureSession,
            BookingStatus.CANCELLED,
            0
        );

        when(repositoryPort.findByIdempotencyKey(idempotencyKey))
            .thenReturn(Optional.empty());

        when(bookingRepositoryPort.findById(bookingId))
            .thenReturn(Optional.of(cancelledBooking));

        PaymentResult result = service.authorize(command);

        assertEquals(PaymentStatus.DECLINED, result.status());
        assertEquals(
            "La reserva ya se encuentra cancelada.",
            result.message()
        );

        verify(repositoryPort).save(eq(idempotencyKey), any(PaymentResult.class));
        verify(paymentPort, never()).process(any());
        verify(eventPublisherPort, never()).publishApproved(any());
    }
}
