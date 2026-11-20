package com.riwi.skillbridge.application.port.in;

import java.util.Objects;
import java.util.UUID;

public record CancelBookingCommand(UUID bookingId, String customerEmail) {
    public CancelBookingCommand {
        Objects.requireNonNull(bookingId, "El id de la reserva es obligatorio");
        Objects.requireNonNull(customerEmail, "El email del cliente es obligatorio");
        if (customerEmail.isBlank()) {
            throw new IllegalArgumentException("El email del cliente es obligatorio");
        }
    }
}
