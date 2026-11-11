package com.riwi.skillbridge.application.port.in;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CancelBookingCommandTest {
    @Test
    void shouldAcceptBookingIdAndAuthenticatedCustomerEmail() {
        assertDoesNotThrow(() -> new CancelBookingCommand(UUID.randomUUID(), "customer@example.com"));
    }

    @Test
    void shouldRejectMissingBookingId() {
        assertThrows(NullPointerException.class, () -> new CancelBookingCommand(null, "customer@example.com"));
    }

    @Test
    void shouldRejectMissingCustomerEmail() {
        assertThrows(NullPointerException.class, () -> new CancelBookingCommand(UUID.randomUUID(), null));
    }

    @Test
    void shouldRejectBlankCustomerEmail() {
        assertThrows(IllegalArgumentException.class, () -> new CancelBookingCommand(UUID.randomUUID(), "  "));
    }
}
