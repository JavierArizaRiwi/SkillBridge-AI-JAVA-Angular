package com.riwi.skillbridge.domain.service;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Booking;
import com.riwi.skillbridge.domain.model.BookingStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BookingCancellationPolicyTest {
    private static final Instant NOW = Instant.parse("2030-10-10T12:00:00Z");
    private static final Duration MINIMUM_NOTICE = Duration.ofHours(24);
    private final BookingCancellationPolicy policy = new BookingCancellationPolicy(
            Clock.fixed(NOW, ZoneOffset.UTC), MINIMUM_NOTICE);

    @Test
    void shouldAllowCancellationAtExactMinimumNotice() {
        assertDoesNotThrow(() -> policy.validate(bookingScheduledAt(NOW.plus(MINIMUM_NOTICE))));
    }

    @Test
    void shouldAllowCancellationAfterMinimumNotice() {
        assertDoesNotThrow(() -> policy.validate(bookingScheduledAt(NOW.plus(MINIMUM_NOTICE).plusSeconds(1))));
    }

    @Test
    void shouldRejectCancellationWithInsufficientNotice() {
        assertThrows(BusinessRuleException.class,
                () -> policy.validate(bookingScheduledAt(NOW.plus(MINIMUM_NOTICE).minusSeconds(121))));
    }

    @Test
    void shouldRejectCancellationAtCurrentInstant() {
        assertThrows(BusinessRuleException.class, () -> policy.validate(bookingScheduledAt(NOW)));
    }

    @Test
    void shouldRejectCancellationForPastBooking() {
        assertThrows(BusinessRuleException.class, () -> policy.validate(bookingScheduledAt(NOW.minusSeconds(1))));
    }

    private Booking bookingScheduledAt(Instant scheduledAt) {
        return new Booking(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                scheduledAt,
                BookingStatus.CREATED,
                0);
    }
}


