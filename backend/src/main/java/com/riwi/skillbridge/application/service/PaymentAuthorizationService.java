
package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.in.AuthorizePaymentUseCase;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.PaymentEventPublisherPort;
import com.riwi.skillbridge.application.port.out.PaymentPort;
import com.riwi.skillbridge.application.port.out.PaymentResultRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentRejected;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.PaymentCommand;
import com.riwi.skillbridge.domain.model.PaymentResult;
import com.riwi.skillbridge.domain.model.PaymentStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentAuthorizationService implements AuthorizePaymentUseCase {

    private final PaymentPort paymentPort;
    private final PaymentResultRepositoryPort repositoryPort;
    private final PaymentEventPublisherPort eventPublisherPort;
    private final BookingRepositoryPort bookingRepositoryPort;
    private final PaymentApprovedAssembler approvedAssembler;
    private final Clock clock;

    public PaymentAuthorizationService(
        PaymentPort paymentPort,
        PaymentResultRepositoryPort repositoryPort,
        PaymentEventPublisherPort eventPublisherPort,
        BookingRepositoryPort bookingRepositoryPort,
        PaymentApprovedAssembler approvedAssembler,
        Clock clock) {
        this.paymentPort = paymentPort;
        this.repositoryPort = repositoryPort;
        this.eventPublisherPort = eventPublisherPort;
        this.bookingRepositoryPort = bookingRepositoryPort;
        this.approvedAssembler = approvedAssembler;
        this.clock = clock;
    }

    @Override
    public PaymentResult authorize(PaymentCommand command) {
        Optional<PaymentResult> existing =
            repositoryPort.findByIdempotencyKey(command.idempotencyKey());

        if (existing.isPresent()) {
            return existing.get();
        }

        /* Validar el estado y el plazo de pago de la reserva. */
        if (command.bookingId() != null) {
            Optional<Booking> bookingOpt =
                bookingRepositoryPort.findById(command.bookingId());

            if (bookingOpt.isPresent()) {
                Booking booking = bookingOpt.get();

                if (booking.status() == BookingStatus.CANCELLED) {
                    PaymentResult cancelledResult = new PaymentResult(
                        UUID.randomUUID(),
                        command.bookingId(),
                        PaymentStatus.DECLINED,
                        "La reserva ya se encuentra cancelada.",
                        null
                    );

                    repositoryPort.save(command.idempotencyKey(), cancelledResult);
                    return cancelledResult;
                }

                if (booking.isPaymentWindowExpired(clock.instant())) {
                    Booking cancelled = booking.cancel();
                    bookingRepositoryPort.save(cancelled);

                    PaymentResult expiredResult = new PaymentResult(
                        UUID.randomUUID(),
                        command.bookingId(),
                        PaymentStatus.DECLINED,
                        "El plazo para pagar esta reserva ha expirado (menos de 12 horas para la sesión). La reserva ha sido cancelada.",
                        null
                    );

                    repositoryPort.save(command.idempotencyKey(), expiredResult);

                    eventPublisherPort.publishRejected(
                        new PaymentRejected(
                            command.bookingId(),
                            expiredResult.message()
                        )
                    );

                    return expiredResult;
                }
            }
        }

        PaymentResult result = paymentPort.process(command);
        repositoryPort.save(command.idempotencyKey(), result);

        if (result.status() == PaymentStatus.APPROVED) {
            eventPublisherPort.publishApproved(
                approvedAssembler.assemble(
                    result.bookingId(),
                    result.paymentId()
                )
            );
        } else if (result.status() == PaymentStatus.DECLINED) {
            eventPublisherPort.publishRejected(
                new PaymentRejected(
                    result.bookingId(),
                    result.message()
                )
            );
        }

        // Nota: Si el estado es PENDING (como en Stripe PaymentIntent en espera de confirmación del cliente),
        // no emitimos eventos aún; el Webhook asíncrono emitirá la aprobación o rechazo definitivo.

        return result;
    }
}
