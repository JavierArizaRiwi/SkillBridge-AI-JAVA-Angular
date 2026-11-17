package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.common.CorrelationIdHolder;
import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Construye {@link PaymentApproved} v2 a partir de la reserva, la oferta y el cliente.
 * Punto único de enriquecimiento para todos los orígenes de aprobación (pago directo y webhook).
 */
@Component
public class PaymentApprovedAssembler {
    private static final Logger log = LoggerFactory.getLogger(PaymentApprovedAssembler.class);

    private final BookingRepositoryPort bookings;
    private final OfferingRepositoryPort offerings;
    private final UserRepositoryPort users;

    public PaymentApprovedAssembler(BookingRepositoryPort bookings, OfferingRepositoryPort offerings,
                                    UserRepositoryPort users) {
        this.bookings = bookings;
        this.offerings = offerings;
        this.users = users;
    }

    /**
     * @param paymentId puede ser {@code null} (webhook de Stripe)
     * @return evento v2; o la forma mínima v1 si falta algún dato (se registra un aviso)
     */
    public PaymentApproved assemble(UUID bookingId, UUID paymentId) {
        Optional<Booking> booking = bookings.findById(bookingId);
        if (booking.isEmpty()) {
            return degraded(bookingId, "reserva no encontrada");
        }
        Booking b = booking.get();
        Optional<Offering> offering = offerings.findById(b.offeringId());
        if (offering.isEmpty()) {
            return degraded(bookingId, "oferta " + b.offeringId() + " no encontrada");
        }
        Optional<UserAccount> customer = users.findById(b.customerId());
        if (customer.isEmpty()) {
            return degraded(bookingId, "cliente " + b.customerId() + " no encontrado");
        }
        Offering o = offering.get();
        String correlationId = CorrelationIdHolder.get() != null
            ? CorrelationIdHolder.get() : UUID.randomUUID().toString();
        return new PaymentApproved(PaymentApproved.CURRENT_VERSION, UUID.randomUUID(), Instant.now(), correlationId,
            bookingId, paymentId, b.customerId(), customer.get().email(),
            o.id(), o.title(), o.providerId(), b.scheduledAt(), o.durationHours(), o.mode());
    }

    private PaymentApproved degraded(UUID bookingId, String reason) {
        log.warn("PaymentApproved para bookingId={} se emite en forma mínima (v1): {}", bookingId, reason);
        return new PaymentApproved(bookingId);
    }
}
