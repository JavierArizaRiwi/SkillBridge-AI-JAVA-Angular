package com.riwi.skillbridge.domain.service;

import com.riwi.skillbridge.domain.exception.BusinessRuleException;
import com.riwi.skillbridge.domain.model.Booking;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class BookingCancellationPolicy {
    private final Clock clock;
    private final Duration minimumNotice;

    public BookingCancellationPolicy(Clock clock, Duration minimumNotice) {
        this.clock = Objects.requireNonNull(clock, "El reloj es obligatorio");
        this.minimumNotice = Objects.requireNonNull(minimumNotice, "La anticipación mínima es obligatoria");
        if (minimumNotice.isZero() || minimumNotice.isNegative()) {
            throw new IllegalArgumentException("La anticipación mínima debe ser positiva");
        }
    }

    public void validate(Booking booking) {
        Objects.requireNonNull(booking, "La reserva es obligatoria");
        Instant now = clock.instant();
        if (!booking.scheduledAt().isAfter(now)) {
            throw new BusinessRuleException("Solo se pueden cancelar reservas con fecha futura");
        }
        if (booking.scheduledAt().isBefore(now.plus(minimumNotice))) {
            throw new BusinessRuleException("La reserva debe cancelarse con una anticipación mínima de " + minimumNotice.toHours() + " horas");
        }
    }
}
