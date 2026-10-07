package com.riwi.skillbridge.domain.model;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void shouldRejectCancellationFromConfirmedStatus() {
        Booking booking = bookingWithStatus(BookingStatus.CONFIRMED);

        assertThrows(BusinessRuleException.class, booking::cancel);
    }

    @Test
    void shouldRejectCancellationFromCompletedStatus() {
        Booking booking = bookingWithStatus(BookingStatus.COMPLETED);

        assertThrows(BusinessRuleException.class, booking::cancel);
    }

    private Booking bookingWithStatus(BookingStatus status) {
        return new Booking(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2030-10-10T15:00:00Z"),
                status);
    }
}
