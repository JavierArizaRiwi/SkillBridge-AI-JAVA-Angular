package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookingTest {
    @Test
    void shouldCancelCreatedBookingAndPreserveItsData() {
        Booking booking = bookingWithStatus(BookingStatus.CREATED);

        Booking cancelled = booking.cancel();

        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(booking.id(), cancelled.id());
        assertEquals(booking.offeringId(), cancelled.offeringId());
        assertEquals(booking.customerId(), cancelled.customerId());
        assertEquals(booking.scheduledAt(), cancelled.scheduledAt());
    }

    @Test
    void shouldReturnSameBookingWhenCancellationIsRepeated() {
        Booking booking = bookingWithStatus(BookingStatus.CANCELLED);

        Booking result = booking.cancel();

        assertSame(booking, result);
    }

    @Test
    void shouldAllowCancellationFromConfirmedStatus() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);
        Booking cancelled = booking.cancel();
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
    }

    @Test
    void shouldRejectCancellationFromCompletedStatus() {
        Booking booking = bookingWithStatus(BookingStatus.COMPLETED);

        assertThrows(BusinessRuleException.class, booking::cancel);
    }

    @Test
    void shouldBeEligibleForPaymentReminderWhenWithin7DaysAndNeverReminded() {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        // Sesión en 5 días -> límite de pago en 4.5 días (dentro de la ventana de 7 días)
        Instant scheduledAt = now.plus(Duration.ofDays(5));
        Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                scheduledAt, BookingStatus.CREATED, 0);

        assertTrue(booking.isEligibleForPaymentReminder(now));
    }

    @Test
    void shouldNotBeEligibleWhenAlreadyRemindedWithinLast24Hours() {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        Instant scheduledAt = now.plus(Duration.ofDays(5));
        Instant lastReminded = now.minus(Duration.ofHours(12)); // hace 12 horas
        Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                scheduledAt, BookingStatus.CREATED, 0, lastReminded);

        assertFalse(booking.isEligibleForPaymentReminder(now));
    }

    @Test
    void shouldBeEligibleWhenLastReminderWasMoreThan24HoursAgo() {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        Instant scheduledAt = now.plus(Duration.ofDays(5));
        Instant lastReminded = now.minus(Duration.ofHours(25)); // hace 25 horas
        Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                scheduledAt, BookingStatus.CREATED, 0, lastReminded);

        assertTrue(booking.isEligibleForPaymentReminder(now));
    }

    @Test
    void shouldNotBeEligibleWhenSessionIsMoreThan7DaysBeforeDeadline() {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        // Sesión en 10 días -> deadline en 9.5 días (aún faltan más de 7 días para el vencimiento)
        Instant scheduledAt = now.plus(Duration.ofDays(10));
        Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                scheduledAt, BookingStatus.CREATED, 0);

        assertFalse(booking.isEligibleForPaymentReminder(now));
    }

    @Test
    void shouldNotBeEligibleWhenPaymentWindowAlreadyExpired() {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        // Sesión en 6 horas -> ya pasaron las 12h de límite (vencido)
        Instant scheduledAt = now.plus(Duration.ofHours(6));
        Booking booking = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                scheduledAt, BookingStatus.CREATED, 0);

        assertFalse(booking.isEligibleForPaymentReminder(now));
    }

    @Test
    void shouldNotBeEligibleWhenStatusIsNotCreated() {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");
        Instant scheduledAt = now.plus(Duration.ofDays(3));
        Booking confirmed = new Booking(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                scheduledAt, BookingStatus.CONFIRMED, 0);

        assertFalse(confirmed.isEligibleForPaymentReminder(now));
    }

    private Booking bookingWithStatus(BookingStatus status) {
        return new Booking(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2030-10-10T15:00:00Z"),
                status,
                0);
    }
}

