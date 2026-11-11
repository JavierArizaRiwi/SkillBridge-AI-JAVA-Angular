package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.BookingRepositoryPort;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.UserRepositoryPort;
import com.riwi.skillbridge.domain.event.PaymentApproved;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import com.riwi.skillbridge.domain.model.MentorshipMode;
import com.riwi.skillbridge.domain.model.Offering;
import com.riwi.skillbridge.domain.model.Role;
import com.riwi.skillbridge.domain.model.UserAccount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentApprovedAssemblerTest {

    private final BookingRepositoryPort bookings = mock(BookingRepositoryPort.class);
    private final OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
    private final UserRepositoryPort users = mock(UserRepositoryPort.class);
    private final PaymentApprovedAssembler assembler = new PaymentApprovedAssembler(bookings, offerings, users);

    private final UUID bookingId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID providerId = UUID.randomUUID();
    private final Instant scheduledAt = Instant.parse("2030-01-01T15:00:00Z");
    private Offering offering;

    @BeforeEach
    void setUp() {
        offering = Offering.create(providerId, "Java avanzado", "d", "BACKEND", BigDecimal.TEN,
            MentorshipMode.LIVE, 2);
    }

    private void givenAll() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(
            new Booking(bookingId, offering.id(), customerId, scheduledAt, BookingStatus.CONFIRMED, 0)));
        when(offerings.findById(offering.id())).thenReturn(Optional.of(offering));
        when(users.findById(customerId)).thenReturn(Optional.of(
            new UserAccount(customerId, "Ana", "ana@test.com", "hash", Role.CUSTOMER)));
    }

    @Test
    void arma_el_evento_v2_con_el_estado_completo() {
        givenAll();
        UUID paymentId = UUID.randomUUID();

        PaymentApproved e = assembler.assemble(bookingId, paymentId);

        assertEquals(2, e.version());
        assertNotNull(e.eventId());
        assertNotNull(e.occurredAt());
        assertNotNull(e.correlationId());
        assertEquals(bookingId, e.bookingId());
        assertEquals(paymentId, e.paymentId());
        assertEquals(customerId, e.customerId());
        assertEquals("ana@test.com", e.customerEmail());
        assertEquals(offering.id(), e.offeringId());
        assertEquals("Java avanzado", e.offeringTitle());
        assertEquals(providerId, e.providerId());
        assertEquals(scheduledAt, e.scheduledAt());
        assertEquals(2, e.durationHours());
        assertEquals(MentorshipMode.LIVE, e.mode());
        assertTrue(e.hasFullMentorshipState());
    }

    @Test
    void el_webhook_de_stripe_no_tiene_payment_id_y_el_evento_sigue_completo() {
        givenAll();
        PaymentApproved e = assembler.assemble(bookingId, null);
        assertNull(e.paymentId());
        assertTrue(e.hasFullMentorshipState());
    }

    @Test
    void sin_reserva_degrada_a_forma_minima_sin_fallar() {
        when(bookings.findById(bookingId)).thenReturn(Optional.empty());
        PaymentApproved e = assembler.assemble(bookingId, null);
        assertEquals(bookingId, e.bookingId());
        assertEquals(1, e.version());
        assertFalse(e.hasFullMentorshipState());
    }

    @Test
    void sin_oferta_o_sin_cliente_degrada_a_forma_minima() {
        when(bookings.findById(bookingId)).thenReturn(Optional.of(
            new Booking(bookingId, offering.id(), customerId, scheduledAt, BookingStatus.CONFIRMED, 0)));
        when(offerings.findById(offering.id())).thenReturn(Optional.empty());
        assertFalse(assembler.assemble(bookingId, null).hasFullMentorshipState());

        when(offerings.findById(offering.id())).thenReturn(Optional.of(offering));
        when(users.findById(customerId)).thenReturn(Optional.empty());
        PaymentApproved e = assembler.assemble(bookingId, null);
        assertFalse(e.hasFullMentorshipState());
        assertEquals(bookingId, e.bookingId());
    }

    @Test
    void la_forma_minima_v1_no_se_considera_enriquecida() {
        assertFalse(new PaymentApproved(bookingId).hasFullMentorshipState());
    }
}
